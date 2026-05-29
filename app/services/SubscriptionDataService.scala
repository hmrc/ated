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

package services

import connectors.{EtmpDetailsConnector, HipDetailsConnector}

import javax.inject.Inject
import models._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.{AuthFunctionality, SessionUtils}

import scala.concurrent.{ExecutionContext, Future}

class SubscriptionDataServiceImpl @Inject()(val etmpConnector: EtmpDetailsConnector,
                                            val hipConnector: HipDetailsConnector,
                                            val authConnector: AuthConnector,
                                            override implicit val sc: ServicesConfig) extends SubscriptionDataService

trait SubscriptionDataService extends AuthFunctionality {

  implicit val sc: ServicesConfig

  def etmpConnector: EtmpDetailsConnector

  def hipConnector: HipDetailsConnector

  def authConnector: AuthConnector

  def retrieveSubscriptionData(atedReferenceNo: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    hipConnector.getSubscriptionData(atedReferenceNo)
  }

  def updateSubscriptionData(atedReferenceNo: String, updateData: UpdateSubscriptionDataRequest)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    retrieveAgentRefNumberFor { agentRefNo =>
      val request = UpdateEtmpSubscriptionDataRequest(
        SessionUtils.getUniqueAckNo,
        updateData.emailConsent,
        updateData.changeIndicators,
        agentRefNo,
        updateData.address
      )

      hipConnector.updateSubscriptionData(atedReferenceNo, request)
    }
  }

  def updateRegistrationDetails(atedReferenceNo: String, safeId: String, updateData: UpdateRegistrationDetailsRequest)
                               (implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val request = updateData.copy(acknowledgementReference = Some(SessionUtils.getUniqueAckNo))
    etmpConnector.updateRegistrationDetails(atedReferenceNo, safeId, request)
  }
}
