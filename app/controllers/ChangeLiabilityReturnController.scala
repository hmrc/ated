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

package controllers

import audit.Auditable
import config.MicroserviceAuditConnector
import models._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import services.{ChangeLiabilityService, PropertyDetailsService}
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

trait ChangeLiabilityReturnController extends BaseController {

  def changeLiabilityService: ChangeLiabilityService

  def convertSubmittedReturnToCachedDraft(accountRef: String, formBundle: String) = Action.async { implicit request =>
    for {
      changeLiabilityResponse <- changeLiabilityService.convertSubmittedReturnToCachedDraft(accountRef, formBundle)
    } yield {
      changeLiabilityResponse match {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
    }
  }

  def calculateDraftChangeLiability(atedRef: String, oldFormBundleNo: String) = Action.async { implicit request =>
    changeLiabilityService.calculateDraftChangeLiability(atedRef, oldFormBundleNo).map { updateResponse =>
      updateResponse match {
        case Some(x) => Ok(Json.toJson(updateResponse))
        case None => BadRequest(Json.toJson(updateResponse))
      }
    }
  }

  def submitChangeLiabilityReturn(atedRef: String, oldFormBundleNo: String) = Action.async { implicit request =>
    changeLiabilityService.submitChangeLiability(atedRef, oldFormBundleNo) map { response =>
      response.status match {
        case OK => Ok(response.body)
        case status =>
          Logger.warn(s"[ChangeLiabilityReturnController][submitChangeLiabilityReturn] - status = ${response.status} && response.body = ${response.body}")
          InternalServerError(response.body)
      }
    }
  }

  def convertPreviousSubmittedReturnToCachedDraft(accountRef: String, formBundle: String, period: Int) = Action.async { implicit request =>
    for {
      changeLiabilityResponse <- changeLiabilityService.convertSubmittedReturnToCachedDraft(accountRef, formBundle, Some(true), Some(period))
    } yield {
      changeLiabilityResponse match {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
    }
  }

}

object ChangeLiabilityReturnController extends ChangeLiabilityReturnController {
  override val changeLiabilityService = ChangeLiabilityService

}
