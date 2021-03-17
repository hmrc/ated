/*
 * Copyright 2021 HM Revenue & Customs
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

import builders.{ChangeLiabilityReturnBuilder, PropertyDetailsBuilder}
import models._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.PropertyDetailsService
import uk.gov.hmrc.crypto.{ApplicationCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class PropertyDetailsControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar {

  val mockPropertyDetailsService: PropertyDetailsService = mock[PropertyDetailsService]

  implicit lazy val compositeSymmetricCrypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]
  implicit lazy val crypto: CryptoWithKeysFromConfig = compositeSymmetricCrypto.JsonCrypto
  implicit lazy val format: OFormat[PropertyDetails] = PropertyDetails.formats

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

    class TestPropertyDetailsController extends BackendController(cc) with PropertyDetailsController {
      val propertyDetailsService = mockPropertyDetailsService
      override implicit val crypto: ApplicationCrypto = compositeSymmetricCrypto
      implicit val ec: ExecutionContext = cc.executionContext
    }

    val controller = new TestPropertyDetailsController()
  }

  "PropertyDetailsController" must {

    "retrieveDraftPropertyDetails" must {

      "respond with OK and the Property Details if we have one" in new Setup {
        val testAccountRef = "ATED1223123"
        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))

        when(mockPropertyDetailsService.retrieveDraftPropertyDetail(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"))(ArgumentMatchers.any())).thenReturn(Future(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest()
        val result = controller.retrieveDraftPropertyDetails(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result).as[JsValue] must be(Json.toJson(testPropertyDetails))
      }

      "respond with NotFound and No Property Details if we have None" in new Setup {
        val testAccountRef = "ATED1223123"

        when(mockPropertyDetailsService.retrieveDraftPropertyDetail(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("2"))(ArgumentMatchers.any())).thenReturn(Future(None))

        val fakeRequest = FakeRequest()
        val result = controller.retrieveDraftPropertyDetails(testAccountRef, "2").apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("null"))
      }
    }

    "createDraftPropertyDetails" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsAddr = testPropertyDetails.addressProperty
        when(mockPropertyDetailsService.createDraftPropertyDetails(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq(2015), ArgumentMatchers.eq(testPropertyDetailsAddr))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsAddr))
        val result = controller.createDraftPropertyDetails(testAccountRef, 2015).apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsAddr = testPropertyDetails.addressProperty
        when(mockPropertyDetailsService.createDraftPropertyDetails(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq(2015), ArgumentMatchers.eq(testPropertyDetailsAddr))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsAddr))
        val result = controller.createDraftPropertyDetails(testAccountRef, 2015).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }


    "saveDraftPropertyDetailsAddress" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsAddr = testPropertyDetails.addressProperty
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAddress(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsAddr))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsAddr))
        val result = controller.saveDraftPropertyDetailsAddress(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsAddr = testPropertyDetails.addressProperty
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAddress(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsAddr))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsAddr))
        val result = controller.saveDraftPropertyDetailsAddress(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsTitle" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsTitle = testPropertyDetails.title.get
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsTitle(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsTitle))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsTitle))
        val result = controller.saveDraftPropertyDetailsTitle(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        val testPropertyDetailsTitle = testPropertyDetails.title.get
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsTitle(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsTitle))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsTitle))
        val result = controller.saveDraftPropertyDetailsTitle(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftTaxAvoidance" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsTaxAvoidance()
        when(mockPropertyDetailsService.cacheDraftTaxAvoidance(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftTaxAvoidance(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetailsPeriod = PropertyDetailsTaxAvoidance()
        when(mockPropertyDetailsService.cacheDraftTaxAvoidance(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftTaxAvoidance(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftSupportingInfo" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsSupportingInfo("")
        when(mockPropertyDetailsService.cacheDraftSupportingInfo(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftSupportingInfo(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetailsPeriod = PropertyDetailsSupportingInfo("")
        when(mockPropertyDetailsService.cacheDraftSupportingInfo(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftSupportingInfo(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "updateHasBankDetails" must {
      val atedRefNo = "ated-123"
      val formBundle1 = "123456789012"
      "for successful save, return ChangeLiabilityReturn model with OK as response status" in new Setup {
        val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(true))
        when(mockPropertyDetailsService.cacheDraftHasBankDetails(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1),
          ArgumentMatchers.eq(true))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(changeLiabilityReturn)))
        val result = controller.updateDraftHasBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(changeLiabilityReturn))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in new Setup {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(false))
        when(mockPropertyDetailsService.cacheDraftHasBankDetails(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.eq(false))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        val result = controller.updateDraftHasBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse( """{}"""))
      }
    }


    "updateBankDetails" must {
      val atedRefNo = "ated-123"
      val formBundle1 = "123456789012"
      "for successful save, return ChangeLiabilityReturn model with OK as response status" in new Setup {
        val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        val bankdetails = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails.bankDetails.get
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(bankdetails))
        when(mockPropertyDetailsService.cacheDraftBankDetails(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1),
          ArgumentMatchers.eq(bankdetails))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(changeLiabilityReturn)))
        val result = controller.updateDraftBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(changeLiabilityReturn))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in new Setup {
        val bankdetails = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails.bankDetails.get
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(bankdetails))
        when(mockPropertyDetailsService.cacheDraftBankDetails(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1), ArgumentMatchers.eq(bankdetails))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        val result = controller.updateDraftBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse( """{}"""))
      }
    }

    "calculateDraftPropertyDetails" must {

      "respond with OK and the Property Details if we have one" in new Setup {
        val testAccountRef = "ATED1223123"
        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))

        when(mockPropertyDetailsService.calculateDraftPropertyDetails(ArgumentMatchers.eq(testAccountRef), ArgumentMatchers.eq("1"))(any(), any())).thenReturn(Future(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest()
        val result = controller.calculateDraftPropertyDetails(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result).as[JsValue] must be(Json.toJson(testPropertyDetails))
      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        when(mockPropertyDetailsService.calculateDraftPropertyDetails(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"))(any(), any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest()
        val result = controller.calculateDraftPropertyDetails(testAccountRef, "1").apply(fakeRequest)

        status(result) must be(BAD_REQUEST)

      }
    }

    "submitDraftPropertyDetails" must {
      val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z", "atedRefNumber": "ABCDEabcde12345", "formBundleNumber": "123456789012345"}""")
      val failureResponse = Json.parse( """{"reason": "Something went wrong. try again later"}""")

      "respond with OK and a list of cached Property Details if this successfully submits" in new Setup {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(ArgumentMatchers.eq(testAccountRef), ArgumentMatchers.eq("1"))(any(), any())).thenReturn(Future(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
        val result = controller.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(OK)
      }

      "respond with NOT_FOUND and a list of cached Property Details if no data is found" in new Setup {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(ArgumentMatchers.eq(testAccountRef), ArgumentMatchers.eq("1"))(any(), any())).thenReturn(Future(HttpResponse(NOT_FOUND, successResponse, Map.empty[String, Seq[String]])))
        val result = controller.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(NOT_FOUND)
      }

      "respond with BAD_REQUEST and a list of cached Property Details if we have this status" in new Setup {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(ArgumentMatchers.eq(testAccountRef), ArgumentMatchers.eq("1"))(any(), any())).thenReturn(Future(HttpResponse(BAD_REQUEST, failureResponse, Map.empty[String, Seq[String]])))
        val result = controller.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(BAD_REQUEST)
      }

      "respond with SERVICE_UNAVAILABLE and a list of cached Property Details if we have this status" in new Setup {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"))(any(), any()))
          .thenReturn(Future(HttpResponse(SERVICE_UNAVAILABLE, failureResponse, Map.empty[String, Seq[String]])))
        val result = controller.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(SERVICE_UNAVAILABLE)
      }

      "respond with 999 and a list of cached Property Details if we have this status" in new Setup {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.submitDraftPropertyDetail(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"))(any(), any()))
          .thenReturn(Future(HttpResponse(999, failureResponse, Map.empty[String, Seq[String]])))
        val result = controller.submitDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "deleteDraftPropertyDetails" must {
      "respond with OK when list is empty" in new Setup {
        val testAccountRef = "ATED1223123"
        when(mockPropertyDetailsService.deleteChargeableDraft(ArgumentMatchers.eq(testAccountRef), ArgumentMatchers.eq("1"))(ArgumentMatchers.any()))
          .thenReturn(Future(Seq[PropertyDetails]()))
        val result = controller.deleteDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(OK)
      }

      "respond with NTERNAL_SERVER_ERROR when list is not empty" in new Setup {
        val testAccountRef = "ATED1223123"
        val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        when(mockPropertyDetailsService.deleteChargeableDraft(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"))(ArgumentMatchers.any()))
          .thenReturn(Future(Seq[PropertyDetails](testPropertyDetails)))
        val result = controller.deleteDraftPropertyDetails(testAccountRef, "1").apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }
}
