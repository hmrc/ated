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

import builders.{ChangeLiabilityReturnBuilder, PropertyDetailsBuilder, TestAudit}
import models._
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.PropertyDetailsService
import uk.gov.hmrc.play.audit.model.Audit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HttpResponse

class PropertyDetailsControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar {

  val mockPropertyDetailsService: PropertyDetailsService = mock[PropertyDetailsService]

  object TestPropertyDetailsController extends PropertyDetailsController {
    val propertyDetailsService = mockPropertyDetailsService
  }

  "PropertyDetailsController" must {

    "use the correct Service" in {
      PropertyDetailsController.propertyDetailsService must be(PropertyDetailsService)
    }

    "retrieveDraftPropertyDetails" must {

      "respond with OK and the Property Details if we have one" in {
        val testAccountRef = "ATED1223123"
        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))

        when(mockPropertyDetailsService.retrieveDraftPropertyDetail(Matchers.eq(testAccountRef), Matchers.eq("1"))(Matchers.any())).thenReturn(Future(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest()
        val result = TestPropertyDetailsController.retrieveDraftPropertyDetails(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result).as[JsValue] must be(Json.toJson(testPropertyDetails))
      }

      "respond with NotFound and No Property Details if we have None" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetail(Matchers.eq(testAccountRef), Matchers.eq("2"))(Matchers.any())).thenReturn(Future(None))

        val fakeRequest = FakeRequest()
        val result = TestPropertyDetailsController.retrieveDraftPropertyDetails(testAccountRef, "2").apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("null"))
      }
    }

    "createDraftPropertyDetails" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsAddr = testPropertyDetails.addressProperty
        when(mockPropertyDetailsService.createDraftPropertyDetails(Matchers.eq(testAccountRef), Matchers.eq(2015),
          Matchers.eq(testPropertyDetailsAddr))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsAddr))
        val result = TestPropertyDetailsController.createDraftPropertyDetails(testAccountRef, 2015).apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsAddr = testPropertyDetails.addressProperty
        when(mockPropertyDetailsService.createDraftPropertyDetails(Matchers.eq(testAccountRef), Matchers.eq(2015),
          Matchers.eq(testPropertyDetailsAddr))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsAddr))
        val result = TestPropertyDetailsController.createDraftPropertyDetails(testAccountRef, 2015).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }


    "saveDraftPropertyDetailsAddress" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsAddr = testPropertyDetails.addressProperty
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAddress(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsAddr))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsAddr))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsAddress(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsAddr = testPropertyDetails.addressProperty
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAddress(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsAddr))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsAddr))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsAddress(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsTitle" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsTitle = testPropertyDetails.title.get
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsTitle(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsTitle))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsTitle))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsTitle(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsTitle = testPropertyDetails.title.get
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsTitle(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsTitle))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsTitle))
        val result = TestPropertyDetailsController.saveDraftPropertyDetailsTitle(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftTaxAvoidance" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsTaxAvoidance()
        when(mockPropertyDetailsService.cacheDraftTaxAvoidance(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftTaxAvoidance(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsTaxAvoidance()
        when(mockPropertyDetailsService.cacheDraftTaxAvoidance(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftTaxAvoidance(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftSupportingInfo" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsSupportingInfo("")
        when(mockPropertyDetailsService.cacheDraftSupportingInfo(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftSupportingInfo(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsSupportingInfo("")
        when(mockPropertyDetailsService.cacheDraftSupportingInfo(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftSupportingInfo(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "updateHasBankDetails" must {
      val atedRefNo = "ated-123"
      val formBundle1 = "123456789012"
      val formBundle2 = "100000000000"
      "for successful save, return ChangeLiabilityReturn model with OK as response status" in {
        val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(true))
        when(mockPropertyDetailsService.cacheDraftHasBankDetails(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(true))(Matchers.any())).thenReturn(Future.successful(Some(changeLiabilityReturn)))
        val result = TestPropertyDetailsController.updateDraftHasBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(changeLiabilityReturn))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in {
        val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(false))
        when(mockPropertyDetailsService.cacheDraftHasBankDetails(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(false))(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestPropertyDetailsController.updateDraftHasBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse( """{}"""))
      }
    }


    "updateBankDetails" must {
      val atedRefNo = "ated-123"
      val formBundle1 = "123456789012"
      val formBundle2 = "100000000000"
      "for successful save, return ChangeLiabilityReturn model with OK as response status" in {
        val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        val bankdetails = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails.bankDetails.get
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(bankdetails))
        when(mockPropertyDetailsService.cacheDraftBankDetails(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(bankdetails))(Matchers.any())).thenReturn(Future.successful(Some(changeLiabilityReturn)))
        val result = TestPropertyDetailsController.updateDraftBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(changeLiabilityReturn))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in {
        val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        val bankdetails = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails.bankDetails.get
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(bankdetails))
        when(mockPropertyDetailsService.cacheDraftBankDetails(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(bankdetails))(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestPropertyDetailsController.updateDraftBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse( """{}"""))
      }
    }

    "calculateDraftPropertyDetails" must {

      "respond with OK and the Property Details if we have one" in {
        val testAccountRef = "ATED1223123"
        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))

        when(mockPropertyDetailsService.calculateDraftPropertyDetails(Matchers.eq(testAccountRef), Matchers.eq("1"))(Matchers.any())).thenReturn(Future(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest()
        val result = TestPropertyDetailsController.calculateDraftPropertyDetails(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result).as[JsValue] must be(Json.toJson(testPropertyDetails))
      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsTitle = testPropertyDetails.title.get
        when(mockPropertyDetailsService.calculateDraftPropertyDetails(Matchers.eq(testAccountRef),
          Matchers.eq("1"))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest()
        val result = TestPropertyDetailsController.calculateDraftPropertyDetails(testAccountRef, "1").apply(fakeRequest)

        status(result) must be(BAD_REQUEST)

      }
    }

    "submitDraftPropertyDetails" must {
      val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z", "atedRefNumber": "ABCDEabcde12345", "formBundleNumber": "123456789012345"}""")
      val failureResponse = Json.parse( """{"reason": "Something went wrong. try again later"}""")

      "respond with OK and a list of cached Property Details if this successfully submits" in {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(Matchers.eq(testAccountRef), Matchers.eq("1"))(org.mockito.Matchers.any())).thenReturn(Future(HttpResponse(OK, Some(successResponse))))
        val result = TestPropertyDetailsController.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(OK)
      }

      "respond with NOT_FOUND and a list of cached Property Details if no data is found" in {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(Matchers.eq(testAccountRef), Matchers.eq("1"))(org.mockito.Matchers.any())).thenReturn(Future(HttpResponse(NOT_FOUND, Some(successResponse))))
        val result = TestPropertyDetailsController.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(NOT_FOUND)
      }

      "respond with BAD_REQUEST and a list of cached Property Details if we have this status" in {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(Matchers.eq(testAccountRef), Matchers.eq("1"))(org.mockito.Matchers.any())).thenReturn(Future(HttpResponse(BAD_REQUEST, Some(failureResponse))))
        val result = TestPropertyDetailsController.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(BAD_REQUEST)
      }

      "respond with SERVICE_UNAVAILABLE and a list of cached Property Details if we have this status" in {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(Matchers.eq(testAccountRef), Matchers.eq("1"))(org.mockito.Matchers.any())).thenReturn(Future(HttpResponse(SERVICE_UNAVAILABLE, Some(failureResponse))))
        val result = TestPropertyDetailsController.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(SERVICE_UNAVAILABLE)
      }

      "respond with 999 and a list of cached Property Details if we have this status" in {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(Matchers.eq(testAccountRef), Matchers.eq("1"))(org.mockito.Matchers.any())).thenReturn(Future(HttpResponse(999, Some(failureResponse))))
        val result = TestPropertyDetailsController.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "deleteDraftPropertyDetails" must {
      "respond with OK when list is empty" in {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.deleteChargeableDraft(Matchers.eq(testAccountRef), Matchers.eq("1"))(org.mockito.Matchers.any())).thenReturn(Future(Seq[PropertyDetails]()))
        val result = TestPropertyDetailsController.deleteDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(OK)
      }

      "respond with NTERNAL_SERVER_ERROR when list is not empty" in {
        val testAccountRef = "ATED1223123"
        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        when(mockPropertyDetailsService.deleteChargeableDraft(Matchers.eq(testAccountRef), Matchers.eq("1"))(org.mockito.Matchers.any())).thenReturn(Future(Seq[PropertyDetails](testPropertyDetails)))
        val result = TestPropertyDetailsController.deleteDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }
}
