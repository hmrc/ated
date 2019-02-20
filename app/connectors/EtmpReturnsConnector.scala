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

import audit.Auditable
import config.{MicroserviceAuditConnector, WSHttp}
import metrics.{Metrics, MetricsEnum}
import models._
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EtmpReturnsConnector extends ServicesConfig with RawResponseReads with Auditable {

  val baseURI = "annual-tax-enveloped-dwellings"
  val submitReturnsURI = "returns"
  val submitEditedLiabilityReturnsURI = "returns"
  val submitClientRelationship = "relationship"
  val getSummaryReturns = "returns"
  val formBundleReturns = "form-bundle"

  def serviceUrl: String

  def urlHeaderEnvironment: String

  def urlHeaderAuthorization: String

  def metrics: Metrics

  def http: CoreGet with CorePost with CorePut


  def submitReturns(atedReferenceNo: String, submitReturns: SubmitEtmpReturnsRequest): Future[HttpResponse] = {
    implicit val headerCarrier = createHeaderCarrier
    val postUrl = s"""$serviceUrl/$baseURI/$submitReturnsURI/$atedReferenceNo"""

    val jsonData = Json.toJson(submitReturns)
    val timerContext = metrics.startTimer(MetricsEnum.EtmpSubmitReturns)
    http.POST(postUrl, jsonData).map { response =>
      timerContext.stop()
      auditSubmitReturns(atedReferenceNo, submitReturns, response)
      if (submitReturns.liabilityReturns.isDefined) {
        auditAddress(submitReturns.liabilityReturns.get.head.propertyDetails)
      }
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpSubmitReturns)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpSubmitReturns)
          Logger.warn(s"[EtmpReturnsConnector][submitReturns] - status: $status")
          doHeaderEvent("submitReturnsFailedHeaders", response.allHeaders)
          doFailedAudit("submitReturnsFailed", postUrl, Some(jsonData.toString), response.body)
          response
      }
    }
  }

  def getSummaryReturns(atedReferenceNo: String, years: Int): Future[HttpResponse] = {
    implicit val headerCarrier = createHeaderCarrier
    val getUrl = s"""$serviceUrl/$baseURI/$getSummaryReturns/$atedReferenceNo?years=$years"""

    val timerContext = metrics.startTimer(MetricsEnum.EtmpGetSummaryReturns)
    http.GET[HttpResponse](getUrl).map { response =>
      timerContext.stop()
      response.status match {
        case OK | NOT_FOUND =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpGetSummaryReturns)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpGetSummaryReturns)
          Logger.warn(s"[EtmpReturnsConnector][getSummaryReturns] - status: $status")
          doHeaderEvent("getSummaryReturnsFailedHeaders", response.allHeaders)
          doFailedAudit("getSummaryReturnsFailed", getUrl, None, response.body)
          response
      }
    }
  }

  def getFormBundleReturns(atedReferenceNo: String, formBundleNumber: String): Future[HttpResponse] = {
    implicit val headerCarrier = createHeaderCarrier
    val getUrl = s"""$serviceUrl/$baseURI/$getSummaryReturns/$atedReferenceNo/$formBundleReturns/$formBundleNumber"""

    val timerContext = metrics.startTimer(MetricsEnum.EtmpGetFormBundleReturns)
    http.GET[HttpResponse](getUrl).map { response =>
      timerContext.stop()
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpGetFormBundleReturns)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpGetFormBundleReturns)
          Logger.warn(s"[EtmpReturnsConnector][getFormBundleReturns] - status: $status")
          doHeaderEvent("getFormBundleReturnsFailedHeaders", response.allHeaders)
          doFailedAudit("getFormBundleReturnsFailed", getUrl, None, response.body)
          response
      }
    }
  }

  def submitEditedLiabilityReturns(atedReferenceNo: String, editedLiabilityReturns: EditLiabilityReturnsRequestModel, disposal: Boolean = false): Future[HttpResponse] = {
    implicit val headerCarrier = createHeaderCarrier
    val putUrl = s"""$serviceUrl/$baseURI/$submitEditedLiabilityReturnsURI/$atedReferenceNo"""

    val jsonData = Json.toJson(editedLiabilityReturns)
    val timerContext = metrics.startTimer(MetricsEnum.EtmpSubmitEditedLiabilityReturns)
    http.PUT[JsValue, HttpResponse](putUrl, jsonData).map { response =>
      timerContext.stop()
      auditSubmitEditedLiabilityReturns(atedReferenceNo, editedLiabilityReturns, response, disposal)
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpSubmitEditedLiabilityReturns)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpSubmitEditedLiabilityReturns)
          Logger.warn(s"[EtmpReturnsConnector][submitEditedLiabilityReturns] - status: $status")
          doHeaderEvent("getSummaryReturnsFailed", response.allHeaders)
          doFailedAudit("submitEditedLiabilityReturnsFailed", putUrl, Some(jsonData.toString), response.body)
          response
      }
    }

  }

  private def createHeaderCarrier: HeaderCarrier = {
    HeaderCarrier(extraHeaders = Seq("Environment" -> urlHeaderEnvironment),
      authorization = Some(Authorization(urlHeaderAuthorization)))
  }

  private def auditSubmitReturns(atedReferenceNo: String,
                                 returns: SubmitEtmpReturnsRequest,
                                 response: HttpResponse)(implicit hc: HeaderCarrier) {
    val eventType = response.status match {
      case OK => EventTypes.Succeeded
      case _ => EventTypes.Failed
    }
    sendDataEvent(transactionName = "etmpSubmitReturns",
      detail = Map("txName" -> "etmpSubmitReturns",
        "atedRefNumber" -> s"$atedReferenceNo",
        "agentRefNo" -> s"${returns.agentReferenceNumber.getOrElse("")}",
        "liabilityReturns_count" -> s"${if (returns.liabilityReturns.isDefined) returns.liabilityReturns.get.size else 0}",
        "reliefReturns_count" -> s"${ if (returns.reliefReturns.isDefined) returns.reliefReturns.get.size else 0 }",
        "reliefReturnCodes" -> s"${ returns.reliefReturns match {
          case Some(reliefReturns) => reliefReturns.map(x => x.reliefDescription).mkString(";")
          case None => ""
        }}",
        "requestBody" -> s"${Json.toJson(returns)}",
        "responseStatus" -> s"${response.status}",
        "responseBody" -> s"${response.body}",
        "status" -> s"$eventType"))
  }


  private def auditSubmitEditedLiabilityReturns(atedReferenceNo: String,
                                                returns: EditLiabilityReturnsRequestModel,
                                                response: HttpResponse,
                                                disposal: Boolean)(implicit hc: HeaderCarrier) {
    val eventType = response.status match {
      case OK => EventTypes.Succeeded
      case _ => EventTypes.Failed
    }

    val typeOfReturn = {
      if (disposal) "D"
      else {
        val amountField = (Json.parse(response.body) \\ "amountDueOrRefund").headOption
        amountField match {
          case Some(x) =>
            val y = x.as[BigDecimal]
            if (y > 0) "F"
            else if (y < 0) "A"
            else "C"
          case None => ""
        }
      }
    }
    sendDataEvent(transactionName = "etmpSubmitEditedLiabilityReturns",
      detail = Map("txName" -> "etmpSubmitEditedLiabilityReturns",
        "atedRefNumber" -> s"$atedReferenceNo",
        "agentRefNo" -> s"${returns.agentReferenceNumber.getOrElse("")}",
        "liabilityReturns count" -> s"${returns.liabilityReturn.size}",
        "amended_further_changed_return" -> typeOfReturn,
        "requestBody" -> s"${Json.toJson(returns)}",
        "responseStatus" -> s"${response.status}",
        "responseBody" -> s"${response.body}",
        "status" -> s"$eventType"))

    auditLiabilityReturnsBankDetails(atedReferenceNo, returns, eventType, typeOfReturn)
  }

  private def auditAddress(addressDetails: Option[EtmpPropertyDetails])(implicit hc: HeaderCarrier) = {
    addressDetails.map { x =>
      sendDataEvent(transactionName = "manualAddressSubmitted",
        detail = Map(
          "submittedLine1" -> addressDetails.get.address.addressLine1,
          "submittedLine2" -> addressDetails.get.address.addressLine2,
          "submittedLine3" -> addressDetails.get.address.addressLine3.getOrElse(""),
          "submittedLine4" -> addressDetails.get.address.addressLine4.getOrElse(""),
          "submittedPostcode" -> addressDetails.get.address.postalCode.getOrElse(""),
          "submittedCountry" -> addressDetails.get.address.countryCode))
    }
  }

  private def auditLiabilityReturnsBankDetails(atedReferenceNo: String,
                                               editedLiabilityReturns: EditLiabilityReturnsRequestModel,
                                               eventType: String,
                                               typeOfReturn: String)(implicit hc: HeaderCarrier) = {

    //Only Audit the Bank Details from the Head
    val headBankDetails = editedLiabilityReturns.liabilityReturn.headOption.flatMap(_.bankDetails)
    headBankDetails.map{ bankDetailsData =>
      sendDataEvent("etmpLiabilityReturnsBankDetails",
        detail = Map(
          "txName" -> "etmpLiabilityReturnsBankDetails",
          "atedRefNumber" -> atedReferenceNo,
          "accountName" ->  bankDetailsData.accountName,
          "sortCode" -> bankDetailsData.ukAccount.map(_.sortCode).getOrElse(""),
          "accountNumber" ->  bankDetailsData.ukAccount.map(_.accountNumber).getOrElse(""),
          "iban" ->  bankDetailsData.internationalAccount.map(_.iban).getOrElse(""),
          "bicSwiftCode" ->  bankDetailsData.internationalAccount.map(_.bicSwiftCode).getOrElse(""),
          "amended_further_changed_return" -> typeOfReturn,
          "status" -> s"${eventType}")
      )
    }
  }
}

object EtmpReturnsConnector extends EtmpReturnsConnector {

  val serviceUrl = baseUrl("etmp-hod")

  val urlHeaderEnvironment: String = config("etmp-hod").getString("environment").getOrElse("")

  val urlHeaderAuthorization: String = s"Bearer ${config("etmp-hod").getString("authorization-token").getOrElse("")}"

  val http: CoreGet with CorePost with CorePut = WSHttp

  val audit: Audit = new Audit(AppName.appName, MicroserviceAuditConnector)

  val appName: String = AppName.appName

  val metrics = Metrics

}
