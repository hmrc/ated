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
import repository.{PropertyDetailsMongoRepository, PropertyDetailsMongoWrapper}
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

  class Setup {
    val repo: PropertyDetailsMongoRepository = app.injector.instanceOf[PropertyDetailsMongoWrapper].apply()

    await(repo.drop)
    await(repo.ensureIndexes)
  }

  def createDraftProperty: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE1234567XX/property-details/create/2019")
    .post(Json.toJson(address))

  def createDraftProperty2: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE7654321XX/property-details/create/2019")
    .post(Json.toJson(address.copy(line_1 = "New Line 1")))

  "documentUpdateService" should {
    "not delete any drafts" when {
      "the draft has only just been added" in new Setup {
        val draft: WSResponse = await(createDraftProperty)
        val deleteCount: Int = await(documentUpdateService.invoke)
        val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())

        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for 27 days" in new Setup {
        val draft: WSResponse = await(createDraftProperty)
        await(repo.updateTimeStamp(createPropertyDetails(draft.json, address), date27DaysAgo))
        val deleteCount: Int = await(documentUpdateService.invoke)
        val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())

        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }
    }

    "delete draft property details" when {
      "the draft has been stored for exactly 28 days" in new Setup {
        val draft: WSResponse = await(createDraftProperty)
        await(repo.updateTimeStamp(createPropertyDetails(draft.json, address), date28DaysAgo))

        await(repo.collection.count()) mustBe 1

        val deleteCount: Int = await(documentUpdateService.invoke)
        val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())

        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
      }

      "the draft have been stored longer than 28 days" in new Setup {
        val draft: WSResponse = await(createDraftProperty)
        await(repo.updateTimeStamp(createPropertyDetails(draft.json, address), date29DaysAgo))

        await(repo.collection.count()) mustBe 1

        val deleteCount: Int = await(documentUpdateService.invoke)
        val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())

        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
			}
    }

    "only delete outdated drafts when multiple drafts exist" in new Setup {
      val draft: WSResponse = await(createDraftProperty)
      val draft2: WSResponse = await(createDraftProperty2)
      await(repo.updateTimeStamp(createPropertyDetails(draft.json, address), date29DaysAgo))

      await(repo.collection.count()) mustBe 2

      val deleteCount: Int = await(documentUpdateService.invoke)
      val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())
      val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE7654321XX/property-details/retrieve/${(draft2.json \ "id").as[String]}").get())

      deleteCount mustBe 1
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe OK
    }

    "delete multiple drafts when the batch size is >1" in new Setup {
      val draft: WSResponse = await(createDraftProperty)
      val draft2: WSResponse = await(createDraftProperty2)
      await(repo.updateTimeStamp(createPropertyDetails(draft.json, address), date29DaysAgo))
      await(repo.updateTimeStamp(createPropertyDetails(draft2.json, address), date29DaysAgo))

      await(repo.collection.count()) mustBe 2

      val deleteCount: Int = await(documentUpdateService.invoke)

      deleteCount mustBe 2
    }
  }
}
