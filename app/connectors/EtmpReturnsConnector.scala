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

import audit.Auditable
import metrics.{MetricsEnum, ServiceMetrics}
import models._
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EtmpReturnsConnectorImpl @Inject()(val servicesConfig: ServicesConfig,
                                         val http: HttpClientV2,
                                         val auditConnector: AuditConnector,
                                         val metrics: ServiceMetrics) extends EtmpReturnsConnector {
  val serviceUrl: String = servicesConfig.baseUrl("etmp-hod")
  val urlHeaderEnvironment: String = servicesConfig.getConfString("etmp-hod.environment", "")
  val urlHeaderAuthorization: String = s"Bearer ${servicesConfig.getConfString("etmp-hod.authorization-token", "")}"

  val audit: Audit = new Audit("ated", auditConnector)

  val baseURI: String = "annual-tax-enveloped-dwellings"
  val submitReturnsURI: String = "returns"
  val submitEditedLiabilityReturnsURI: String = "returns"
  val submitClientRelationship: String = "relationship"
  val getSummaryReturns: String = "returns"
  val formBundleReturns: String = "form-bundle"
}

trait EtmpReturnsConnector extends Auditable with Logging {
  def serviceUrl: String
  def urlHeaderEnvironment: String
  def urlHeaderAuthorization: String

  def metrics: ServiceMetrics
  def http: HttpClientV2

  val baseURI: String
  val submitReturnsURI: String
  val submitEditedLiabilityReturnsURI: String
  val submitClientRelationship: String
  val getSummaryReturns: String
  val formBundleReturns: String

  def submitReturns(atedReferenceNo: String, submitReturns: SubmitEtmpReturnsRequest)
                   (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[HttpResponse] = {
    val postUrl = s"""$serviceUrl/$baseURI/$submitReturnsURI/$atedReferenceNo"""

    val jsonData = Json.toJson(submitReturns)
    val timerContext = metrics.startTimer(MetricsEnum.EtmpSubmitReturns)
      http.post(url"$postUrl").withBody(jsonData).setHeader(createHeaders: _*).execute[HttpResponse].map{ response =>
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
          logger.warn(s"[EtmpReturnsConnector][submitReturns] - status: $status")
          doHeaderEvent("submitReturnsFailedHeaders", response.headers)
          doFailedAudit("submitReturnsFailed", postUrl, Some(jsonData.toString), response.body)
          response
      }
    }
  }

  def getSummaryReturns(atedReferenceNo: String, years: Int)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[HttpResponse] = {
    val getUrl = s"""$serviceUrl/$baseURI/$getSummaryReturns/$atedReferenceNo?years=$years"""

    val timerContext = metrics.startTimer(MetricsEnum.EtmpGetSummaryReturns)
    http.get(url"$getUrl").setHeader(createHeaders: _*).execute[HttpResponse].map{ response =>
      timerContext.stop()
      response.status match {
        case OK | NOT_FOUND =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpGetSummaryReturns)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpGetSummaryReturns)
          logger.warn(s"[EtmpReturnsConnector][getSummaryReturns] - status: $status")
          doHeaderEvent("getSummaryReturnsFailedHeaders", response.headers)
          doFailedAudit("getSummaryReturnsFailed", getUrl, None, response.body)
          response
      }
    }
  }

  def getFormBundleReturns(atedReferenceNo: String, formBundleNumber: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[HttpResponse] = {
    val getUrl = s"""$serviceUrl/$baseURI/$getSummaryReturns/$atedReferenceNo/$formBundleReturns/$formBundleNumber"""

    val timerContext = metrics.startTimer(MetricsEnum.EtmpGetFormBundleReturns)
    http.get(url"$getUrl").setHeader(createHeaders: _*).execute[HttpResponse].map{ response =>
      timerContext.stop()
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpGetFormBundleReturns)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpGetFormBundleReturns)
          logger.warn(s"[EtmpReturnsConnector][getFormBundleReturns] - status: $status")
          doHeaderEvent("getFormBundleReturnsFailedHeaders", response.headers)
          doFailedAudit("getFormBundleReturnsFailed", getUrl, None, response.body)
          response
      }
    }
  }

  def submitEditedLiabilityReturns(atedReferenceNo: String,
                                   editedLiabilityReturns: EditLiabilityReturnsRequestModel,
                                   disposal: Boolean = false)(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val putUrl = s"""$serviceUrl/$baseURI/$submitEditedLiabilityReturnsURI/$atedReferenceNo"""

    val jsonData = Json.toJson(editedLiabilityReturns)
    val timerContext = metrics.startTimer(MetricsEnum.EtmpSubmitEditedLiabilityReturns)
    http.put(url"$putUrl").withBody(jsonData).setHeader(createHeaders: _*).execute[HttpResponse].map{ response =>
      timerContext.stop()
      auditSubmitEditedLiabilityReturns(atedReferenceNo, editedLiabilityReturns, response, disposal)
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpSubmitEditedLiabilityReturns)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpSubmitEditedLiabilityReturns)
          logger.warn(s"[EtmpReturnsConnector][submitEditedLiabilityReturns] - status: $status, reason - ${response.json}")
          doHeaderEvent("getSummaryReturnsFailed", response.headers)
          doFailedAudit("submitEditedLiabilityReturnsFailed", putUrl, Some(jsonData.toString), response.body)
          response
      }
    }

  }

  private def createHeaders: Seq[(String, String)] = {
    Seq(
      "Environment" -> urlHeaderEnvironment,
      "Authorization" -> urlHeaderAuthorization
    )
  }

  private def auditSubmitReturns(atedReferenceNo: String,
                                 returns: SubmitEtmpReturnsRequest,
                                 response: HttpResponse)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
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
                                                disposal: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
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

  private def auditAddress(addressDetails: Option[EtmpPropertyDetails])(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    addressDetails.map { _ =>
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
                                               typeOfReturn: String)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {

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
          "status" -> s"$eventType")
      )
    }
  }
}