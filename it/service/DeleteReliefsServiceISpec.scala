package service

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.{AssertionHelpers, IntegrationSpec}
import models.{Reliefs, ReliefsTaxAvoidance, TaxAvoidance}
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.FutureAwaits
import scheduler.DeleteReliefsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteReliefsServiceISpec extends IntegrationSpec with AssertionHelpers with FutureAwaits {
  val deleteReliefsService: DeleteReliefsService = app.injector.instanceOf[DeleteReliefsService]
  val date27DaysAgo: DateTime = DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(27)
  val date28DaysAgo: DateTime = date27DaysAgo.minusDays(1)
  val date29DaysAgo: DateTime = date27DaysAgo.minusDays(2)
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

  "deleteReliefsService" should {
    def createRelief: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE1234567XX/ated/reliefs/save")
      .post(Json.toJson(reliefTaxAvoidance("ThisGetsOverwritten")))

    def createRelief2: Future[WSResponse] = hitApplicationEndpoint("/ated/ATE7654321XX/ated/reliefs/save")
      .post(Json.toJson(reliefTaxAvoidance("SoDoesThis")))

    "not delete any drafts" when {
      "the draft has only just been added" in {
        stubAuthPost
        val result = for {
          _ <- createRelief
          deleteCount <- deleteReliefsService.invoke
          retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get()
        } yield (deleteCount, retrieve)

        val (deleteCount, foundDraft) = await(result)
        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for 27 days" in {
        stubAuthPost
        val result = for {
          _ <- createRelief
          _ <- deleteReliefsService.repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date27DaysAgo)
          deleteCount <- deleteReliefsService.invoke
          retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get()
        } yield (deleteCount, retrieve)

        val (deleteCount, foundDraft) = await(result)
        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }
    }

    "delete the relief drafts" when {
      "the draft has been stored for 28 days" in {
        stubAuthPost
        val result = for {
          _ <- createRelief
          _ <- deleteReliefsService.repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date28DaysAgo)
          deleteCount <- deleteReliefsService.invoke
          retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get()
        } yield (deleteCount, retrieve)

        val (deleteCount, deletedDraft) = await(result)
        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
      }

      "the draft has been stored for 29 days" in {
        stubAuthPost
        val result = for {
          _ <- createRelief
          _ <- deleteReliefsService.repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date29DaysAgo)
          deleteCount <- deleteReliefsService.invoke
          retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get()
        } yield (deleteCount, retrieve)

        val (deleteCount, deletedDraft) = await(result)
        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
      }
    }

    "only delete outdated reliefs when multiple reliefs exist" in {
      stubAuthPost
      val result = for {
        _ <- createRelief
        _ <- createRelief2
        _ <- deleteReliefsService.repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date29DaysAgo)
        deleteCount <- deleteReliefsService.invoke
        retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get()
        retrieve2 <- hitApplicationEndpoint(s"/ated/ATE7654321XX/ated/reliefs/$periodKey").get()
      } yield (deleteCount, retrieve, retrieve2)

      val (deleteCount, deletedDraft, foundDraft) = await(result)
      deleteCount mustBe 1
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe OK
    }

    "delete multiple drafts when the batchSize is >1" in {
      stubAuthPost
      val result = for {
        _ <- createRelief
        _ <- createRelief2
        _ <- deleteReliefsService.repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date29DaysAgo)
        _ <- deleteReliefsService.repo.updateTimeStamp(reliefTaxAvoidance("ATE7654321XX"), date29DaysAgo)
        deleteCount <- deleteReliefsService.invoke
        retrieve <- hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get()
        retrieve2 <- hitApplicationEndpoint(s"/ated/ATE7654321XX/ated/reliefs/$periodKey").get()
      } yield (deleteCount, retrieve, retrieve2)

      val (deleteCount, deletedDraft, foundDraft) = await(result)
      deleteCount mustBe 2
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe NOT_FOUND
    }

  }
}
