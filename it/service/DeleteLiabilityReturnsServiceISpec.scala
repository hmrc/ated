package service

import helpers.{AssertionHelpers, IntegrationSpec}
import models._
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.http.Status._
import play.api.libs.json.{Format, Json, OFormat}
import play.api.libs.ws.WSResponse
import play.api.test.FutureAwaits
import repository.{DisposeLiabilityReturnMongoRepository, DisposeLiabilityReturnMongoWrapper}
import scheduler.DeleteLiabilityReturnsService
import uk.gov.hmrc.crypto.{ApplicationCrypto, CryptoWithKeysFromConfig}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteLiabilityReturnsServiceISpec extends IntegrationSpec with AssertionHelpers with FutureAwaits {
  implicit val crypto: CryptoWithKeysFromConfig = app.injector.instanceOf[ApplicationCrypto].JsonCrypto
  implicit val bankDetailsModelFormat: Format[BankDetailsModel] = BankDetailsModel.format
  implicit val formats: OFormat[DisposeLiability] = DisposeLiability.formats

  val deleteLiabilityReturnsService: DeleteLiabilityReturnsService = app.injector.instanceOf[DeleteLiabilityReturnsService]
  val justAdded: DateTime = DateTime.now(DateTimeZone.UTC).minusMinutes(1)
  val date59DaysAgo: DateTime = DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(59)
  val date60DaysAgo: DateTime = date59DaysAgo.minusDays(1)
  val date60DaysHrsMinsAgo: DateTime = date59DaysAgo.minusDays(1).minusHours(23).minusMinutes(59)
  val date61DaysAgo: DateTime = date59DaysAgo.minusDays(2)
  val date61DaysMinsAgo: DateTime = date59DaysAgo.minusDays(2).minusMinutes(1)
  val periodKey = 2019

  override def additionalConfig(a: Map[String, Any]): Map[String, Any] = Map(
    "microservice.services.etmp-hod.host" -> wireMockHost,
    "microservice.services.etmp-hod.port" -> wireMockPort,
    "schedules.delete-liability-returns-job.cleardown.batchSize" -> 20
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
  val liabilityReturn: DisposeLiabilityReturn = DisposeLiabilityReturn(atedRefNo = "ATE1234567XX", id = "101010", formBundle)
  val liabilityReturn2: DisposeLiabilityReturn = DisposeLiabilityReturn(atedRefNo = "ATE7654321XX", id = "010101", formBundle)
  val liabilityReturn3: DisposeLiabilityReturn = DisposeLiabilityReturn(atedRefNo = "ATE1234568XX", id = "101012", formBundle)

  val disposeLiability: DisposeLiability = DisposeLiability(Option(LocalDate.now()), periodKey)

  class Setup {
    val repo: DisposeLiabilityReturnMongoRepository = app.injector.instanceOf[DisposeLiabilityReturnMongoWrapper].apply()

    await(repo.collection.drop().toFuture())
    await(repo.ensureIndexes)
  }

  "deleteLiabilityReturnsService" should {
    def createAndRetrieveLiabilityReturn: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE1234567XX/dispose-liability/101010").get()

    def createAndRetrieveLiabilityReturn2: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE7654321XX/dispose-liability/010101").get()

    def createAndRetrieveLiabilityReturn3: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE1234568XX/dispose-liability/101012").get()

    def updateLiabilityReturn() = hitApplicationEndpoint("/ated/ATE1234567XX/dispose-liability/101010/update-date").post(Json.toJson(disposeLiability))

    def updateLiabilityReturn2() = hitApplicationEndpoint("/ated/ATE7654321XX/dispose-liability/010101/update-date").post(Json.toJson(disposeLiability))

    def updateLiabilityReturn3() = hitApplicationEndpoint("/ated/ATE1234568XX/dispose-liability/101012/update-date").post(Json.toJson(disposeLiability))

    "not delete any drafts 60 days" when {
      "the draft has only just been added" in new Setup {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        val insert: WSResponse = await(createAndRetrieveLiabilityReturn)
        await(repo.updateTimeStamp(liabilityReturn, justAdded))

        val deleteCount: Int = await(deleteLiabilityReturnsService.invoke())
        val foundDraft: WSResponse = await(createAndRetrieveLiabilityReturn)

        insert.status mustBe OK
        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for 59 days" in new Setup {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        val insert: WSResponse = await(createAndRetrieveLiabilityReturn)
        await(repo.updateTimeStamp(liabilityReturn, date59DaysAgo))

        val deleteCount: Int = await(deleteLiabilityReturnsService.invoke())
        val foundDraft: WSResponse = await(createAndRetrieveLiabilityReturn)

        insert.status mustBe OK
        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for 60 days" in new Setup {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        await(createAndRetrieveLiabilityReturn)
        await(repo.updateTimeStamp(liabilityReturn, date60DaysAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount: Int = await(deleteLiabilityReturnsService.invoke())
        val retrieve: WSResponse = await(updateLiabilityReturn())

        deleteCount mustBe 0
        retrieve.status mustBe OK
      }

      "the draft has been stored for 60 days 23hr and 59mins" in new Setup {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        await(createAndRetrieveLiabilityReturn)
        await(repo.updateTimeStamp(liabilityReturn, date60DaysHrsMinsAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount: Int = await(deleteLiabilityReturnsService.invoke())
        val retrieve: WSResponse = await(updateLiabilityReturn())

        deleteCount mustBe 0
        retrieve.status mustBe OK
      }
    }

    "delete the liability return drafts" when {
      "the draft has been stored for 61 days" in new Setup {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        await(createAndRetrieveLiabilityReturn)
        await(repo.updateTimeStamp(liabilityReturn, date61DaysAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount: Int = await(deleteLiabilityReturnsService.invoke())
        val retrieve: WSResponse = await(updateLiabilityReturn())

        deleteCount mustBe 1
        retrieve.status mustBe NOT_FOUND
      }

      "the draft has been stored for 61 days and 1 min" in new Setup {
        stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)

        await(createAndRetrieveLiabilityReturn)
        await(repo.updateTimeStamp(liabilityReturn, date61DaysMinsAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount: Int = await(deleteLiabilityReturnsService.invoke())
        val retrieve: WSResponse = await(updateLiabilityReturn())

        deleteCount mustBe 1
        retrieve.status mustBe NOT_FOUND
      }
    }

    "only delete outdated reliefs when multiple reliefs exist for 60 days" in new Setup {
      stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)
      stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE7654321XX/form-bundle/010101", OK, Json.toJson(formBundle).toString)

      await(createAndRetrieveLiabilityReturn)
      await(createAndRetrieveLiabilityReturn2)
      await(repo.updateTimeStamp(liabilityReturn, date61DaysAgo))
      await(repo.updateTimeStamp(liabilityReturn2, date60DaysHrsMinsAgo))

      await(repo.collection.countDocuments().toFuture()) mustBe 2

      val deleteCount: Int = await(deleteLiabilityReturnsService.invoke())
      val deletedDraft: WSResponse = await(updateLiabilityReturn())
      val foundDraft: WSResponse = await(updateLiabilityReturn2())

      deleteCount mustBe 1
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe OK
    }

    "delete multiple drafts when the batchSize is >1 for 60 days" in new Setup {
      stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234567XX/form-bundle/101010", OK, Json.toJson(formBundle).toString)
      stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE7654321XX/form-bundle/010101", OK, Json.toJson(formBundle).toString)
      stubbedGet("/annual-tax-enveloped-dwellings/returns/ATE1234568XX/form-bundle/101012", OK, Json.toJson(formBundle).toString)

      await(createAndRetrieveLiabilityReturn)
      await(createAndRetrieveLiabilityReturn2)
      await(createAndRetrieveLiabilityReturn3)
      await(repo.updateTimeStamp(liabilityReturn, date61DaysAgo))
      await(repo.updateTimeStamp(liabilityReturn2, date61DaysMinsAgo))
      await(repo.updateTimeStamp(liabilityReturn3, date60DaysHrsMinsAgo))

      await(repo.collection.countDocuments().toFuture()) mustBe 3

      val deleteCount: Int = await(deleteLiabilityReturnsService.invoke())
      val deletedDraft: WSResponse = await(updateLiabilityReturn())
      val deletedDraft2: WSResponse = await(updateLiabilityReturn2())
      val foundDraft: WSResponse = await(updateLiabilityReturn3())

      deleteCount mustBe 2
      deletedDraft.status mustBe NOT_FOUND
      deletedDraft2.status mustBe NOT_FOUND
      foundDraft.status mustBe OK
    }
  }
}
