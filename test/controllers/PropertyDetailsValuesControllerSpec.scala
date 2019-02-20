/*
 * Copyright 2019 HM Revenue & Customs
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

import builders.{PropertyDetailsBuilder, TestAudit}
import models._
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.PropertyDetailsValuesService
import uk.gov.hmrc.play.audit.model.Audit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PropertyDetailsValuesControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar {

  val mockPropertyDetailsService: PropertyDetailsValuesService = mock[PropertyDetailsValuesService]

  object TestPropertyDetailsController extends PropertyDetailsValuesController {
    val propertyDetailsService = mockPropertyDetailsService
  }

  "PropertyDetailsValuesController" must {

    "use the correct Service" in {
      PropertyDetailsValuesController.propertyDetailsService must be(PropertyDetailsValuesService)
    }


    "saveDraftHasValueChanged" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"
        val updated = true
        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        when(mockPropertyDetailsService.cacheDraftHasValueChanged(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftHasValueChanged(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"
        val updated = true
        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsSupportingInfo("")
        when(mockPropertyDetailsService.cacheDraftHasValueChanged(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftHasValueChanged(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }
    "saveDraftPropertyDetailsAcquisition" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = true
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsAcquisition(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = true
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsAcquisition(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsRevalued" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsRevalued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsRevalued(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsRevalued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsRevalued(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsOwnedBefore" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = PropertyDetailsOwnedBefore()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsOwnedBefore(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsOwnedBefore()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsOwnedBefore(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsNewBuild" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = PropertyDetailsNewBuild()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuild(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsNewBuild(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsNewBuild()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuild(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsNewBuild(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsProfessionallyValued" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsProfessionallyValued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsProfessionallyValued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val updated = new PropertyDetailsProfessionallyValued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(updated))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsProfessionallyValued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

  }
}
