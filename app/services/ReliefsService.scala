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

package services

import connectors.{EmailConnector, EtmpReturnsConnector}
import javax.inject.Inject
import models.{ReliefsTaxAvoidance, SubmitEtmpReturnsRequest}
import play.api.http.Status._
import play.api.libs.json.Json
import repository.{ReliefsMongoRepository, ReliefsMongoWrapper}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.{AuthFunctionality, ReliefUtils}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReliefsServiceImpl @Inject()(val etmpConnector: EtmpReturnsConnector,
                                   val authConnector: AuthConnector,
                                   val subscriptionDataService: SubscriptionDataService,
                                   val emailConnector: EmailConnector,
                                   val reliefRepo: ReliefsMongoWrapper) extends ReliefsService {
 lazy val reliefsCache: ReliefsMongoRepository = reliefRepo()
}

trait ReliefsService extends NotificationService with AuthFunctionality {

  def reliefsCache: ReliefsMongoRepository
  def etmpConnector: EtmpReturnsConnector
  def authConnector: AuthConnector
  def subscriptionDataService: SubscriptionDataService

  def saveDraftReliefs(atedRefNo: String, relief: ReliefsTaxAvoidance): Future[Seq[ReliefsTaxAvoidance]] = {
    for {
      _ <- reliefsCache.cacheRelief(relief.copy(atedRefNo = atedRefNo))
      draftReliefs <- reliefsCache.fetchReliefs(relief.atedRefNo)
    } yield {
      draftReliefs
    }
  }

  def retrieveDraftReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]] = {
    reliefsCache.fetchReliefs(atedRefNo)
  }

  def submitAndDeleteDraftReliefs(atedRefNo: String, periodKey: Int)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    retrieveAgentRefNumberFor { agentRefNo =>
      for {
        reliefRequest <- getSubmitReliefsRequest(atedRefNo, periodKey, agentRefNo)
        submitResponse <- reliefRequest match {
          case Some(x) => etmpConnector.submitReturns(atedRefNo, x)
          case _ =>
            val notFound = Json.parse("""{"reason" : "No Reliefs to submit"}""")
            Future.successful(HttpResponse(NOT_FOUND, notFound, Map.empty[String, Seq[String]]))
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
  }

  def retrieveDraftReliefForPeriodKey(atedRefNo: String, periodKey: Int): Future[Option[ReliefsTaxAvoidance]] = {
    for {
      draftReliefs <- retrieveDraftReliefs(atedRefNo)
    } yield {
      draftReliefs.find(x => x.periodKey == periodKey)
    }
  }

  private def getSubmitReliefsRequest(atedRefNo: String, periodKey: Int, agentRefNo: Option[String]): Future[Option[SubmitEtmpReturnsRequest]] = {
    for {
      draftReliefs <- retrieveDraftReliefForPeriodKey(atedRefNo, periodKey)
    } yield {
      ReliefUtils.convertToSubmitReturnsRequest(draftReliefs, agentRefNo)
    }
  }

  def deleteAllDraftReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]] = {
    for {
      _ <- reliefsCache.deleteReliefs(atedRefNo)
      reliefsList <- reliefsCache.fetchReliefs(atedRefNo)
    } yield {
      reliefsList
    }
  }

  def deleteAllDraftReliefByYear(atedRefNo: String, periodKey: Int): Future[Seq[ReliefsTaxAvoidance]] = {
    for {
      _ <- reliefsCache.deleteDraftReliefByYear(atedRefNo, periodKey)
      reliefsList <- reliefsCache.fetchReliefs(atedRefNo)
    } yield {
      reliefsList
    }
  }

}
