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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.PropertyDetailsValuesService
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.Future

class PropertyDetailsValuesControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar {

  val mockPropertyDetailsService: PropertyDetailsValuesService = mock[PropertyDetailsValuesService]

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

    class TestPropertyDetailsController extends BackendController(cc) with PropertyDetailsValuesController {
      val propertyDetailsService = mockPropertyDetailsService
    }

    val controller = new TestPropertyDetailsController()
  }

  "PropertyDetailsValuesController" must {

    "saveDraftHasValueChanged" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"
        val updated = true
        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        when(mockPropertyDetailsService.cacheDraftHasValueChanged(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftHasValueChanged(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"
        val updated = true
        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsSupportingInfo("")
        when(mockPropertyDetailsService.cacheDraftHasValueChanged(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftHasValueChanged(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }
    "saveDraftPropertyDetailsAcquisition" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = true
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsAcquisition(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = true
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsAcquisition(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsRevalued" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsRevalued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsRevalued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsRevalued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsRevalued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsOwnedBefore" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = PropertyDetailsOwnedBefore()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsOwnedBefore(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsOwnedBefore()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsOwnedBefore(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsNewBuild" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = PropertyDetailsNewBuild()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuild(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsNewBuild(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsNewBuild()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuild(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsNewBuild(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsProfessionallyValued" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsProfessionallyValued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsProfessionallyValued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsProfessionallyValued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsProfessionallyValued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

  }
}
