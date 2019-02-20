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
import services.PropertyDetailsValuesService
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

trait PropertyDetailsValuesController extends BaseController  {

  def propertyDetailsService: PropertyDetailsValuesService

  def saveDraftHasValueChanged(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[Boolean] { overLimit =>
        propertyDetailsService.cacheDraftHasValueChanged(atedRefNo, id, overLimit).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }


  def saveDraftPropertyDetailsAcquisition(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[Boolean] { overLimit =>
        propertyDetailsService.cacheDraftPropertyDetailsAcquisition(atedRefNo, id, overLimit).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }

  def saveDraftPropertyDetailsRevalued(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsRevalued] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsRevalued(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }

  def saveDraftPropertyDetailsOwnedBefore(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsOwnedBefore] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }

  def saveDraftPropertyDetailsNewBuild(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsNewBuild] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsNewBuild(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }


  def saveDraftPropertyDetailsProfessionallyValued(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsProfessionallyValued] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }
}

object PropertyDetailsValuesController extends PropertyDetailsValuesController {

  val propertyDetailsService = PropertyDetailsValuesService

  val audit: Audit = new Audit(s"ATED:${AppName.appName}", MicroserviceAuditConnector)

  val appName: String = AppName.appName

}
