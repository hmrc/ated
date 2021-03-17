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

package connectors

import builders.TestAudit
import metrics.ServiceMetrics
import models._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import utils.SessionUtils

import scala.concurrent.{ExecutionContext, Future}

class EtmpDetailsConnectorSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfter {

  val mockWSHttp: HttpClient = mock[HttpClient]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  trait Setup {
    class TestEtmpDetailsConnector extends EtmpDetailsConnector {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      val serviceUrl = "etmp-hod"
      val http: HttpClient = mockWSHttp
      val urlHeaderEnvironment: String = ""
      val urlHeaderAuthorization: String = ""
      val audit: Audit = new TestAudit(mockAuditConnector)
      val appName: String = "Test"
      val metrics: ServiceMetrics = app.injector.instanceOf[ServiceMetrics]
      override val atedBaseURI: String = ""
      override val submitClientRelationship: String = ""
      override val getAgentClientRelationship: String = ""
      override val retrieveSubscriptionData: String = ""
      override val saveSubscriptionData: String = ""
      override val saveRegistrationDetails: String = ""
    }

    val connector = new TestEtmpDetailsConnector()
  }

  before {
    reset(mockWSHttp)
  }


  "EtmpDetailsConnector" must {

    "getDetails" must {
      "do a GET call and fetch data from ETMP for ARN that fails" in new Setup {
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))
        val result = connector.getDetails(identifier = "AARN1234567", identifierType = "arn")
        await(result).status must be(BAD_REQUEST)
      }
      "do a GET call and fetch data from ETMP for ARN" in new Setup {
        val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result = connector.getDetails(identifier = "AARN1234567", identifierType = "arn")
        await(result).json must be(successResponseJson)
        await(result).status must be(OK)
        verify(mockWSHttp, times(1)).GET[HttpResponse](any())(any(), any(), any())
      }
      "do a GET call and fetch data from ETMP for utr" in new Setup {
        val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result = connector.getDetails(identifier = "1111111111", identifierType = "utr")
        await(result).json must be(successResponseJson)
        await(result).status must be(OK)
        verify(mockWSHttp, times(1)).GET[HttpResponse](any())(any(), any(), any())
      }
      "do a GET call and fetch data from ETMP for safeid" in new Setup {
        val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "XP1200000100003", "agentReferenceNumber": "AARN1234567"}""")
        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result = connector.getDetails(identifier = "XP1200000100003", identifierType = "safeid")
        await(result).json must be(successResponseJson)
        await(result).status must be(OK)
        verify(mockWSHttp, times(1)).GET[HttpResponse](any())(any(), any(), any())
      }
      "throw runtime exception for other identifier type" in new Setup {
        val thrown = the[RuntimeException] thrownBy connector.getDetails(identifier = "AARN1234567", identifierType = "xyz")
        thrown.getMessage must include("unexpected identifier type supplied")
        verify(mockWSHttp, times(0)).GET[HttpResponse](any())(any(), any(), any())
      }
    }


    "get subscription data" must {
      "Correctly return no data if there is none" in new Setup {
        val notFoundResponse = Json.parse( """{}""")

        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, notFoundResponse, Map.empty[String, Seq[String]])))

        val result = connector.getSubscriptionData("ATED-123")
        val response = await(result)
        response.status must be(NOT_FOUND)
        response.json must be(notFoundResponse)
      }

      "Correctly return data if we have some" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        when(mockWSHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))

        val result = connector.getSubscriptionData("ATED-123")
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

      "Correctly submit data if with a valid response" in new Setup {
        val successResponse: JsValue = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        when(mockWSHttp.PUT[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))

        val result = connector.updateSubscriptionData("ATED-123", updatedData)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "Correctly submit data if with a valid response and no postcode" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        when(mockWSHttp.PUT[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))

        val result = connector.updateSubscriptionData("ATED-123", updatedDataNoPostcode)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "submit data  with an invalid response" in new Setup {
        val notFoundResponse = Json.parse( """{}""")

        when(mockWSHttp.PUT[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, notFoundResponse, Map.empty[String, Seq[String]])))

        val result = connector.updateSubscriptionData("ATED-123", updatedData)
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

      "Correctly submit data if with a valid response" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        when(mockWSHttp.PUT[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))

        val result = connector.updateRegistrationDetails("ATED-123", "SAFE-123", updatedData)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "Correctly submit data if with a valid response and postcode supplied" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        when(mockWSHttp.PUT[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))

        val result = connector.updateRegistrationDetails("ATED-123", "SAFE-123", updatedDataWithPostcode)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "submit data  with an invalid response" in new Setup {
        val notFoundResponse = Json.parse( """{}""")

        when(mockWSHttp.PUT[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, notFoundResponse, Map.empty[String, Seq[String]])))

        val result = connector.updateRegistrationDetails("ATED-123", "SAFE-123", updatedData)
        val response = await(result)
        response.status must be(NOT_FOUND)
      }
    }
  }

}
