package service

import helpers.{AssertionHelpers, IntegrationSpec}
import models.PropertyDetailsAddress
import play.api.libs.json.Json
import play.api.test.FutureAwaits
import repository.{PropertyDetailsMongoRepository, PropertyDetailsMongoWrapper}
import services.PropertyDetailsService

import scala.concurrent.ExecutionContext.Implicits.global

class PropertyDetailsServiceISpec extends IntegrationSpec with AssertionHelpers with FutureAwaits {

  val propertyDetailsService: PropertyDetailsService = app.injector.instanceOf[PropertyDetailsService]

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

  class Setup {
    val repo: PropertyDetailsMongoRepository = app.injector.instanceOf[PropertyDetailsMongoWrapper].apply()

    await(repo.collection.drop().toFuture())
    await(repo.ensureIndexes)
  }

  "deleteChargeableDraft" should {
    "delete a chargeable draft by id and ated ref" in new Setup {
      await(hitApplicationEndpoint("/ated/ATE1234568XX/property-details/create/2020")
        .post(Json.toJson(address)))

      await(repo.collection.countDocuments().toFuture()) mustBe 1

      val deleteCount = await(propertyDetailsService.deleteChargeableDraft("ATE1234568XX", "id")).size

      deleteCount mustBe 0
    }
  }
}
