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

package controllers

import javax.inject.{Inject, Singleton}
import models._
import play.api.Logging
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.PropertyDetailsService
import uk.gov.hmrc.crypto.{ApplicationCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class PropertyDetailsControllerImpl @Inject()(val propertyDetailsService: PropertyDetailsService,
                                              val cc: ControllerComponents,
                                              implicit val crypto: ApplicationCrypto) extends BackendController(cc) with PropertyDetailsController {
  override implicit val ec: ExecutionContext = cc.executionContext
}

trait PropertyDetailsController extends BackendController with Logging {
  implicit val ec: ExecutionContext
  val crypto: ApplicationCrypto
  implicit lazy val compositeCrypto: CryptoWithKeysFromConfig = crypto.JsonCrypto
  implicit lazy val format: OFormat[PropertyDetails] = PropertyDetails.formats

  def propertyDetailsService: PropertyDetailsService

  def retrieveDraftPropertyDetails(atedRefNo: String, id: String): Action[AnyContent] = Action.async { _ =>
    propertyDetailsService.retrieveDraftPropertyDetail(atedRefNo, id).map {
      case draftPropertyDetails@Some(_) => Ok(Json.toJson(draftPropertyDetails))
      case draftPropertyDetails         => NotFound(Json.toJson(draftPropertyDetails))
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

  def deleteDraftPropertyDetails(atedRefNo: String, id: String) = Action.async { _ =>
    propertyDetailsService.deleteChargeableDraft(atedRefNo, id).map {
      case Nil => Ok
      case a =>
        logger.warn(
          s"""[PropertyDetailsController][deleteDraftPropertyDetails] - && body = $a""")
        InternalServerError(Json.toJson(a))
    }
  }

}
