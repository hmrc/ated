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
import org.joda.time.LocalDate
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import services.{PropertyDetailsPeriodService}
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

trait PropertyDetailsPeriodController extends BaseController {

  def propertyDetailsService: PropertyDetailsPeriodService

  def saveDraftFullTaxPeriod(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[IsFullTaxPeriod] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftFullTaxPeriod(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }
  def saveDraftInRelief(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsInRelief] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftInRelief(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }

  def saveDraftDatesLiable(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsDatesLiable] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftDatesLiable(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }

  def addDraftDatesLiable(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsDatesLiable] { draftPropertyDetails =>
        propertyDetailsService.addDraftDatesLiable(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }

  def addDraftDatesInRelief(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsDatesInRelief] { draftPropertyDetails =>
        propertyDetailsService.addDraftDatesInRelief(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
          }
        }
      }
  }

  def deleteDraftPeriod(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[LocalDate] { dateToDelete =>
        propertyDetailsService.deleteDraftPeriod(atedRefNo, id, dateToDelete).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok(Json.toJson(updatedDraftPropertyDetails))
            case None => BadRequest(Json.toJson(updatedDraftPropertyDetails))
          }
        }
      }
  }

}

object PropertyDetailsPeriodController extends PropertyDetailsPeriodController {

  val propertyDetailsService = PropertyDetailsPeriodService

}
