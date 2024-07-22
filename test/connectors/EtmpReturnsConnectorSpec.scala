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

import java.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
//import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import utils.SessionUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EtmpReturnsConnectorSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfter {

  val mockWSHttp: HttpClientV2 = mock[HttpClientV2]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  val testFormBundleNum = "123456789012"

  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup extends ConnectorTest {
    class TestEtmpReturnsConnector extends EtmpReturnsConnector {
      val serviceUrl = "etmp-hod"
      val http: HttpClientV2 = mockWSHttp
      val urlHeaderEnvironment: String = ""
      val urlHeaderAuthorization: String = ""
      val audit: Audit = new TestAudit(mockAuditConnector)
      val appName: String = "Test"
      val metrics: ServiceMetrics = app.injector.instanceOf[ServiceMetrics]
      override val baseURI: String = ""
      override val submitReturnsURI: String = ""
      override val submitEditedLiabilityReturnsURI: String = ""
      override val submitClientRelationship: String = ""
      override val getSummaryReturns: String = ""
      override val formBundleReturns: String = ""
    }

    val connector = new TestEtmpReturnsConnector()
  }

  before {
    reset(mockWSHttp)
  }


  "EtmpReturnsConnector" must {

    "submit ated returns" must {
      "Correctly Submit a return with reliefs" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        //when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, successResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(200, successResponse, Map.empty[String, Seq[String]])))
        val reliefReturns = Seq(EtmpReliefReturns("", LocalDate.now(), LocalDate.now(), ""))
        val atedReturns = SubmitEtmpReturnsRequest(acknowledgementReference = SessionUtils.getUniqueAckNo,
          agentReferenceNumber = None, reliefReturns = Some(reliefReturns), liabilityReturns = None)
        val result = connector.submitReturns("ATED-123", atedReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "Correctly Submit a return with liabilities" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        //when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, successResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(200, successResponse, Map.empty[String, Seq[String]])))
        val _propertyDetails = Some(EtmpPropertyDetails(address = EtmpAddress("line1", "line2", Some("line3"), Some("line4"), "", Some(""))))
        val liabilityReturns = Seq(EtmpLiabilityReturns("", "", "", propertyDetails = _propertyDetails, dateOfValuation = LocalDate.now(), professionalValuation = false, ninetyDayRuleApplies = false, lineItems = Nil))
        val atedReturns = SubmitEtmpReturnsRequest(acknowledgementReference = SessionUtils.getUniqueAckNo,
          agentReferenceNumber = None, reliefReturns = None, liabilityReturns = Some(liabilityReturns))
        val result = connector.submitReturns("ATED-123", atedReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "check for a failure response" in new Setup {
        val failureResponse = Json.parse( """{"Reason" : "Service Unavailable"}""")
        //when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).thenReturn(Future.successful(HttpResponse(503, failureResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(503, failureResponse, Map.empty[String, Seq[String]])))
        val atedReturns = SubmitEtmpReturnsRequest(acknowledgementReference = SessionUtils.getUniqueAckNo,
          agentReferenceNumber = None, reliefReturns = None, liabilityReturns = None)
        val result = connector.submitReturns("ATED-123", atedReturns)
        val response = await(result)
        response.status must be(SERVICE_UNAVAILABLE)
        response.json must be(failureResponse)
      }
    }

    "get summary returns" must {
      "Correctly return no data if there is none" in new Setup {
        val notFoundResponse = Json.parse( """{}""")

        //when(mockWSHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, notFoundResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(NOT_FOUND, notFoundResponse, Map.empty[String, Seq[String]])))

        val result = connector.getSummaryReturns("ATED-123", 1)
        val response = await(result)
        response.status must be(NOT_FOUND)
        response.json must be(notFoundResponse)
      }

      "Correctly return data if we have some" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        //when(mockWSHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))

        val result = connector.getSummaryReturns("ATED-123", 1)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "not return data if we get some other status" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        //when(mockWSHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, successResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, successResponse, Map.empty[String, Seq[String]])))

        val result = connector.getSummaryReturns("ATED-123", 1)
        val response = await(result)
        response.status must be(BAD_REQUEST)
        response.body must include(" \"processingDate\"")
      }
    }

    "get form bundle returns" must {
      "Correctly return no data if there is none" in new Setup {
        val notFoundResponse = Json.parse( """{}""")

        //when(mockWSHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, notFoundResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(NOT_FOUND, notFoundResponse, Map.empty[String, Seq[String]])))

        val result = connector.getFormBundleReturns("ATED-123", testFormBundleNum)
        val response = await(result)
        response.status must be(NOT_FOUND)
        response.json must be(notFoundResponse)
      }

      "Correctly return data if we have some" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        //when(mockWSHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))

        val result = connector.getFormBundleReturns("ATED-123", testFormBundleNum)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }
    }

    "submit edited liability returns" must {

      "correctly submit a disposal return" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, LocalDate.of(2015, 2, 3), LocalDate.of(2015, 2, 3), "Liability")
        val editLiabReturnReq = EditLiabilityReturnsRequest(oldFormBundleNumber = "form-123",
          mode = "Pre-Calculation",
          periodKey = "2015",
          propertyDetails = p,
          dateOfValuation = LocalDate.now,
          professionalValuation = true,
          ninetyDayRuleApplies = true,
          bankDetails = Some(EtmpBankDetails(accountName = "testAccountName", ukAccount = Some(UKAccount(sortCode = "20-01-01", accountNumber = "123456789")))),
          lineItem = Seq(lineItem1))
        val editLiablityReturns = EditLiabilityReturnsRequestModel(acknowledgmentReference = SessionUtils.getUniqueAckNo, liabilityReturn = Seq(editLiabReturnReq))

        //when(mockWSHttp.PUT[JsValue, HttpResponse](any(), ArgumentMatchers.eq(Json.toJson(editLiablityReturns)), any())(any(), any(), any(), any())).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))


        val result = connector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns, disposal = true)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "correctly submit an amended return" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z", "amountDueOrRefund": -1.0}""")

        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, LocalDate.of(2015, 2, 3), LocalDate.of(2015, 2, 3), "Liability")
        val editLiabReturnReq = EditLiabilityReturnsRequest(oldFormBundleNumber = "form-123", mode = "Pre-Calculation", periodKey = "2015", propertyDetails = p, dateOfValuation = LocalDate.now, professionalValuation = true, ninetyDayRuleApplies = true, lineItem = Seq(lineItem1))
        val editLiablityReturns = EditLiabilityReturnsRequestModel(acknowledgmentReference = SessionUtils.getUniqueAckNo, liabilityReturn = Seq(editLiabReturnReq))

        //when(mockWSHttp.PUT[JsValue, HttpResponse](any(), ArgumentMatchers.eq(Json.toJson(editLiablityReturns)), any())(any(), any(), any(), any())).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))


        val result = connector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "correctly submit a further return" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z", "amountDueOrRefund": 1.0}""")

        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, LocalDate.of(2015, 2, 3), LocalDate.of(2015, 2, 3), "Liability")
        val editLiabReturnReq = EditLiabilityReturnsRequest(oldFormBundleNumber = "form-123", mode = "Pre-Calculation", periodKey = "2015", propertyDetails = p, dateOfValuation = LocalDate.now, professionalValuation = true, ninetyDayRuleApplies = true, lineItem = Seq(lineItem1))
        val editLiablityReturns = EditLiabilityReturnsRequestModel(acknowledgmentReference = SessionUtils.getUniqueAckNo, liabilityReturn = Seq(editLiabReturnReq))

        //when(mockWSHttp.PUT[JsValue, HttpResponse](any(), ArgumentMatchers.eq(Json.toJson(editLiablityReturns)), any())(any(), any(),) any(), any())).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))


        val result = connector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "correctly submit a change of details return" in new Setup {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z", "amountDueOrRefund": 0.0}""")

        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, LocalDate.of(2015, 2, 3), LocalDate.of(2015, 2, 3), "Liability")
        val editLiabReturnReq = EditLiabilityReturnsRequest(oldFormBundleNumber = "form-123", mode = "Pre-Calculation", periodKey = "2015", propertyDetails = p, dateOfValuation = LocalDate.now, professionalValuation = true, ninetyDayRuleApplies = true, lineItem = Seq(lineItem1))
        val editLiablityReturns = EditLiabilityReturnsRequestModel(acknowledgmentReference = SessionUtils.getUniqueAckNo, liabilityReturn = Seq(editLiabReturnReq))

        //when(mockWSHttp.PUT[JsValue, HttpResponse](any(), ArgumentMatchers.eq(Json.toJson(editLiablityReturns)), any())(any(), any(), any(), any())).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))


        val result = connector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "check for a failure response" in new Setup {

        val failureResponse = Json.parse( """{"Reason" : "Service Unavailable"}""")

        //when(mockWSHttp.PUT[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, failureResponse, Map.empty[String, Seq[String]])))
        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, failureResponse, Map.empty[String, Seq[String]])))

        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, LocalDate.of(2015, 2, 3), LocalDate.of(2015, 2, 3), "Liability")
        val editLiabReturnReq = EditLiabilityReturnsRequest(oldFormBundleNumber = "form-123", mode = "Pre-Calculation", periodKey = "2015", propertyDetails = p, dateOfValuation = LocalDate.now, professionalValuation = true, ninetyDayRuleApplies = true, lineItem = Seq(lineItem1))
        val editLiablityReturns = EditLiabilityReturnsRequestModel(acknowledgmentReference = SessionUtils.getUniqueAckNo, liabilityReturn = Seq(editLiabReturnReq))


        val result = connector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns)
        val response = await(result)
        response.status must be(INTERNAL_SERVER_ERROR)
        response.json must be(failureResponse)
      }

    }
  }

}
