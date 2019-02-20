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

package connectors

import java.util.UUID

import builders.TestAudit
import metrics.Metrics
import models._
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.audit.model.Audit
import utils.SessionUtils

import scala.concurrent.Future

class EtmpDetailsConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfter {

  trait MockedVerbs extends CoreGet with CorePost with CorePut
  val mockWSHttp: CoreGet with CorePost with CorePut = mock[MockedVerbs]

  object TestEtmpDetailsConnector extends EtmpDetailsConnector {
    val serviceUrl = "etmp-hod"
    val http: CoreGet with CorePost with CorePut = mockWSHttp
    val urlHeaderEnvironment: String = config("etmp-hod").getString("environment").getOrElse("")
    val urlHeaderAuthorization: String = s"Bearer ${config("etmp-hod").getString("authorization-token").getOrElse("")}"
    val audit: Audit = new TestAudit
    val appName: String = "Test"
    val metrics = Metrics
  }

  before {
    reset(mockWSHttp)
  }


  "EtmpDetailsConnector" must {

    "have a service url" in {
      EtmpDetailsConnector.serviceUrl == "etmp-hod"
    }

    "use correct metrics" in {
      EtmpDetailsConnector.metrics must be(Metrics)
    }

    "getDetails" must {
      "do a GET call and fetch data from ETMP for ARN that fails" in {
        val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, None)))
        val result = TestEtmpDetailsConnector.getDetails(identifier = "AARN1234567", identifierType = "arn")
        await(result).status must be(BAD_REQUEST)
      }
      "do a GET call and fetch data from ETMP for ARN" in {
        val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = TestEtmpDetailsConnector.getDetails(identifier = "AARN1234567", identifierType = "arn")
        await(result).json must be(successResponseJson)
        await(result).status must be(OK)
        verify(mockWSHttp, times(1)).GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())
      }
      "do a GET call and fetch data from ETMP for utr" in {
        val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = TestEtmpDetailsConnector.getDetails(identifier = "1111111111", identifierType = "utr")
        await(result).json must be(successResponseJson)
        await(result).status must be(OK)
        verify(mockWSHttp, times(1)).GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())
      }
      "do a GET call and fetch data from ETMP for safeid" in {
        val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "XP1200000100003", "agentReferenceNumber": "AARN1234567"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = TestEtmpDetailsConnector.getDetails(identifier = "XP1200000100003", identifierType = "safeid")
        await(result).json must be(successResponseJson)
        await(result).status must be(OK)
        verify(mockWSHttp, times(1)).GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())
      }
      "throw runtime exception for other identifier type" in {
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        val thrown = the[RuntimeException] thrownBy TestEtmpDetailsConnector.getDetails(identifier = "AARN1234567", identifierType = "xyz")
        thrown.getMessage must include("unexpected identifier type supplied")
        verify(mockWSHttp, times(0)).GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())
      }
    }


    "get subscription data" must {
      "Correctly return no data if there is none" in {
        val notFoundResponse = Json.parse( """{}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(notFoundResponse))))

        val result = TestEtmpDetailsConnector.getSubscriptionData("ATED-123")
        val response = await(result)
        response.status must be(NOT_FOUND)
        response.json must be(notFoundResponse)
      }

      "Correctly return data if we have some" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))

        val result = TestEtmpDetailsConnector.getSubscriptionData("ATED-123")
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }
    }

    "update subscription data" must {
      val addressDetails = AddressDetails("Correspondence", "line1", "line2", None, None, Some("postCode"), "GB")
      val addressDetailsNoPostcode = AddressDetails("Correspondence", "line1", "line2", None, None, None, "GB")
      val updatedData = new UpdateEtmpSubscriptionDataRequest(SessionUtils.getUniqueAckNo, true, ChangeIndicators(), None,
        List(Address(addressDetails = addressDetails)))
      val updatedDataNoPostcode = new UpdateEtmpSubscriptionDataRequest(SessionUtils.getUniqueAckNo, true, ChangeIndicators(), None,
        List(Address(addressDetails = addressDetailsNoPostcode)))

      "Correctly submit data if with a valid response" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))

        val result = TestEtmpDetailsConnector.updateSubscriptionData("ATED-123", updatedData)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "Correctly submit data if with a valid response and no postcode" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))

        val result = TestEtmpDetailsConnector.updateSubscriptionData("ATED-123", updatedDataNoPostcode)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "submit data  with an invalid response" in {
        val notFoundResponse = Json.parse( """{}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(notFoundResponse))))

        val result = TestEtmpDetailsConnector.updateSubscriptionData("ATED-123", updatedData)
        val response = await(result)
        response.status must be(NOT_FOUND)
      }
    }

    "update registration details" must {
      val registeredDetails = RegisteredAddressDetails(addressLine1 = "", addressLine2 = "", countryCode = "GB")
      val registeredDetailsWithPostcode = RegisteredAddressDetails(addressLine1 = "", addressLine2 = "", countryCode = "GB", postalCode = Some("NE1 1EN"))
      val updatedData = new UpdateRegistrationDetailsRequest(None, false, None,
        Some(Organisation("testName")), registeredDetails, ContactDetails(), false, false)
      val updatedDataWithPostcode = new UpdateRegistrationDetailsRequest(None, false, None,
        Some(Organisation("testName")), registeredDetailsWithPostcode, ContactDetails(), false, false)

      "Correctly submit data if with a valid response" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))

        val result = TestEtmpDetailsConnector.updateRegistrationDetails("ATED-123", "SAFE-123", updatedData)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "Correctly submit data if with a valid response and postcode supplied" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))

        val result = TestEtmpDetailsConnector.updateRegistrationDetails("ATED-123", "SAFE-123", updatedDataWithPostcode)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "submit data  with an invalid response" in {
        val notFoundResponse = Json.parse( """{}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(notFoundResponse))))

        val result = TestEtmpDetailsConnector.updateRegistrationDetails("ATED-123", "SAFE-123", updatedData)
        val response = await(result)
        response.status must be(NOT_FOUND)
      }
    }
  }

}
