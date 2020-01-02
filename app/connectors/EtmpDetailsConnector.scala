/*
 * Copyright 2020 HM Revenue & Customs
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
import javax.inject.Inject
import metrics.{MetricsEnum, ServiceMetrics}
import models._
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EtmpDetailsConnectorImpl @Inject()(val servicesConfig: ServicesConfig,
                                            val http: HttpClient,
                                            val auditConnector: AuditConnector,
                                            val metrics: ServiceMetrics) extends EtmpDetailsConnector {
  val serviceUrl: String = servicesConfig.baseUrl("etmp-hod")

  val urlHeaderEnvironment: String = servicesConfig.getConfString("etmp-hod.environment", "")
  val urlHeaderAuthorization: String = s"Bearer ${servicesConfig.getConfString("etmp-hod.authorization-token", "")}"

  val audit: Audit = new Audit("ated", auditConnector)

  val atedBaseURI: String = "annual-tax-enveloped-dwellings"
  val submitClientRelationship: String = "relationship"
  val getAgentClientRelationship: String = "relationship"
  val retrieveSubscriptionData: String = "subscription"
  val saveSubscriptionData: String = "subscription"
  val saveRegistrationDetails: String = "registration/safeid"
}

trait EtmpDetailsConnector extends RawResponseReads with Auditable {
  def serviceUrl: String
  def urlHeaderEnvironment: String
  def urlHeaderAuthorization: String

  def http: HttpClient
  def metrics: ServiceMetrics

  val atedBaseURI: String
  val submitClientRelationship: String
  val getAgentClientRelationship: String
  val retrieveSubscriptionData: String
  val saveSubscriptionData: String
  val saveRegistrationDetails: String

  def getDetails(identifier: String, identifierType: String): Future[HttpResponse] = {
    def getDetailsFromEtmp(getUrl: String): Future[HttpResponse] = {
      implicit val hc = createHeaderCarrier
      val timerContext = metrics.startTimer(MetricsEnum.EtmpGetDetails)
      http.GET[HttpResponse](getUrl).map { response =>
        timerContext.stop()
        response.status match {
          case OK => metrics.incrementSuccessCounter(MetricsEnum.EtmpGetDetails)
          case status =>
            metrics.incrementFailedCounter(MetricsEnum.EtmpGetDetails)
            Logger.warn(s"[EtmpDetailsConnector][getDetailsFromEtmp] - status: $status")
            doHeaderEvent("getDetailsFromEtmpFailedHeaders", response.allHeaders)
            doFailedAudit("getDetailsFromEtmpFailed", getUrl, None, response.body)
        }
        response
      }
    }

    identifierType match {
      case "arn" => getDetailsFromEtmp(s"$serviceUrl/registration/details?arn=$identifier")
      case "safeid" => getDetailsFromEtmp(s"$serviceUrl/registration/details?safeid=$identifier")
      case "utr" => getDetailsFromEtmp(s"$serviceUrl/registration/details?utr=$identifier")
      case unknownIdentifier =>
        Logger.warn(s"[EtmpDetailsConnector][getDetails] - unexpected identifier type supplied of $unknownIdentifier")
        throw new RuntimeException(s"[EtmpDetailsConnector][getDetails] - unexpected identifier type supplied of $unknownIdentifier")
    }
  }


  def getSubscriptionData(atedReferenceNo: String): Future[HttpResponse] = {
    implicit val headerCarrier = createHeaderCarrier
    val getUrl = s"""$serviceUrl/$atedBaseURI/$retrieveSubscriptionData/$atedReferenceNo"""

    val timerContext = metrics.startTimer(MetricsEnum.EtmpGetSubscriptionData)
    http.GET[HttpResponse](getUrl).map { response =>
      timerContext.stop()
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpGetSubscriptionData)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpGetSubscriptionData)
          Logger.warn(s"[EtmpDetailsConnector][getSummaryReturns] - status: $status")
          doHeaderEvent("getSubscriptionDataFailedHeaders", response.allHeaders)
          doFailedAudit("getSubscriptionDataFailed", getUrl, None, response.body)
          response
      }
    }
  }

  def updateSubscriptionData(atedReferenceNo: String, updatedData: UpdateEtmpSubscriptionDataRequest): Future[HttpResponse] = {
    implicit val headerCarrier = createHeaderCarrier
    val putUrl = s"""$serviceUrl/$atedBaseURI/$saveSubscriptionData/$atedReferenceNo"""

    val timerContext = metrics.startTimer(MetricsEnum.EtmpUpdateSubscriptionData)
    val jsonData = Json.toJson(updatedData)
    http.PUT(putUrl, jsonData).map { response =>
      timerContext.stop()
      auditUpdateSubscriptionData(atedReferenceNo, updatedData, response)
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpUpdateSubscriptionData)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpUpdateSubscriptionData)
          Logger.warn(s"[EtmpDetailsConnector][updateSubscriptionData] - status: $status")
          doHeaderEvent("updateSubscriptionDataFailedHeaders", response.allHeaders)
          doFailedAudit("updateSubscriptionDataFailed", putUrl, Some(jsonData.toString), response.body)
          response
      }
    }
  }

  def updateRegistrationDetails(atedReferenceNo: String, safeId: String, updatedData: UpdateRegistrationDetailsRequest): Future[HttpResponse] = {
    implicit val headerCarrier = createHeaderCarrier
    val putUrl = s"""$serviceUrl/$saveRegistrationDetails/$safeId"""
    val timerContext = metrics.startTimer(MetricsEnum.EtmpUpdateRegistrationDetails)
    val jsonData = Json.toJson(updatedData)
    http.PUT(putUrl, jsonData).map { response =>
      timerContext.stop()
      auditUpdateRegistrationDetails(atedReferenceNo, safeId, updatedData, response)
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpUpdateRegistrationDetails)
          response
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpUpdateRegistrationDetails)
          Logger.warn(s"[EtmpDetailsConnector][updateRegistrationDetails] - status: $status")
          doHeaderEvent("updateRegistrationDetailsFailedHeaders", response.allHeaders)
          doFailedAudit("updateRegistrationDetailsFailed", putUrl, Some(jsonData.toString), response.body)
          response
      }
    }
  }

  private def createHeaderCarrier: HeaderCarrier = {
    HeaderCarrier(extraHeaders = Seq("Environment" -> urlHeaderEnvironment),
      authorization = Some(Authorization(urlHeaderAuthorization)))
  }

  private def auditUpdateSubscriptionData(atedReferenceNo: String,
                                          updateData: UpdateEtmpSubscriptionDataRequest,
                                          response: HttpResponse)(implicit hc: HeaderCarrier) {
    val eventType = response.status match {
      case OK => EventTypes.Succeeded
      case _ => EventTypes.Failed
    }
    sendDataEvent(transactionName = "etmpUpdateSubscription",
      detail = Map("txName" -> "etmpUpdateSubscription",
        "atedReferenceNo" -> s"$atedReferenceNo",
        "agentReferenceNumber" -> s"${updateData.agentReferenceNumber}",
        "requestData" -> s"${Json.toJson(updateData)}",
        "responseStatus" -> s"${response.status}",
        "responseBody" -> s"${response.body}",
        "status" -> s"${eventType}"))
  }


  private def auditUpdateRegistrationDetails(atedReferenceNo: String,
                                             safeId: String,
                                             updateData: UpdateRegistrationDetailsRequest,
                                             response: HttpResponse)(implicit hc: HeaderCarrier) {
    val eventType = response.status match {
      case OK => EventTypes.Succeeded
      case _ => EventTypes.Failed
    }
    sendDataEvent(transactionName = "etmpUpdateRegistrationDetails",
      detail = Map("txName" -> "etmpUpdateRegistrationDetails",
        "atedReferenceNo" -> s"$atedReferenceNo",
        "safeId" -> s"$safeId",
        "requestData" -> s"${Json.toJson(updateData)}",
        "responseStatus" -> s"${response.status}",
        "responseBody" -> s"${response.body}",
        "status" -> s"${eventType}"))
  }

}
