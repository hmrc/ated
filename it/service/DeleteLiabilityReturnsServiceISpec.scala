package service

import helpers.{AssertionHelpers, IntegrationSpec}
import models._
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.http.Status._
import play.api.libs.json.{Format, Json, OFormat}
import play.api.libs.ws.WSResponse
import play.api.test.FutureAwaits
import scheduler.DeleteLiabilityReturnsService
import uk.gov.hmrc.crypto.{ApplicationCrypto, CryptoWithKeysFromConfig}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteLiabilityReturnsServiceISpec extends IntegrationSpec with AssertionHelpers with FutureAwaits {
  implicit val crypto: CryptoWithKeysFromConfig = app.injector.instanceOf[ApplicationCrypto].JsonCrypto
  implicit val bankDetailsModelFormat: Format[BankDetailsModel] = BankDetailsModel.format
  implicit val formats: OFormat[DisposeLiabilityReturn] = Json.format[DisposeLiabilityReturn]
  val deleteLiabilityReturnsService: DeleteLiabilityReturnsService = app.injector.instanceOf[DeleteLiabilityReturnsService]
  val date27DaysAgo: DateTime = DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(27)
  val date28DaysAgo: DateTime = date27DaysAgo.minusDays(1)
  val date29DaysAgo: DateTime = date27DaysAgo.minusDays(2)
  val periodKey = 2019

  override def additionalConfig(a: Map[String, Any]): Map[String, Any] = Map(
    "microservice.services.etmp-hod.host" -> wireMockHost,
    "microservice.services.etmp-hod.port" -> wireMockPort,
    "schedules.delete-liability-returns-job.cleardown.batchSize" -> 2
  )

  def generateFormBundleResponse(periodKey: Int): FormBundleReturn = {
    val formBundleAddress = FormBundleAddress("line1", "line2", None, None, None, "GB")
    val x = FormBundlePropertyDetails(Some("12345678"), formBundleAddress, additionalDetails = Some("supportingInfo"))
    val lineItem1 = FormBundleProperty(BigDecimal(5000000), new LocalDate(s"$periodKey-04-01"), new LocalDate(s"$periodKey-08-31"), "Liability", None)
    val lineItem2 = FormBundleProperty(BigDecimal(5000000), new LocalDate(s"$periodKey-09-01"), new LocalDate(s"${periodKey + 1}-03-31"), "Relief", Some("Relief"))

    FormBundleReturn(periodKey = periodKey.toString,
      propertyDetails = x,
      dateOfAcquisition = None,
      valueAtAcquisition = None,
      dateOfValuation = new LocalDate(s"$periodKey-05-05"),
      localAuthorityCode = None,
      professionalValuation = true,
      taxAvoidanceScheme = Some("taxAvoidanceScheme"),
      ninetyDayRuleApplies = true,
      dateOfSubmission = new LocalDate(s"$periodKey-05-05"),
      liabilityAmount = BigDecimal(123.23),
      paymentReference = "payment-ref-123",
      lineItem = Seq(lineItem1, lineItem2))
  }

  val formBundle: FormBundleReturn = generateFormBundleResponse(periodKey)
  val liabilityReturn = DisposeLiabilityReturn(atedRefNo = "ATE1234567XX", id = "101010", formBundle)
  val liabilityReturn2 = DisposeLiabilityReturn(atedRefNo = "ATE7654321XX", id = "010101", formBundle)
  val disposeLiability = DisposeLiability(Option(LocalDate.now()), periodKey)


  "deleteLiabilityReturnsService" should {
    def createAndRetrieveLiabilityReturn: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE1234567XX/dispose-liability/101010").get()

    def createAndRetrieveLiabilityReturn2: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE7654321XX/dispose-liability/010101").get()

    def updateLiabilityReturn() = hitApplicationEndpoint("/ated/ATE1234567XX/dispose-liability/101010/update-date").post(Json.toJson(disposeLiability))

    def updateLiabilityReturn2() = hitApplicationEndpoint("/ated/ATE7654321XX/dispose-liability/010101/update-date").post(Json.toJson(disposeLiability))

    "not delete any drafts" when {
      "the draft has only just been added" in {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        val result = for {
          insert <- createAndRetrieveLiabilityReturn
          deleteCount <- deleteLiabilityReturnsService.invoke
          retrieve <- createAndRetrieveLiabilityReturn
        } yield (insert, deleteCount, retrieve)

        val (insert, deleteCount, foundDraft) = await(result)
        insert.status mustBe OK
        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for 27 days" in {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        val result = for {
          insert <- createAndRetrieveLiabilityReturn
          _ <- deleteLiabilityReturnsService.repo.updateTimeStamp(liabilityReturn, date27DaysAgo)
          deleteCount <- deleteLiabilityReturnsService.invoke
          retrieve <- createAndRetrieveLiabilityReturn
        } yield (insert, deleteCount, retrieve)

        val (insert, deleteCount, foundDraft) = await(result)
        insert.status mustBe OK
        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }
    }

    "delete the liability return drafts" when {
      "the draft has been stored for 28 days" in {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        val result = for {
          _ <- createAndRetrieveLiabilityReturn
          _ <- deleteLiabilityReturnsService.repo.updateTimeStamp(liabilityReturn, date28DaysAgo)
          deleteCount <- deleteLiabilityReturnsService.invoke
          retrieve <- updateLiabilityReturn()
        } yield (deleteCount, retrieve)

        val (deleteCount, retrieve) = await(result)
        deleteCount mustBe 1
        retrieve.status mustBe NOT_FOUND
      }

      "the draft has been stored for 29 days" in {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        val result = for {
          _ <- createAndRetrieveLiabilityReturn
          _ <- deleteLiabilityReturnsService.repo.updateTimeStamp(liabilityReturn, date29DaysAgo)
          deleteCount <- deleteLiabilityReturnsService.invoke
          retrieve <- updateLiabilityReturn()
        } yield (deleteCount, retrieve)

        val (deleteCount, retrieve) = await(result)
        deleteCount mustBe 1
        retrieve.status mustBe NOT_FOUND
      }
    }

    "only delete outdated reliefs when multiple reliefs exist" in {
      stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)
      stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE7654321XX/form-bundle/010101", OK, Json.toJson(formBundle).toString)
      val result = for {
        _ <- createAndRetrieveLiabilityReturn
        _ <- createAndRetrieveLiabilityReturn2
        _ <- deleteLiabilityReturnsService.repo.updateTimeStamp(liabilityReturn, date29DaysAgo)
        deleteCount <- deleteLiabilityReturnsService.invoke
        retrieve <- updateLiabilityReturn()
        retrieve2 <- updateLiabilityReturn2()
      } yield (deleteCount, retrieve, retrieve2)

      val (deleteCount, deletedDraft, foundDraft) = await(result)
      deleteCount mustBe 1
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe OK
    }

    "delete multiple drafts when the batchSize is >1" in {
      stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)
      stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE7654321XX/form-bundle/010101", OK, Json.toJson(formBundle).toString)
      val result = for {
        _ <- createAndRetrieveLiabilityReturn
        _ <- createAndRetrieveLiabilityReturn2
        _ <- deleteLiabilityReturnsService.repo.updateTimeStamp(liabilityReturn, date28DaysAgo)
        _ <- deleteLiabilityReturnsService.repo.updateTimeStamp(liabilityReturn2, date29DaysAgo)
        deleteCount <- deleteLiabilityReturnsService.invoke
        retrieve <- updateLiabilityReturn()
        retrieve2 <- updateLiabilityReturn2()
      } yield (deleteCount, retrieve, retrieve2)

      val (deleteCount, deletedDraft, deletedDraft2) = await(result)
      deleteCount mustBe 2
      deletedDraft.status mustBe NOT_FOUND
      deletedDraft2.status mustBe NOT_FOUND
    }
  }
}
