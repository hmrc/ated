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

import models._
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.SubscriptionDataService

import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

class SubscriptionDataControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockSubscriptionDataService = mock[SubscriptionDataService]
  val callingUtr = "ATED-123"
  val agentCode = "AGENT-CODE"
  val callingSafeId = "EX0012345678909"
  val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")
  val failureResponseJson = Json.parse( """{"reason":"Agent not found!"}""")
  val errorResponseJson = Json.parse( """{"reason":"Some Error."}""")

  object TestSubscriptionDataController extends SubscriptionDataController {
    val subscriptionDataService: SubscriptionDataService = mockSubscriptionDataService
  }

  object TestAgentRetrieveClientSubscriptionDataController extends SubscriptionDataController {
    val subscriptionDataService: SubscriptionDataService = mockSubscriptionDataService
  }

  override def beforeEach = {
    reset(mockSubscriptionDataService)
  }

  "SubscriptionDataController" must {
    "use correct service" in {
      SubscriptionDataController.subscriptionDataService must be(SubscriptionDataService)
      AgentRetrieveClientSubscriptionDataController.subscriptionDataService must be(SubscriptionDataService)
    }

    "use correct service for agents" in {
      AgentRetrieveClientSubscriptionDataController.subscriptionDataService must be(SubscriptionDataService)
    }

    "get subscription data" must {
      "respond with OK, for successful GET" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = TestSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(failureResponseJson))))
        val result = TestSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(errorResponseJson))))
        val result = TestSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, Some(errorResponseJson))))
        val result = TestSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, Some(errorResponseJson))))
        val result = TestSubscriptionDataController.retrieveSubscriptionData(callingUtr).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }


    "get subscription data requested by agent" must {
      "respond with OK, for successful GET" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = TestSubscriptionDataController.retrieveSubscriptionDataByAgent(callingUtr, agentCode).apply(FakeRequest())
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(failureResponseJson))))
        val result = TestAgentRetrieveClientSubscriptionDataController.retrieveSubscriptionDataByAgent(callingUtr,agentCode).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(errorResponseJson))))
        val result = TestAgentRetrieveClientSubscriptionDataController.retrieveSubscriptionDataByAgent(callingUtr,agentCode).apply(FakeRequest())
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, Some(errorResponseJson))))
        val result = TestAgentRetrieveClientSubscriptionDataController.retrieveSubscriptionDataByAgent(callingUtr,agentCode).apply(FakeRequest())
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in {
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, Some(errorResponseJson))))
        val result = TestAgentRetrieveClientSubscriptionDataController.retrieveSubscriptionDataByAgent(callingUtr,agentCode).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "update subscription data" must {
      val addressDetails = AddressDetails("Correspondence", "line1", "line2", None, None, Some("postCode"), "GB")
      val updatedData = UpdateSubscriptionDataRequest(true, ChangeIndicators(), List(Address(addressDetails = addressDetails)))
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updatedData))
      implicit val hc: HeaderCarrier = HeaderCarrier()

      "respond with OK, for successful GET" in {
        when(mockSubscriptionDataService.updateSubscriptionData(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = TestSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in {
        when(mockSubscriptionDataService.updateSubscriptionData(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(failureResponseJson))))
        val result = TestSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in {
        when(mockSubscriptionDataService.updateSubscriptionData(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(errorResponseJson))))
        val result = TestSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in {
        when(mockSubscriptionDataService.updateSubscriptionData(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, Some(errorResponseJson))))
        val result = TestSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in {
        when(mockSubscriptionDataService.updateSubscriptionData(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, Some(errorResponseJson))))
        val result = TestSubscriptionDataController.updateSubscriptionData(callingUtr).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "update registration details" must {
      val registeredDetails = RegisteredAddressDetails(addressLine1 = "", addressLine2 = "", countryCode = "GB")
      val updatedData = new UpdateRegistrationDetailsRequest(None, false, None, Some(Organisation("testName")), registeredDetails, ContactDetails(), false, false)
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updatedData))

      "respond with OK, for successful GET" in {
        when(mockSubscriptionDataService.updateRegistrationDetails(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = TestSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in {
        when(mockSubscriptionDataService.updateRegistrationDetails(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(failureResponseJson))))
        val result = TestSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in {
        when(mockSubscriptionDataService.updateRegistrationDetails(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(errorResponseJson))))
        val result = TestSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in {
        when(mockSubscriptionDataService.updateRegistrationDetails(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, Some(errorResponseJson))))
        val result = TestSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in {
        when(mockSubscriptionDataService.updateRegistrationDetails(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, Some(errorResponseJson))))
        val result = TestSubscriptionDataController.updateRegistrationDetails(callingUtr, callingSafeId).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }

}
