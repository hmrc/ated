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
import models.UpdateEtmpSubscriptionDataRequest
import play.api.Logging
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import utils.HipUtilities

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.{Base64, UUID}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HipDetailsConnectorImpl @Inject()(val servicesConfig: ServicesConfig,
                                        val http: HttpClientV2,
                                        val auditConnector: AuditConnector,
                                        val metrics: ServiceMetrics)(implicit val ec: ExecutionContext) extends HipDetailsConnector {

  val serviceUrl: String = servicesConfig.baseUrl("hip")

  override val clientId: String = servicesConfig.getConfString("hip.clientId", "")
  override val clientSecret: String = servicesConfig.getConfString("hip.clientSecret", "")
  override val originatingSystem: String = servicesConfig.getConfString("hip.originatingSystem", "ATED")

  val audit: Audit = new Audit("ated", auditConnector)
  val atedBaseURI: String = "etmp/RESTAdapter/ated"
  val retrieveSubscriptionData: String = "subscription"
  val saveSubscriptionData: String = "subscription"
}

trait HipDetailsConnector extends Auditable with Logging {
  implicit val ec: ExecutionContext
  def serviceUrl: String
  def http: HttpClientV2
  def metrics: ServiceMetrics
  def clientId: String
  def clientSecret: String
  def authorizationToken: String = Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes("UTF-8"))

  val atedBaseURI: String
  val retrieveSubscriptionData: String
  val saveSubscriptionData: String
  val transmittingSystem: String = "HIP"
  val originatingSystem: String

  def headers: Seq[(String, String)] = Seq(
    "correlationid" -> UUID.randomUUID().toString,
    "X-Originating-System" -> originatingSystem,
    "X-Receipt-Date" -> retrieveCurrentTime,
    "X-Transmitting-System" -> transmittingSystem,
    "Authorization" -> s"Basic $authorizationToken"
  )

  private def retrieveCurrentTime: String = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    formatter.format(ZonedDateTime.now(ZoneId.of("UTC")))
  }

  def getSubscriptionData(atedReferenceNo: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val getUrl = s"""$serviceUrl/$atedBaseURI/$retrieveSubscriptionData/$atedReferenceNo"""

    val timerContext = metrics.startTimer(MetricsEnum.EtmpGetSubscriptionData)
    http.get(url"$getUrl").setHeader(headers: _*).execute[HttpResponse].map{ response =>
      timerContext.stop()
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpGetSubscriptionData)
          val stripSuccessJson = HipUtilities.stripSuccessWrapper(response.json)
          HttpResponse(
            status = Status.OK,
            body = Json.stringify(stripSuccessJson),
            headers = response.headers
          )
        case NOT_FOUND =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpGetSubscriptionData)
          response
        case UNPROCESSABLE_ENTITY =>
          HipUtilities.extractHipErrorCode(response.body) match {
            case Some(("003", text)) =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpGetSubscriptionData)
              logger.warn(s"[HipDetailsConnector][getSubscriptionData] - $text")
              doHeaderEvent("getSubscriptionDataFailedHeaders", response.headers)
              doFailedAudit("getSubscriptionDataFailed", getUrl, None, response.body)
              HttpResponse(
                status = Status.NOT_FOUND,
                body = response.body,
                headers = response.headers
              )

            case Some(("004", text)) =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpGetSubscriptionData)
              logger.warn(s"[HipDetailsConnector][getSubscriptionData] - $text")
              doHeaderEvent("getSubscriptionDataFailedHeaders", response.headers)
              doFailedAudit("getSubscriptionDataFailed", getUrl, None, response.body)
              HttpResponse(
                status = Status.BAD_REQUEST,
                body = response.body,
                headers = response.headers
              )

            case Some(("005", text)) =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpGetSubscriptionData)
              logger.warn(s"[HipDetailsConnector][getSubscriptionData] - $text")
              doHeaderEvent("getSubscriptionDataFailedHeaders", response.headers)
              doFailedAudit("getSubscriptionDataFailed", getUrl, None, response.body)
              HttpResponse(
                status = Status.UNAUTHORIZED,
                body = response.body,
                headers = response.headers
              )

            case status =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpGetSubscriptionData)
              logger.warn(s"[HipDetailsConnector][getSubscriptionData - Unsuccessful return of data. Status: $status")
              doHeaderEvent("getSubscriptionDataFailedHeaders", response.headers)
              doFailedAudit("getSubscriptionDataFailed", getUrl, None, response.body)
              HttpResponse(
                status = Status.INTERNAL_SERVER_ERROR,
                body = response.body,
                headers = response.headers
              )
          }
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpGetSubscriptionData)
          logger.warn(s"[HipDetailsConnector][getSubscriptionData] - status: $status")
          doHeaderEvent("getSubscriptionDataFailedHeaders", response.headers)
          doFailedAudit("getSubscriptionDataFailed", getUrl, None, response.body)
          response
      }
    }
  }

  def updateSubscriptionData(atedReferenceNo: String, updatedData: UpdateEtmpSubscriptionDataRequest)
                            (implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val putUrl = s"""$serviceUrl/$atedBaseURI/$saveSubscriptionData/$atedReferenceNo"""

    val timerContext = metrics.startTimer(MetricsEnum.EtmpUpdateSubscriptionData)
    val jsonData = Json.toJson(updatedData)
    val withAcknowledgementReferenceRemovedJson = HipUtilities.removeAcknowledgementReferenceField(jsonData)

    http.put(url"$putUrl").withBody(withAcknowledgementReferenceRemovedJson).setHeader(headers: _*).execute[HttpResponse].map{ response =>
      timerContext.stop()

      auditUpdateSubscriptionData(atedReferenceNo, updatedData, response)
      response.status match {
        case OK =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpUpdateSubscriptionData)
          val stripSuccessJson = HipUtilities.stripSuccessWrapper(response.json)
          HttpResponse(
            status = Status.OK,
            body = Json.stringify(stripSuccessJson),
            headers = response.headers
          )
        case NOT_FOUND =>
          metrics.incrementSuccessCounter(MetricsEnum.EtmpUpdateSubscriptionData)
          response
        case UNPROCESSABLE_ENTITY =>
          HipUtilities.extractHipErrorCode(response.body) match {
            case Some(("003", text))  =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpUpdateSubscriptionData)
              logger.warn(s"[HipDetailsConnector][updateSubscriptionData] - $text")
              doHeaderEvent("updateSubscriptionDataFailedHeaders", response.headers)
              doFailedAudit("updateSubscriptionDataailed", putUrl, None, response.body)
              HttpResponse(
                status = Status.NOT_FOUND,
                body = response.body,
                headers = response.headers
              )

            case Some(("004", text)) =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpUpdateSubscriptionData)
              logger.warn(s"[HipDetailsConnector][updateSubscriptionData] - $text")
              doHeaderEvent("updateSubscriptionDataFailedHeaders", response.headers)
              doFailedAudit("updateSubscriptionDataFailed", putUrl, None, response.body)
              HttpResponse(
                status = Status.BAD_REQUEST,
                body = response.body,
                headers = response.headers
              )

            case Some(("005", text)) =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpUpdateSubscriptionData)
              logger.warn(s"[HipDetailsConnector][updateSubscriptionData] - $text")
              doHeaderEvent("updateSubscriptionDataFailedHeaders", response.headers)
              doFailedAudit("updateSubscriptionDataFailed", putUrl, None, response.body)
              HttpResponse(
                status = Status.UNAUTHORIZED,
                body = response.body,
                headers = response.headers
              )

            case Some(("006", text)) =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpUpdateSubscriptionData)
              logger.warn(s"[HipDetailsConnector][updateSubscriptionData] - $text")
              doHeaderEvent("updateSubscriptionDataFailedHeaders", response.headers)
              doFailedAudit("updateSubscriptionDataFailed", putUrl, None, response.body)
              HttpResponse(
                status = Status.BAD_REQUEST,
                body = response.body,
                headers = response.headers
              )

            case status =>
              metrics.incrementFailedCounter(MetricsEnum.EtmpUpdateSubscriptionData)
              logger.warn(s"[HipDetailsConnector][updateSubscriptionData] - Unsuccessful return of data. Status: $status")
              doHeaderEvent("updateSubscriptionDataFailedHeaders", response.headers)
              doFailedAudit("updateSubscriptionDataFailed", putUrl, None, response.body)
              HttpResponse(
                status = Status.INTERNAL_SERVER_ERROR,
                body = response.body,
                headers = response.headers
              )
          }
        case status =>
          metrics.incrementFailedCounter(MetricsEnum.EtmpUpdateSubscriptionData)
          logger.warn(s"[HipDetailsConnector][updateSubscriptionData] - status: $status")
          doHeaderEvent("updateSubscriptionDataFailedHeaders", response.headers)
          doFailedAudit("updateSubscriptionDataFailed", putUrl, Some(jsonData.toString), response.body)
          response
      }
    }
  }

  private def auditUpdateSubscriptionData(atedReferenceNo: String,
                                          updateData: UpdateEtmpSubscriptionDataRequest,
                                          response: HttpResponse)(implicit hc: HeaderCarrier): Unit = {
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
        "status" -> s"$eventType"))
  }
}