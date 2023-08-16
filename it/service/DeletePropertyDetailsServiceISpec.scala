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
import uk.gov.hmrc.crypto.{ApplicationCrypto, Encrypter, Decrypter}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeletePropertyDetailsServiceISpec extends IntegrationSpec with AssertionHelpers with FutureAwaits {
  implicit val crypto: Encrypter with Decrypter = app.injector.instanceOf[ApplicationCrypto].JsonCrypto
  implicit val bankDetailsModelFormat: Format[BankDetailsModel] = BankDetailsModel.format
  implicit val formats: OFormat[PropertyDetails] = Json.format[PropertyDetails]

  val documentUpdateService: DeletePropertyDetailsService = app.injector.instanceOf[DeletePropertyDetailsService]
  val dateOneMinAgo: DateTime = DateTime.now(DateTimeZone.UTC).minusMinutes(1)
  val date59DaysAgo: DateTime = DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(59)
  val date60DaysAgo: DateTime = date59DaysAgo.minusDays(1)
  val date60DaysHrsMinsAgo: DateTime = date59DaysAgo.minusDays(1).minusHours(23).minusMinutes(59)
  val date61DaysAgo: DateTime = date59DaysAgo.minusDays(2)
  val date61DaysMinsAgo: DateTime = date59DaysAgo.minusDays(2).minusMinutes(1)

  override def additionalConfig(a: Map[String, Any]): Map[String, Any] = Map(
    "schedules.delete-property-details-job.cleardown.batchSize" -> 2
  )

  val address: PropertyDetailsAddress = PropertyDetailsAddress(
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

  def createPropertyDetails60(json: JsValue, addressProp: PropertyDetailsAddress): PropertyDetails = {
    PropertyDetails(
      atedRefNo = (json \ "atedRefNo").as[String],
      id = (json \ "id").as[String],
      periodKey = 2020,
      addressProperty = addressProp
    )
  }

  class Setup {
    val repo: PropertyDetailsMongoRepository = app.injector.instanceOf[PropertyDetailsMongoWrapper].apply()

    await(repo.collection.drop().toFuture())
    await(repo.ensureIndexes())
  }

  def createDraftProperty60: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE1234568XX/property-details/create/2020")
    .post(Json.toJson(address))

  def createDraftProperty260: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE7654322XX/property-details/create/2020")
    .post(Json.toJson(address.copy(line_1 = "New Line 1")))

  "documentUpdateService" should {
    "not delete any drafts" when {
      "the draft has only just been added" in new Setup {
        val draft: WSResponse = await(createDraftProperty60)
        await(repo.updateTimeStamp(createPropertyDetails60(draft.json, address), dateOneMinAgo))
        val deleteCount: Int = await(documentUpdateService.invoke())
        val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234568XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())

        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for 59 days" in new Setup {
        val draft: WSResponse = await(createDraftProperty60)
        await(repo.updateTimeStamp(createPropertyDetails60(draft.json, address), date59DaysAgo))

        val deleteCount: Int = await(documentUpdateService.invoke())
        val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234568XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())

        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for exactly 60 days 23hrs and 59mins" in new Setup {
        val draft: WSResponse = await(createDraftProperty60)
        await(repo.updateTimeStamp(createPropertyDetails60(draft.json, address), date60DaysHrsMinsAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount: Int = await(documentUpdateService.invoke())
        val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234568XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())

        deleteCount mustBe 0
        deletedDraft.status mustBe OK
      }
    }

    "delete draft property details" when {
      "the draft have been stored longer than 61 days" in new Setup {
        val draft: WSResponse = await(createDraftProperty60)
        await(repo.updateTimeStamp(createPropertyDetails60(draft.json, address), date61DaysAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount: Int = await(documentUpdateService.invoke())
        val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234568XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())

        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
      }

      "the draft have been stored longer than 61 days and 1 min" in new Setup {
        val draft: WSResponse = await(createDraftProperty60)
        await(repo.updateTimeStamp(createPropertyDetails60(draft.json, address), date61DaysMinsAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount: Int = await(documentUpdateService.invoke())
        val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234568XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())

        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
      }
    }

    "only delete outdated drafts when multiple drafts exist for 60days" in new Setup {
      val draft: WSResponse = await(createDraftProperty60)
      val draft2: WSResponse = await(createDraftProperty260)
      await(repo.updateTimeStamp(createPropertyDetails60(draft.json, address), date61DaysAgo))
      await(repo.updateTimeStamp(createPropertyDetails60(draft2.json, address), date60DaysHrsMinsAgo))

      await(repo.collection.countDocuments().toFuture()) mustBe 2

      val deleteCount: Int = await(documentUpdateService.invoke())
      val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234568XX/property-details/retrieve/${(draft.json \ "id").as[String]}").get())
      val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE7654322XX/property-details/retrieve/${(draft2.json \ "id").as[String]}").get())

      deleteCount mustBe 1
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe OK
    }

    "delete multiple drafts when the batch size is >1 for 60days" in new Setup {
      val draft: WSResponse = await(createDraftProperty60)
      val draft2: WSResponse = await(createDraftProperty260)
      await(repo.updateTimeStamp(createPropertyDetails60(draft.json, address), date61DaysAgo))
      await(repo.updateTimeStamp(createPropertyDetails60(draft2.json, address), date61DaysAgo))

      await(repo.collection.countDocuments().toFuture()) mustBe 2

      val deleteCount: Int = await(documentUpdateService.invoke())

      deleteCount mustBe 2
    }
  }
}
