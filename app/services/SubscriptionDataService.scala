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

import connectors.{AuthConnector, EtmpDetailsConnector}
import models._
import utils.SessionUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

trait SubscriptionDataService {

  def etmpConnector: EtmpDetailsConnector

  def authConnector: AuthConnector

  def retrieveSubscriptionData(atedReferenceNo: String): Future[HttpResponse] = {
    etmpConnector.getSubscriptionData(atedReferenceNo)
  }

  def updateSubscriptionData(atedReferenceNo: String, updateData: UpdateSubscriptionDataRequest)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    authConnector.agentReferenceNo flatMap {
      agentRefNo =>
        val request = UpdateEtmpSubscriptionDataRequest(SessionUtils.getUniqueAckNo, updateData.emailConsent, updateData.changeIndicators, agentRefNo, updateData.address)
        etmpConnector.updateSubscriptionData(atedReferenceNo, request)
    }
  }

  def updateRegistrationDetails(atedReferenceNo: String, safeId: String, updateData: UpdateRegistrationDetailsRequest): Future[HttpResponse] = {
    val request = updateData.copy(acknowledgementReference = Some(SessionUtils.getUniqueAckNo))
    etmpConnector.updateRegistrationDetails(atedReferenceNo, safeId, request)
  }

}

object SubscriptionDataService extends SubscriptionDataService {
  def etmpConnector: EtmpDetailsConnector = EtmpDetailsConnector
  val authConnector: AuthConnector = AuthConnector
}
