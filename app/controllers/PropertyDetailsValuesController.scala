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

package controllers

import javax.inject.{Inject, Named, Singleton}
import models._
import play.api.libs.json.JsValue
import play.api.mvc.{Action, ControllerComponents}
import services.PropertyDetailsValuesService
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PropertyDetailsValuesControllerImpl @Inject()(val cc: ControllerComponents,
                                                    val propertyDetailsService: PropertyDetailsValuesService,
                                                    val auditConnector: AuditConnector,
                                                    @Named("appName") val appName: String
                                                   ) extends BackendController(cc) with PropertyDetailsValuesController {
  val audit: Audit = new Audit(s"ATED:$appName", auditConnector)
}

trait PropertyDetailsValuesController extends BackendController {

  def propertyDetailsService: PropertyDetailsValuesService

  def saveDraftHasValueChanged(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[Boolean] { overLimit =>
        propertyDetailsService.cacheDraftHasValueChanged(atedRefNo, id, overLimit).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(_) => Ok("")
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
            case Some(_) => Ok("")
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
            case Some(_) => Ok("")
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
            case Some(_) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }

  def saveDraftPropertyDetailsIsNewBuild(atedRefNo: String, id: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsIsNewBuild] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsIsNewBuild(atedRefNo, id, draftPropertyDetails).map {
          case Some(_) => Ok("")
          case None => BadRequest("Invalid Request")
        }
      }
  }

  def saveDraftPropertyDetailsNewBuildDates(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsNewBuildDates] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsNewBuildDates(atedRefNo, id, draftPropertyDetails).map {
          case Some(_) => Ok("")
          case None => BadRequest("Invalid Request")
        }
      }
  }

  def saveDraftPropertyDetailsNewBuildValue(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsNewBuildValue] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsNewBuildValue(atedRefNo, id, draftPropertyDetails).map {
          case Some(_) => Ok("")
          case None => BadRequest("Invalid Request")
        }
      }
  }

  def saveDraftPropertyDetailsValueAcquired(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsValueOnAcquisition] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsValueAcquired(atedRefNo, id, draftPropertyDetails).map {
          case Some(_) => Ok("")
          case None => BadRequest("Invalid Request")
        }
      }
  }

  def saveDraftPropertyDetailsDatesAcquired(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsDateOfAcquisition] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsDatesAcquired(atedRefNo, id, draftPropertyDetails).map {
          case Some(_) => Ok("")
          case None => BadRequest("Invalid Request")
        }
      }
  }

  def saveDraftPropertyDetailsProfessionallyValued(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsProfessionallyValued] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(_) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }
}
