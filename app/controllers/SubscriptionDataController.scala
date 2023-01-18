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

package controllers

import javax.inject.{Inject, Singleton}
import models.{UpdateRegistrationDetailsRequest, UpdateSubscriptionDataRequest}
import play.api.mvc.ControllerComponents
import services.SubscriptionDataService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class SubscriptionDataControllerImpl @Inject()(val cc: ControllerComponents,
                                               val subscriptionDataService: SubscriptionDataService
                                              ) extends BackendController(cc) with SubscriptionDataController {
  override implicit val ec: ExecutionContext = cc.executionContext
}

@Singleton
class AgentRetrieveClientSubscriptionDataController @Inject()(val cc: ControllerComponents,
                                                              val subscriptionDataService: SubscriptionDataService
                                                             ) extends BackendController(cc) with SubscriptionDataController {
  override implicit val ec: ExecutionContext = cc.executionContext
}

trait SubscriptionDataController extends BackendController {
  implicit val ec: ExecutionContext

  def subscriptionDataService: SubscriptionDataService

  def retrieveSubscriptionData(accountRef: String) = Action.async { implicit request =>
    subscriptionDataService.retrieveSubscriptionData(accountRef) map { responseReceived =>
      responseReceived.status match {
        case OK => Ok(responseReceived.body)
        case NOT_FOUND => NotFound(responseReceived.body)
        case BAD_REQUEST => BadRequest(responseReceived.body)
        case SERVICE_UNAVAILABLE => ServiceUnavailable(responseReceived.body)
        case _ => InternalServerError(responseReceived.body)
      }
    }
  }

  def updateSubscriptionData(accountRef: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[UpdateSubscriptionDataRequest] { updatedData =>
      subscriptionDataService.updateSubscriptionData(accountRef, updatedData) map { responseReceived =>
        responseReceived.status match {
          case OK => Ok(responseReceived.body)
          case NOT_FOUND => NotFound(responseReceived.body)
          case BAD_REQUEST => BadRequest(responseReceived.body)
          case SERVICE_UNAVAILABLE => ServiceUnavailable(responseReceived.body)
          case _ => InternalServerError(responseReceived.body)
        }
      }
    }
  }

  def retrieveSubscriptionDataByAgent(accountRef: String, agentCode: String) = Action.async { implicit request =>
    subscriptionDataService.retrieveSubscriptionData(accountRef) map { responseReceived =>
      responseReceived.status match {
        case OK => Ok(responseReceived.body)
        case NOT_FOUND => NotFound(responseReceived.body)
        case BAD_REQUEST => BadRequest(responseReceived.body)
        case SERVICE_UNAVAILABLE => ServiceUnavailable(responseReceived.body)
        case _ => InternalServerError(responseReceived.body)
      }
    }
  }

  def updateRegistrationDetails(accountRef: String, safeId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[UpdateRegistrationDetailsRequest] { updatedData =>
      subscriptionDataService.updateRegistrationDetails(accountRef, safeId, updatedData) map { responseReceived =>
        responseReceived.status match {
          case OK => Ok(responseReceived.body)
          case NOT_FOUND => NotFound(responseReceived.body)
          case BAD_REQUEST => BadRequest(responseReceived.body)
          case SERVICE_UNAVAILABLE => ServiceUnavailable(responseReceived.body)
          case _ => InternalServerError(responseReceived.body)
        }
      }
    }
  }

}
