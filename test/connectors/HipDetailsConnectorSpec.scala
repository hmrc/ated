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

package connectors

import builders.TestAudit
import metrics.ServiceMetrics
import models._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import utils.SessionUtils

import scala.concurrent.{ExecutionContext, Future}

class HipDetailsConnectorSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfter {

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup extends ConnectorTest {
    class TestHipDetailsConnector extends HipDetailsConnector {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      val serviceUrl = "http://localhost:9020//etmp/RESTAdaptor/ated"
      val http: HttpClientV2 = mockHttpClient

      val urlHeaderEnvironment: String = ""
      val urlHeaderAuthorization: String = ""
      val audit: Audit = new TestAudit(mockAuditConnector)
      val appName: String = "Test"
      val metrics: ServiceMetrics = app.injector.instanceOf[ServiceMetrics]
      override val atedBaseURI: String = ""
      override val retrieveSubscriptionData: String = ""
      override val saveSubscriptionData: String = ""
      override val clientSecret: String = ""
      override val clientId: String = ""
      override val originatingSystem: String = ""
    }

    val connector = new TestHipDetailsConnector()
  }

  "HipDetailsConnector" must {

    "get subscription data" must {
      "Correctly return no data if there is none" in new Setup {
        val notFoundResponse = Json.parse("""{}""")

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(NOT_FOUND, notFoundResponse, Map.empty[String, Seq[String]])))

        val result = connector.getSubscriptionData("ATED-123")
        val response = await(result)
        response.status must be(NOT_FOUND)
        response.json must be(notFoundResponse)
      }

      "Correctly return data if we have some" in new Setup {
        val successResponse = Json.parse(
          """{
            |    "processingDate": "2001-12-17T09:30:47Z",
            |    "formBundleNumber": "12345678"
            |}""".stripMargin)

        val wrappedSuccessResponse = Json.parse(
          """{
            |    "success": {
            |        "processingDate": "2001-12-17T09:30:47Z",
            |        "formBundleNumber": "12345678"
            |    }
            |}""".stripMargin)

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, wrappedSuccessResponse, Map.empty[String, Seq[String]])))

        val result = connector.getSubscriptionData("ATED-123")
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "get data with unprocessable entity response converted to relevant status" in new Setup {

        val scenarios = List(
          ("003", NOT_FOUND),
          ("004", BAD_REQUEST),
          ("005", UNAUTHORIZED),
          ("999", INTERNAL_SERVER_ERROR)
        )

        scenarios.foreach{ case (code, status) =>
          val unprocessableResponse = Json.parse(
            s"""{
               |  "errors": {
               |    "processingDate": "2025-12-09T12:34:46.672Z",
               |    "code": "$code",
               |    "text": "ID not found"
               |  }
               |}
               |""".stripMargin)

          when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(UNPROCESSABLE_ENTITY, unprocessableResponse, Map.empty[String, Seq[String]])))

          val result = connector.getSubscriptionData("ATED-123")
          val response = await(result)
          response.status must be(status)
        }
      }
    }

    "update subscription data" must {
      val addressDetails = AddressDetails("Correspondence", "line1", "line2", None, None, Some("postCode"), "GB")
      val addressDetailsNoPostcode = AddressDetails("Correspondence", "line1", "line2", None, None, None, "GB")
      val updatedData = new UpdateEtmpSubscriptionDataRequest(SessionUtils.getUniqueAckNo, emailConsent = true, ChangeIndicators(), None,
        List(Address(addressDetails = addressDetails)))
      val updatedDataNoPostcode = new UpdateEtmpSubscriptionDataRequest(SessionUtils.getUniqueAckNo, emailConsent = true, ChangeIndicators(), None,
        List(Address(addressDetails = addressDetailsNoPostcode)))

      "Correctly submit data if with a valid response" in new Setup {
        val successResponse = Json.parse(
          """{
            |    "processingDate": "2001-12-17T09:30:47Z",
            |    "formBundleNumber": "12345678"
            |}""".stripMargin)

        val wrappedSuccessResponse = Json.parse(
          """{
            |    "success": {
            |        "processingDate": "2001-12-17T09:30:47Z",
            |        "formBundleNumber": "12345678"
            |    }
            |}""".stripMargin)

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, wrappedSuccessResponse, Map.empty[String, Seq[String]])))

        val result = connector.updateSubscriptionData("ATED-123", updatedData)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "Correctly submit data if with a valid response and no postcode" in new Setup {
        val successResponse = Json.parse(
          """{
            |    "processingDate": "2001-12-17T09:30:47Z",
            |    "formBundleNumber": "12345678"
            |}""".stripMargin)

        val wrappedSuccessResponse = Json.parse(
          """{
            |    "success": {
            |        "processingDate": "2001-12-17T09:30:47Z",
            |        "formBundleNumber": "12345678"
            |    }
            |}""".stripMargin)

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, wrappedSuccessResponse, Map.empty[String, Seq[String]])))

        val result = connector.updateSubscriptionData("ATED-123", updatedDataNoPostcode)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "submit data  with an invalid response" in new Setup {
        val notFoundResponse = Json.parse( """{}""")

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(NOT_FOUND, notFoundResponse, Map.empty[String, Seq[String]])))

        val result = connector.updateSubscriptionData("ATED-123", updatedData)
        val response = await(result)
        response.status must be(NOT_FOUND)
      }

      "submit data with unprocessable entity response converted to relevant status" in new Setup {

        val scenarios = List(
          ("003", NOT_FOUND),
          ("004", BAD_REQUEST),
          ("005", UNAUTHORIZED),
          ("006", BAD_REQUEST),
          ("999", INTERNAL_SERVER_ERROR)
        )

        scenarios.foreach{ case (code, status) =>
          val unprocessableResponse = Json.parse(
            s"""{
               |  "errors": {
               |    "processingDate": "2025-12-09T12:34:46.672Z",
               |    "code": "$code",
               |    "text": "ID not found"
               |  }
               |}
               |""".stripMargin)

          when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(UNPROCESSABLE_ENTITY, unprocessableResponse, Map.empty[String, Seq[String]])))

          val result = connector.updateSubscriptionData("ATED-123", updatedData)
          val response = await(result)
          response.status must be(status)
        }
      }
    }
  }
}
