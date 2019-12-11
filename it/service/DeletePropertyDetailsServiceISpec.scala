package service

import helpers.{AssertionHelpers, IntegrationSpec}
import models.{BankDetailsModel, PropertyDetails, PropertyDetailsAddress}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.http.Status._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import play.api.libs.json.{Format, JsValue, Json, OFormat}
import play.api.libs.ws.WSResponse
import play.api.test.FutureAwaits
import scheduler.DeletePropertyDetailsService
import uk.gov.hmrc.crypto.{ApplicationCrypto, CryptoWithKeysFromConfig}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeletePropertyDetailsServiceISpec extends IntegrationSpec with AssertionHelpers with FutureAwaits {
  implicit val crypto: CryptoWithKeysFromConfig = app.injector.instanceOf[ApplicationCrypto].JsonCrypto
  implicit val bankDetailsModelFormat: Format[BankDetailsModel] = BankDetailsModel.format
  implicit val formats: OFormat[PropertyDetails] = Json.format[PropertyDetails]

  val documentUpdateService: DeletePropertyDetailsService = app.injector.instanceOf[DeletePropertyDetailsService]
  val date27DaysAgo: DateTime = DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(27)
  val date28DaysAgo: DateTime = date27DaysAgo.minusDays(1)
  val date29DaysAgo: DateTime = date27DaysAgo.minusDays(2)

  override def additionalConfig(a: Map[String, Any]): Map[String, Any] = Map(
    "schedules.delete-property-details-job.cleardown.batchSize" -> 2
  )

  val address = PropertyDetailsAddress(
    line_1 = "Test line 1",
    line_2 = "Test line 2",
    line_3 = Some("Test line 3"),
    line_4 = None,
    postcode = None
  )

  def createPropertyDetails(json: JsValue, addressProp: PropertyDetailsAddress): PropertyDetails = {
    PropertyDetails(
      atedRefNo = (json \ "atedRefNo").as[String],
      id = (json \ "id").as[String],
      periodKey = 2019,
      addressProperty = addressProp
    )
  }

  def createDraftProperty: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE1234567XX/property-details/create/2019")
    .post(Json.toJson(address))

  def createDraftProperty2: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE7654321XX/property-details/create/2019")
    .post(Json.toJson(address.copy(line_1 = "New Line 1")))

  "documentUpdateService" should {
    "not delete any drafts" when {
      "the draft has only just been added" in {
        val result = for {
          draft <- createDraftProperty
          deleteCount <- documentUpdateService.invoke
          retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get()
        } yield (deleteCount, retrieve)

        val (deleteCount, foundDraft) = await(result)
        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for 27 days" in {
        val result = for {
          draft <- createDraftProperty
          _ <- documentUpdateService.repo.updateTimeStamp(createPropertyDetails(draft.json, address), date27DaysAgo)
          deleteCount <- documentUpdateService.invoke
          retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get()
        } yield (deleteCount, retrieve)

        val (deleteCount, foundDraft) = await(result)
        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }
    }

    "delete draft property details" when {
      "the draft has been stored for exactly 28 days" in {
        val result = for {
          draft <- createDraftProperty
          _ <- documentUpdateService.repo.updateTimeStamp(createPropertyDetails(draft.json, address), date28DaysAgo)
          deleteCount <- documentUpdateService.invoke
          retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get()
        } yield (deleteCount, retrieve)

        val (deleteCount, deletedDraft) = await(result)
        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
      }

      "the draft have been stored longer than 28 days" in {
        val result = for {
          draft <- createDraftProperty
          _ <- documentUpdateService.repo.updateTimeStamp(createPropertyDetails(draft.json, address), date28DaysAgo)
          deleteCount <- documentUpdateService.invoke
          retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get()
        } yield (deleteCount, retrieve)

        val (deleteCount, deletedDraft) = await(result)
        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
			}
    }

    "only delete outdated drafts when multiple drafts exist" in {
      val result = for {
        draft <- createDraftProperty
        draft2 <- createDraftProperty2
        _ <- documentUpdateService.repo.updateTimeStamp(createPropertyDetails(draft.json, address), date29DaysAgo)
        deleteCount <- documentUpdateService.invoke
        retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get()
        retrieve2 <- hitApplicationEndpoint(s"/ated/ATE7654321XX/property-details/retrieve/${(draft2.json \ "id").as[String]}").get()

      } yield (deleteCount, retrieve, retrieve2)

      val (deleteCount, deletedDraft, foundDraft) = await(result)
      deleteCount mustBe 1
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe OK
    }

    "delete multiple drafts when the batch size is >1" in {
      val result = for {
        draft <- createDraftProperty
        draft2 <- createDraftProperty2
        _ <- documentUpdateService.repo.updateTimeStamp(createPropertyDetails(draft.json, address), date29DaysAgo)
        _ <- documentUpdateService.repo.updateTimeStamp(createPropertyDetails(draft2.json, address), date29DaysAgo)
        deleteCount <- documentUpdateService.invoke
      } yield deleteCount

      await(result) mustBe 2
    }
  }
}
