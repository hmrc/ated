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

import javax.inject.{Inject, Singleton}
import models._
import org.joda.time.LocalDate
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.ControllerComponents
import services.PropertyDetailsPeriodService
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, CryptoWithKeysFromConfig}

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PropertyDetailsPeriodControllerImpl @Inject()(
                                                     val propertyDetailsService: PropertyDetailsPeriodService,
                                                     val cc: ControllerComponents,
                                                     implicit val crypto: ApplicationCrypto
                                                   ) extends BackendController(cc) with PropertyDetailsPeriodController

trait PropertyDetailsPeriodController extends BackendController {
  val crypto: ApplicationCrypto
  implicit lazy val compositeCrypto: CryptoWithKeysFromConfig = crypto.JsonCrypto
  implicit lazy val format: OFormat[PropertyDetails] = PropertyDetails.formats

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
