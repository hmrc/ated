package service

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.{AssertionHelpers, IntegrationSpec}
import models.{Reliefs, ReliefsTaxAvoidance, TaxAvoidance}
import org.joda.time.{DateTime, LocalDate}
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.FutureAwaits
import repository.{ReliefCached, ReliefCachedError, ReliefsMongoRepository, ReliefsMongoWrapper}
import scheduler.DeleteReliefsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteReliefsServiceISpec extends IntegrationSpec with AssertionHelpers with FutureAwaits {
  val deleteReliefsService: DeleteReliefsService = app.injector.instanceOf[DeleteReliefsService]
  val date59DaysAgo: DateTime = DateTime.now.withHourOfDay(0).minusDays(59)
  val date60DaysAgo: DateTime = date59DaysAgo.minusDays(1)
  val date60DaysHrsMinsAgo: DateTime = date59DaysAgo.minusDays(1).minusHours(22).minusMinutes(59)
  val date61DaysAgo: DateTime = date59DaysAgo.minusDays(2)
  val date61DaysMinsAgo: DateTime = date59DaysAgo.minusDays(2).minusMinutes(1)
  val dateOneMinAgo: DateTime =  DateTime.now.minusMinutes(1)

  val periodKey = 2019

  override def additionalConfig(a: Map[String, Any]): Map[String, Any] = Map(
    "microservice.services.auth.port" -> wireMockPort,
    "microservice.services.auth.host" -> wireMockHost,
    "schedules.delete-reliefs-job.cleardown.batchSize" -> 2
  )

  def periodStartDateConverter(periodKey: Int): LocalDate = new LocalDate(s"$periodKey-04-01")

  def periodEndDateConverter(periodKey: Int): LocalDate = periodStartDateConverter(periodKey).plusYears(1).minusDays(1)

  def reliefTaxAvoidance(atedRefNo: String) = ReliefsTaxAvoidance(
    atedRefNo = atedRefNo,
    periodKey = periodKey,
    reliefs = Reliefs(periodKey = periodKey),
    taxAvoidance = TaxAvoidance(),
    periodStartDate = periodStartDateConverter(periodKey),
    periodEndDate = periodEndDateConverter(periodKey)
  )

  def stubAuthPost: StubMapping = stubbedPost("/auth/authorise", OK,
    """{
      |  "allEnrolments": [{
      |    "key": "HMRC-AGENT-AGENT",
      |    "identifiers": [{ "key": "AgentRefNumber", "value": "24680246" }],
      |    "state": "Activated"
      |   }]
      |}""".stripMargin
  )

  class Setup {
    val repo: ReliefsMongoRepository = app.injector.instanceOf[ReliefsMongoWrapper].apply()

    await(repo.collection.drop().toFuture())
    await(repo.ensureIndexes)
  }

  "deleteReliefsService" should {
    def createRelief: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE1234567XX/ated/reliefs/save")
      .post(Json.toJson(reliefTaxAvoidance("ThisGetsOverwritten")))

    def createRelief2: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE7654321XX/ated/reliefs/save")
      .post(Json.toJson(reliefTaxAvoidance("SoDoesThis")))

    "not delete any drafts" when {
      "when the timestamp cannot be updated" in new Setup {
        await(createRelief)
        val res: ReliefCached = await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234569XX"), dateOneMinAgo))

        res match {
          case ReliefCachedError => ()
          case _ => fail
        }
      }

      "the draft has only just been added" in new Setup {
        stubAuthPost

        await(createRelief)
        await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), dateOneMinAgo))
        val deleteCount = await(deleteReliefsService.invoke())
        val retrieve = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

        deleteCount mustBe 0
        retrieve.status mustBe OK
      }

      "the draft has been stored for 59 days" in new Setup {
        stubAuthPost

        await(createRelief)
        await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date59DaysAgo))
        val deleteCount = await(deleteReliefsService.invoke())
        val foundDraft = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for 60 days 23 hrs and 59 mins" in new Setup {
        stubAuthPost

        await(createRelief)
        await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date60DaysHrsMinsAgo))
        val deleteCount = await(deleteReliefsService.invoke())
        val foundDraft = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }
    }

    "delete the relief drafts" when {
      "the draft has been stored for 61 days" in new Setup {
        stubAuthPost

        await(createRelief)
        await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date61DaysAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount = await(deleteReliefsService.invoke())
        val deletedDraft = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
      }

      "the draft has been stored for 61 days and 1 min" in new Setup {
        stubAuthPost

        await(createRelief)
        await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date61DaysMinsAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount = await(deleteReliefsService.invoke())
        val deletedDraft = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
      }
    }

    "only delete outdated reliefs when multiple reliefs exist for 60 days" in new Setup {
      stubAuthPost

      await(createRelief)
      await(createRelief2)
      await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date61DaysAgo))
      await(repo.updateTimeStamp(reliefTaxAvoidance("ATE7654321XX"), date59DaysAgo))


      await(repo.collection.countDocuments().toFuture()) mustBe 2

      val deleteCount = await(deleteReliefsService.invoke())
      val deletedDraft = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())
      val foundDraft = await(hitApplicationEndpoint(s"/ated/ATE7654321XX/ated/reliefs/$periodKey").get())

      deleteCount mustBe 1
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe OK
    }

    "delete multiple drafts when the batchSize is >1 for 60 days" in new Setup {
      stubAuthPost

      await(createRelief)
      await(createRelief2)
      await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date61DaysMinsAgo))
      await(repo.updateTimeStamp(reliefTaxAvoidance("ATE7654321XX"), date61DaysAgo))

      await(repo.collection.countDocuments().toFuture()) mustBe 2

      val deleteCount = await(deleteReliefsService.invoke())

      await(repo.collection.countDocuments().toFuture()) mustBe 0

      val deletedDraft = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())
      val foundDraft = await(hitApplicationEndpoint(s"/ated/ATE7654321XX/ated/reliefs/$periodKey").get())

      deleteCount mustBe 2
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe NOT_FOUND
    }
  }
}
