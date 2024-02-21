package service

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.{AssertionHelpers, IntegrationSpec}
import models.{Reliefs, ReliefsTaxAvoidance, TaxAvoidance}
import java.time.LocalDate
import play.api.http.Status._
import play.api.test.FutureAwaits
import repository.{ReliefsMongoRepository, ReliefsMongoWrapper}
import services.ReliefsService

import scala.concurrent.ExecutionContext.Implicits.global

class ReliefsServiceISpec extends IntegrationSpec with AssertionHelpers with FutureAwaits {
  val reliefsService: ReliefsService = app.injector.instanceOf[ReliefsService]

  override def additionalConfig(a: Map[String, Any]): Map[String, Any] = Map(
    "microservice.services.auth.port" -> wireMockPort,
    "microservice.services.auth.host" -> wireMockHost,
    "schedules.delete-reliefs-job.cleardown.batchSize" -> 2
  )

  def reliefTaxAvoidance(atedRefNo: String, periodKey: Int) = {

    val startDate: LocalDate = new LocalDate(s"$periodKey-04-01")

    ReliefsTaxAvoidance(
      atedRefNo = atedRefNo,
      periodKey = periodKey,
      reliefs = Reliefs(periodKey = periodKey),
      taxAvoidance = TaxAvoidance(),
      periodStartDate = startDate,
      periodEndDate = startDate.plusYears(1).minusDays(1)
    )
  }

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
    await(repo.ensureIndexes())

  }

  ".deleteAllDraftReliefs" should {
    "delete all draft reliefs for a provided ated reference" in new Setup {
      val periodKey = 2020

      await(repo.cacheRelief(reliefTaxAvoidance("123456789", periodKey)))
      await(repo.collection.find().toFuture()).size must be(1)
      await(reliefsService.deleteAllDraftReliefs("123456789"))
      await(repo.collection.find().toFuture()).size must be(0)
    }
    "not throw an exception if draft reliefs cannot be removed due to an error" in new Setup {
      await(reliefsService.deleteAllDraftReliefs("123456789")).size must be(0)
    }
  }

  ".deleteAllDraftReliefByYear" should {
    "delete all draft reliefs by year" in new Setup {
      val periodKey2019 = 2019
      val periodKey2020 = 2020

      await(repo.cacheRelief(reliefTaxAvoidance("123456719", 2018)))
      await(repo.cacheRelief(reliefTaxAvoidance("123456719", 2017)))
      await(repo.cacheRelief(reliefTaxAvoidance("123456719", periodKey2019)))
      await(repo.cacheRelief(reliefTaxAvoidance("123456720", periodKey2020)))
      await(repo.collection.find().toFuture()).size must be(4)
      val remaining = await(reliefsService.deleteAllDraftReliefByYear("123456719", periodKey2019))
      remaining.size must be(0)
      await(repo.collection.find().toFuture()).size must be(3)
    }
    "not throw an exception if draft reliefs cannot be deleted by year" in new Setup {
      val periodKey2019 = 2019

      await(reliefsService.deleteAllDraftReliefByYear("123456789", periodKey2019)).size must be(0)
    }
  }
}
