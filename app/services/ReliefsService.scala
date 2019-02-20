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

package services

import connectors.{AuthConnector, EmailConnector, EtmpReturnsConnector}
import models.{ReliefsTaxAvoidance, SubmitEtmpReturnsRequest}
import play.api.http.Status._
import play.api.libs.json.Json
import repository.ReliefsMongoRepository
import utils.ReliefUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

trait ReliefsService extends NotificationService {

  def reliefsCache: ReliefsMongoRepository

  def etmpConnector: EtmpReturnsConnector

  def authConnector: AuthConnector

  def subscriptionDataService: SubscriptionDataService

  def saveDraftReliefs(atedRefNo: String, relief: ReliefsTaxAvoidance)(implicit hc: HeaderCarrier): Future[Seq[ReliefsTaxAvoidance]] = {
    for {
      _ <- reliefsCache.cacheRelief(relief.copy(atedRefNo = atedRefNo))
      draftReliefs <- reliefsCache.fetchReliefs(relief.atedRefNo)
    } yield {
      draftReliefs
    }
  }

  def retrieveDraftReliefs(atedRefNo: String)(implicit hc: HeaderCarrier): Future[Seq[ReliefsTaxAvoidance]] = {
    reliefsCache.fetchReliefs(atedRefNo)
  }

  def submitAndDeleteDraftReliefs(atedRefNo: String, periodKey: Int)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val agentRefNoFuture = authConnector.agentReferenceNo
    for {
      agentRefNo <- agentRefNoFuture
      reliefRequest <- getSubmitReliefsRequest(atedRefNo, periodKey, agentRefNo)
      submitResponse <- reliefRequest match {
        case Some(x) => etmpConnector.submitReturns(atedRefNo, x)
        case _ =>
          val notFound = Json.parse( """{"reason" : "No Reliefs to submit"}""")
          Future.successful(HttpResponse(NOT_FOUND, Some(notFound)))
      }
      subscriptionData <- subscriptionDataService.retrieveSubscriptionData(atedRefNo)
    } yield {
      submitResponse.status match {
        case OK =>
          deleteAllDraftReliefByYear(atedRefNo, periodKey)
          val references = (submitResponse.json \\ "formBundleNumber").map(x => x.as[String]).mkString(",")
          sendMail(subscriptionData.json, "relief_return_submit", Map("reference" -> references))
          submitResponse
        case _ => submitResponse
      }
    }
  }

  def retrieveDraftReliefForPeriodKey(atedRefNo: String, periodKey: Int)(implicit hc: HeaderCarrier) = {
    for {
      draftReliefs <- retrieveDraftReliefs(atedRefNo)
    } yield {
      draftReliefs.find(x => x.periodKey == periodKey)
    }
  }

  private def getSubmitReliefsRequest(atedRefNo: String, periodKey: Int, agentRefNo: Option[String] = None)
                                     (implicit hc: HeaderCarrier): Future[Option[SubmitEtmpReturnsRequest]] = {
    for {
      draftReliefs <- retrieveDraftReliefForPeriodKey(atedRefNo, periodKey)
    } yield {
      ReliefUtils.convertToSubmitReturnsRequest(atedRefNo, draftReliefs, agentRefNo)
    }
  }

  def deleteAllDraftReliefs(atedRefNo: String)(implicit hc: HeaderCarrier) = {
    for {
      _ <- reliefsCache.deleteReliefs(atedRefNo)
      reliefsList <- reliefsCache.fetchReliefs(atedRefNo)
    } yield {
      reliefsList
    }
  }

  def deleteAllDraftReliefByYear(atedRefNo: String, periodKey: Int)(implicit hc: HeaderCarrier) = {
    for {
      _ <- reliefsCache.deleteDraftReliefByYear(atedRefNo, periodKey)
      reliefsList <- reliefsCache.fetchReliefs(atedRefNo)
    } yield {
      reliefsList
    }
  }

}

object ReliefsService extends ReliefsService {
  val reliefsCache = ReliefsMongoRepository()
  val etmpConnector: EtmpReturnsConnector = EtmpReturnsConnector
  val authConnector: AuthConnector = AuthConnector
  val subscriptionDataService: SubscriptionDataService = SubscriptionDataService
  val emailConnector: EmailConnector = EmailConnector
}
