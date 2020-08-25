/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import builders.PropertyDetailsBuilder
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.PropertyDetailsValuesService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future

class PropertyDetailsValuesControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar {

  val mockPropertyDetailsService: PropertyDetailsValuesService = mock[PropertyDetailsValuesService]

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]
    val testAccountRef = "ATED1223123"
    lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))

    class TestPropertyDetailsController extends BackendController(cc) with PropertyDetailsValuesController {
      val propertyDetailsService = mockPropertyDetailsService
    }

    val controller = new TestPropertyDetailsController()
  }

  "PropertyDetailsValuesController" must {
    "saveDraftHasValueChanged" must {
      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val updated = true
        when(mockPropertyDetailsService.cacheDraftHasValueChanged(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftHasValueChanged(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val updated = true
        when(mockPropertyDetailsService.cacheDraftHasValueChanged(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftHasValueChanged(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsAcquisition" must {
      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val updated = true
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsAcquisition(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val updated = true
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsAcquisition(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsRevalued" must {
      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val update = new PropertyDetailsRevalued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsRevalued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val update = new PropertyDetailsRevalued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsRevalued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsOwnedBefore" must {
      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val update = PropertyDetailsOwnedBefore()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsOwnedBefore(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val update = new PropertyDetailsOwnedBefore()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsOwnedBefore(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsIsNewBuild" must {
      "respond with OK when saving the IsNewBuildFlag" in new Setup {
        val update = new PropertyDetailsIsNewBuild()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsIsNewBuild(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsIsNewBuild(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the IsNewBuildFlag" in new Setup {
        val update = new PropertyDetailsIsNewBuild()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsIsNewBuild(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsIsNewBuild(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsNewBuildDates" must {
      "respond with an Ok when successfully saving the new build dates" in new Setup {
        val update = new PropertyDetailsNewBuildDates()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuildDates(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewBuildDates(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the new build dates" in new Setup {
        val update = new PropertyDetailsNewBuildDates()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuildDates(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewBuildDates(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsNewBuildValue" must {
      "respond with OK when saving the New Build Value" in new Setup {
        val update = new PropertyDetailsNewBuildValue()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuildValue(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewBuildValue(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the New Build Value" in new Setup {
        val update = new PropertyDetailsNewBuildValue()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuildValue(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewBuildValue(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsValueAcquired" must {
      "respond with OK when saving the value on acquisition" in new Setup {
        val update = new PropertyDetailsValueOnAcquisition()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsValueAcquired(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsValueAcquired(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the Value acquired" in new Setup {
        val update = new PropertyDetailsValueOnAcquisition()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsValueAcquired(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsValueAcquired(testAccountRef, id = "1").apply(fakeRequest)

        status(result) must be(BAD_REQUEST)
      }
    }


    "saveDraftPropertyDetailsDatesAcquired" must {
      "respond with OK when saving the dates which the property was acquired" in new Setup {
        val update = new PropertyDetailsDateOfAcquisition()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsDatesAcquired(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsDatesAcquired(testAccountRef, id = "1").apply(fakeRequest)

        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the dates the property was acquired" in new Setup {
        val update = new PropertyDetailsDateOfAcquisition()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsDatesAcquired(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsDatesAcquired(testAccountRef, id = "1").apply(fakeRequest)

        status(result) must be(BAD_REQUEST)
      }
    }


    "saveDraftPropertyDetailsProfessionallyValued" must {
      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val update = new PropertyDetailsProfessionallyValued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsProfessionallyValued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val update = new PropertyDetailsProfessionallyValued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsProfessionallyValued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

  }
}
