/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.service

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.{AssertionHelpers, IntegrationSpec}
import models.{Reliefs, ReliefsTaxAvoidance, TaxAvoidance}
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.FutureAwaits
import repository.{ReliefCached, ReliefCachedError, ReliefsMongoRepository, ReliefsMongoWrapper}
import scheduler.DeleteReliefsService

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteReliefsServiceISpec extends IntegrationSpec with AssertionHelpers with FutureAwaits {
  val deleteReliefsService: DeleteReliefsService = app.injector.instanceOf[DeleteReliefsService]
  val dateOneMinAgo: ZonedDateTime =  ZonedDateTime.now(ZoneId.of("UTC")).minusMinutes(1)
  val date59DaysAgo: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC")).withHour(0).minusDays(59)
  val date60DaysAgo: ZonedDateTime = date59DaysAgo.minusDays(1)
  val date60DaysHrsMinsAgo: ZonedDateTime = date59DaysAgo.minusDays(1).minusHours(22).minusMinutes(59)
  val date61DaysAgo: ZonedDateTime = date59DaysAgo.minusDays(2)
  val date61DaysMinsAgo: ZonedDateTime = date59DaysAgo.minusDays(2).minusMinutes(1)
  val periodKey = 2019

  override def additionalConfig(a: Map[String, Any]): Map[String, Any] = Map(
    "microservice.services.auth.port" -> wireMockPort,
    "microservice.services.auth.host" -> wireMockHost,
    "schedules.delete-reliefs-job.cleardown.batchSize" -> 2
  )

  def periodStartDateConverter(periodKey: Int): LocalDate = LocalDate.of(periodKey, 4, 1)

  def periodEndDateConverter(periodKey: Int): LocalDate = periodStartDateConverter(periodKey).plusYears(1).minusDays(1)

  def reliefTaxAvoidance(atedRefNo: String): ReliefsTaxAvoidance = ReliefsTaxAvoidance(
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
    await(repo.ensureIndexes())
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
          case _ => fail()
        }
      }

      "the draft has only just been added" in new Setup {
        stubAuthPost

        await(createRelief)
        await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), dateOneMinAgo))
        val deleteCount: Int = await(deleteReliefsService.invoke())
        val retrieve: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

        deleteCount mustBe 0
        retrieve.status mustBe OK
      }

      "the draft has been stored for 59 days" in new Setup {
        stubAuthPost

        await(createRelief)
        await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date59DaysAgo))
        val deleteCount: Int = await(deleteReliefsService.invoke())
        val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

        deleteCount mustBe 0
        foundDraft.status mustBe OK
      }

      "the draft has been stored for 60 days 23 hrs and 59 mins" in new Setup {
        stubAuthPost

        await(createRelief)
        await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date60DaysHrsMinsAgo))
        val deleteCount: Int = await(deleteReliefsService.invoke())
        val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

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

        val deleteCount: Int = await(deleteReliefsService.invoke())
        val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

        deleteCount mustBe 1
        deletedDraft.status mustBe NOT_FOUND
      }

      "the draft has been stored for 61 days and 1 min" in new Setup {
        stubAuthPost

        await(createRelief)
        await(repo.updateTimeStamp(reliefTaxAvoidance("ATE1234567XX"), date61DaysMinsAgo))

        await(repo.collection.countDocuments().toFuture()) mustBe 1

        val deleteCount: Int = await(deleteReliefsService.invoke())
        val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())

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

      val deleteCount: Int = await(deleteReliefsService.invoke())
      val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())
      val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE7654321XX/ated/reliefs/$periodKey").get())

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

      val deleteCount: Int = await(deleteReliefsService.invoke())

      await(repo.collection.countDocuments().toFuture()) mustBe 0

      val deletedDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE1234567XX/ated/reliefs/$periodKey").get())
      val foundDraft: WSResponse = await(hitApplicationEndpoint(s"/ated/ATE7654321XX/ated/reliefs/$periodKey").get())

      deleteCount mustBe 2
      deletedDraft.status mustBe NOT_FOUND
      foundDraft.status mustBe NOT_FOUND
    }
  }
}
