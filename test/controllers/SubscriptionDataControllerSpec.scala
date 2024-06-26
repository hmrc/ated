/*
 * Copyright 2023 HM Revenue & Customs
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

import models._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.SubscriptionDataService
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class SubscriptionDataControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockSubscriptionDataService: SubscriptionDataService = mock[SubscriptionDataService]
  val callingUtr = "ATED-123"
  val agentCode = "AGENT-CODE"
  val callingSafeId = "EX0012345678909"
  val successResponseJson: JsValue = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")
  val failureResponseJson: JsValue = Json.parse( """{"reason":"Agent not found!"}""")
  val errorResponseJson: JsValue = Json.parse( """{"reason":"Some Error."}""")

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

    class TestSubscriptionDataController extends BackendController(cc) with SubscriptionDataController {
      implicit val ec: ExecutionContext = cc.executionContext
      val subscriptionDataService: SubscriptionDataService = mockSubscriptionDataService
    }

    class TestAgentRetrieveClientSubscriptionDataController extends BackendController(cc) with SubscriptionDataController {
      implicit val ec: ExecutionContext = cc.executionContext
      val subscriptionDataService: SubscriptionDataService = mockSubscriptionDataService
    }

    val testSubscriptionDataController: TestSubscriptionDataController = new TestSubscriptionDataController
    val testAgentRetrieveClientSubController: TestAgentRetrieveClientSubscriptionDataController = new TestAgentRetrieveClientSubscriptionDataController
  }

  override def beforeEach(): Unit = {
    reset(mockSubscriptionDataService)
  }

  "SubscriptionDataController" must {

    "get subscription data" must {
      "respond with OK, for successful GET" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, failureResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }


    "get subscription data requested by agent" must {
      "respond with OK, for successful GET" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.retrieveSubscriptionDataByAgent(callingUtr, agentCode).apply(FakeRequest())
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, failureResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testAgentRetrieveClientSubController.retrieveSubscriptionDataByAgent(callingUtr,agentCode).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testAgentRetrieveClientSubController.retrieveSubscriptionDataByAgent(callingUtr,agentCode).apply(FakeRequest())
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testAgentRetrieveClientSubController.retrieveSubscriptionDataByAgent(callingUtr,agentCode).apply(FakeRequest())
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in new Setup {
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testAgentRetrieveClientSubController.retrieveSubscriptionDataByAgent(callingUtr,agentCode).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "update subscription data" must {
      val addressDetails = AddressDetails("Correspondence", "line1", "line2", None, None, Some("postCode"), "GB")
      val updatedData = UpdateSubscriptionDataRequest(emailConsent = true, ChangeIndicators(), List(Address(addressDetails = addressDetails)))
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updatedData))

      "respond with OK, for successful GET" in new Setup {
        when(mockSubscriptionDataService
          .updateSubscriptionData(any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in new Setup {
        when(mockSubscriptionDataService
          .updateSubscriptionData(any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, failureResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in new Setup {
        when(mockSubscriptionDataService
          .updateSubscriptionData(any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in new Setup {
        when(mockSubscriptionDataService
          .updateSubscriptionData(any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in new Setup {
        when(mockSubscriptionDataService
          .updateSubscriptionData(any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "update registration details" must {
      val registeredDetails = RegisteredAddressDetails(addressLine1 = "", addressLine2 = "", countryCode = "GB")
      val updatedData = new UpdateRegistrationDetailsRequest(None, isAnIndividual = false, None, Some(Organisation("testName")), registeredDetails, ContactDetails(), isAnAgent = false, isAGroup = false)
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updatedData))

      "respond with OK, for successful GET" in new Setup {
        when(mockSubscriptionDataService
          .updateRegistrationDetails(any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in new Setup {
        when(mockSubscriptionDataService
          .updateRegistrationDetails(any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, failureResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in new Setup {
        when(mockSubscriptionDataService
          .updateRegistrationDetails(any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in new Setup {
        when(mockSubscriptionDataService
          .updateRegistrationDetails(any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in new Setup {
        when(mockSubscriptionDataService
          .updateRegistrationDetails(any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = testSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }

}
