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
import org.joda.time.LocalDate
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

class EtmpReturnsConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfter {

  trait MockedVerbs extends CoreGet with CorePost with CorePut
  val mockWSHttp: CoreGet with CorePost with CorePut = mock[MockedVerbs]

  val testFormBundleNum = "123456789012"

  object TestEtmpReturnsConnector extends EtmpReturnsConnector {
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


  "EtmpReturnsConnector" must {

    "have a service url" in {
      EtmpReturnsConnector.serviceUrl == "etmp-hod"
    }

    "use correct metrics" in {
      EtmpReturnsConnector.metrics must be(Metrics)
    }

    "submit ated returns" must {
      "Correctly Submit a return with reliefs" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, responseJson = Some(successResponse))))
        val reliefReturns = Seq(EtmpReliefReturns("", LocalDate.now(), LocalDate.now(), ""))
        val atedReturns = SubmitEtmpReturnsRequest(acknowledgementReference = SessionUtils.getUniqueAckNo,
          agentReferenceNumber = None, reliefReturns = Some(reliefReturns), liabilityReturns = None)
        val result = TestEtmpReturnsConnector.submitReturns("ATED-123", atedReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "Correctly Submit a return with liabilities" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, responseJson = Some(successResponse))))
        val _propertyDetails = Some(EtmpPropertyDetails(address = EtmpAddress("line1", "line2", Some("line3"), Some("line4"), "", Some(""))))
        val liabilityReturns = Seq(EtmpLiabilityReturns("", "", "", propertyDetails = _propertyDetails, dateOfValuation = LocalDate.now(), professionalValuation = false, ninetyDayRuleApplies = false, lineItems = Nil))
        val atedReturns = SubmitEtmpReturnsRequest(acknowledgementReference = SessionUtils.getUniqueAckNo,
          agentReferenceNumber = None, reliefReturns = None, liabilityReturns = Some(liabilityReturns))
        val result = TestEtmpReturnsConnector.submitReturns("ATED-123", atedReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "check for a failure response" in {
        val failureResponse = Json.parse( """{"Reason" : "Service Unavailable"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(503, responseJson = Some(failureResponse))))
        val atedReturns = SubmitEtmpReturnsRequest(acknowledgementReference = SessionUtils.getUniqueAckNo,
          agentReferenceNumber = None, reliefReturns = None, liabilityReturns = None)
        val result = TestEtmpReturnsConnector.submitReturns("ATED-123", atedReturns)
        val response = await(result)
        response.status must be(SERVICE_UNAVAILABLE)
        response.json must be(failureResponse)
      }
    }

    "get summary returns" must {
      "Correctly return no data if there is none" in {
        val notFoundResponse = Json.parse( """{}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(notFoundResponse))))

        val result = TestEtmpReturnsConnector.getSummaryReturns("ATED-123", 1)
        val response = await(result)
        response.status must be(NOT_FOUND)
        response.json must be(notFoundResponse)
      }

      "Correctly return data if we have some" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))

        val result = TestEtmpReturnsConnector.getSummaryReturns("ATED-123", 1)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "not return data if we get some other status" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(successResponse))))

        val result = TestEtmpReturnsConnector.getSummaryReturns("ATED-123", 1)
        val response = await(result)
        response.status must be(BAD_REQUEST)
        response.body must include(" \"processingDate\"")
      }
    }

    "get form bundle returns" must {
      "Correctly return no data if there is none" in {
        val notFoundResponse = Json.parse( """{}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(notFoundResponse))))

        val result = TestEtmpReturnsConnector.getFormBundleReturns("ATED-123", testFormBundleNum)
        val response = await(result)
        response.status must be(NOT_FOUND)
        response.json must be(notFoundResponse)
      }

      "Correctly return data if we have some" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))

        val result = TestEtmpReturnsConnector.getFormBundleReturns("ATED-123", testFormBundleNum)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }
    }

    "submit edited liability returns" must {

      "correctly submit a disposal return" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, new LocalDate("2015-02-03"), new LocalDate("2015-02-03"), "Liability")
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

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.eq(Json.toJson(editLiablityReturns)))(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successResponse))))


        val result = TestEtmpReturnsConnector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns, true)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "correctly submit an amended return" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z", "amountDueOrRefund": -1.0}""")

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, new LocalDate("2015-02-03"), new LocalDate("2015-02-03"), "Liability")
        val editLiabReturnReq = EditLiabilityReturnsRequest(oldFormBundleNumber = "form-123", mode = "Pre-Calculation", periodKey = "2015", propertyDetails = p, dateOfValuation = LocalDate.now, professionalValuation = true, ninetyDayRuleApplies = true, lineItem = Seq(lineItem1))
        val editLiablityReturns = EditLiabilityReturnsRequestModel(acknowledgmentReference = SessionUtils.getUniqueAckNo, liabilityReturn = Seq(editLiabReturnReq))

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.eq(Json.toJson(editLiablityReturns)))(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successResponse))))


        val result = TestEtmpReturnsConnector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "correctly submit a further return" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z", "amountDueOrRefund": 1.0}""")

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, new LocalDate("2015-02-03"), new LocalDate("2015-02-03"), "Liability")
        val editLiabReturnReq = EditLiabilityReturnsRequest(oldFormBundleNumber = "form-123", mode = "Pre-Calculation", periodKey = "2015", propertyDetails = p, dateOfValuation = LocalDate.now, professionalValuation = true, ninetyDayRuleApplies = true, lineItem = Seq(lineItem1))
        val editLiablityReturns = EditLiabilityReturnsRequestModel(acknowledgmentReference = SessionUtils.getUniqueAckNo, liabilityReturn = Seq(editLiabReturnReq))

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.eq(Json.toJson(editLiablityReturns)))(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successResponse))))


        val result = TestEtmpReturnsConnector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "correctly submit a change of details return" in {
        val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z", "amountDueOrRefund": 0.0}""")

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, new LocalDate("2015-02-03"), new LocalDate("2015-02-03"), "Liability")
        val editLiabReturnReq = EditLiabilityReturnsRequest(oldFormBundleNumber = "form-123", mode = "Pre-Calculation", periodKey = "2015", propertyDetails = p, dateOfValuation = LocalDate.now, professionalValuation = true, ninetyDayRuleApplies = true, lineItem = Seq(lineItem1))
        val editLiablityReturns = EditLiabilityReturnsRequestModel(acknowledgmentReference = SessionUtils.getUniqueAckNo, liabilityReturn = Seq(editLiabReturnReq))

        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.eq(Json.toJson(editLiablityReturns)))(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successResponse))))


        val result = TestEtmpReturnsConnector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }

      "check for a failure response" in {

        val failureResponse = Json.parse( """{"Reason" : "Service Unavailable"}""")

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
        when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(failureResponse))))

        val address = EtmpAddress("address-line-1", "address-line-2", None, None, "GB")
        val p = EtmpPropertyDetails(address = address)
        val lineItem1 = EtmpLineItems(123456, new LocalDate("2015-02-03"), new LocalDate("2015-02-03"), "Liability")
        val editLiabReturnReq = EditLiabilityReturnsRequest(oldFormBundleNumber = "form-123", mode = "Pre-Calculation", periodKey = "2015", propertyDetails = p, dateOfValuation = LocalDate.now, professionalValuation = true, ninetyDayRuleApplies = true, lineItem = Seq(lineItem1))
        val editLiablityReturns = EditLiabilityReturnsRequestModel(acknowledgmentReference = SessionUtils.getUniqueAckNo, liabilityReturn = Seq(editLiabReturnReq))


        val result = TestEtmpReturnsConnector.submitEditedLiabilityReturns("ATED-123", editLiablityReturns)
        val response = await(result)
        response.status must be(INTERNAL_SERVER_ERROR)
        response.json must be(failureResponse)
      }

    }
  }

}
