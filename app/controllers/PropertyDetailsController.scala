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
import services.PropertyDetailsService
import uk.gov.hmrc.play.audit.model.{Audit, EventTypes}
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

trait PropertyDetailsController extends BaseController {

  def propertyDetailsService: PropertyDetailsService

  def retrieveDraftPropertyDetails(atedRefNo: String, id: String) = Action.async { implicit request =>
    propertyDetailsService.retrieveDraftPropertyDetail(atedRefNo, id).map { draftPropertyDetails =>
      draftPropertyDetails match {
        case Some(x) => Ok(Json.toJson(draftPropertyDetails))
        case _ => NotFound(Json.toJson(draftPropertyDetails))
      }
    }
  }

  def createDraftPropertyDetails(atedRefNo: String, periodKey: Int) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsAddress] { draftPropertyDetails =>
        propertyDetailsService.createDraftPropertyDetails(atedRefNo, periodKey, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok(Json.toJson(x))
            case None => BadRequest(Json.toJson(updatedDraftPropertyDetails))
          }
        }
      }
  }

  def saveDraftPropertyDetailsAddress(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsAddress] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsAddress(atedRefNo, id, draftPropertyDetails).map { updatedDraftPropertyDetails =>
          updatedDraftPropertyDetails match {
            case Some(x) => Ok(Json.parse("""{}"""))
            case None => BadRequest(Json.toJson(updatedDraftPropertyDetails))
          }
        }
      }
  }

  def saveDraftPropertyDetailsTitle(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsTitle] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftPropertyDetailsTitle(atedRefNo, id, draftPropertyDetails).map {
            case Some(x) => Ok(Json.parse("""{}"""))
            case None => BadRequest("Invalid Request")
        }
      }
  }

  def saveDraftTaxAvoidance(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsTaxAvoidance] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftTaxAvoidance(atedRefNo, id, draftPropertyDetails).map {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
        }
      }
  }

  def saveDraftSupportingInfo(atedRefNo: String, id: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PropertyDetailsSupportingInfo] { draftPropertyDetails =>
        propertyDetailsService.cacheDraftSupportingInfo(atedRefNo, id, draftPropertyDetails).map {
            case Some(x) => Ok("")
            case None => BadRequest("Invalid Request")
        }
      }
  }

  def updateDraftHasBankDetails(atedRef: String, oldFormBundleNo: String) = Action.async(parse.json) {
    implicit request => withJsonBody[Boolean] {
      updatedValue => propertyDetailsService.cacheDraftHasBankDetails(atedRef, oldFormBundleNo, updatedValue) map {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
    }
  }

  def updateDraftBankDetails(atedRef: String, oldFormBundleNo: String) = Action.async(parse.json) {
    implicit request => withJsonBody[BankDetails] {
      updatedValue => propertyDetailsService.cacheDraftBankDetails(atedRef, oldFormBundleNo, updatedValue) map {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
    }
  }

  def calculateDraftPropertyDetails(atedRefNo: String, id: String) = Action.async { implicit request =>
    propertyDetailsService.calculateDraftPropertyDetails(atedRefNo, id).map { updateResponse =>
      updateResponse match {
        case Some(x) => Ok(Json.toJson(updateResponse))
        case None => BadRequest(Json.toJson(updateResponse))
      }
    }
  }

  def submitDraftPropertyDetails(atedRefNo: String, id: String) = Action.async { implicit request =>
    propertyDetailsService.submitDraftPropertyDetail(atedRefNo, id).map { updateResponse =>
      updateResponse.status match {
        case OK => Ok(updateResponse.body)
        case BAD_REQUEST => BadRequest(updateResponse.body)
        case NOT_FOUND => NotFound(updateResponse.body)
        case SERVICE_UNAVAILABLE => ServiceUnavailable(updateResponse.body)
        case _ =>  InternalServerError(updateResponse.body)
      }
    }
  }

  def deleteDraftPropertyDetails(atedRefNo: String, id: String) = Action.async { implicit request =>
    propertyDetailsService.deleteChargeableDraft(atedRefNo, id).map {
      case Nil => Ok
      case a =>
        Logger.warn(
          s"""[PropertyDetailsController][deleteDraftPropertyDetails] - && body = $a""")
        InternalServerError(Json.toJson(a))
    }
  }

}

object PropertyDetailsController extends PropertyDetailsController {

  val propertyDetailsService = PropertyDetailsService


}
