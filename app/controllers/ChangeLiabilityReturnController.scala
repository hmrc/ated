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
import models.PropertyDetails
import play.api.Logging
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.{ChangeLiabilityService, NoLiabilityAmountException}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class ChangeLiabilityReturnControllerImpl @Inject()(
                                                     val changeLiabilityService: ChangeLiabilityService,
                                                     val cc: ControllerComponents,
                                                     implicit val crypto: ApplicationCrypto
                                                   ) extends BackendController(cc) with ChangeLiabilityReturnController {
  override implicit val ec: ExecutionContext = cc.executionContext
}

trait ChangeLiabilityReturnController extends BackendController with Logging {
  implicit val ec: ExecutionContext
  val crypto: ApplicationCrypto
  implicit lazy val compositeCrypto: CryptoWithKeysFromConfig = crypto.JsonCrypto
  implicit lazy val format: OFormat[PropertyDetails] = PropertyDetails.formats

  def changeLiabilityService: ChangeLiabilityService

  def convertSubmittedReturnToCachedDraft(accountRef: String, formBundle: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      changeLiabilityResponse <- changeLiabilityService.convertSubmittedReturnToCachedDraft(accountRef, formBundle)
    } yield {
      changeLiabilityResponse match {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
    }
  }

  def calculateDraftChangeLiability(atedRef: String, oldFormBundleNo: String): Action[AnyContent] = Action.async { implicit request =>
    changeLiabilityService.calculateDraftChangeLiability(atedRef, oldFormBundleNo).map {
      case updateResponse @ Some(_)  => Ok(Json.toJson(updateResponse))
      case updateResponse: None.type => BadRequest(Json.toJson(updateResponse))
    } recover {
      case ex: NoLiabilityAmountException =>
        logger.warn("NoLiabilityAmountException: " + ex.message)
        NoContent
    }
  }

  def submitChangeLiabilityReturn(atedRef: String, oldFormBundleNo: String): Action[AnyContent] = Action.async { implicit request =>
    changeLiabilityService.submitChangeLiability(atedRef, oldFormBundleNo) map { response =>
      response.status match {
        case OK => Ok(response.body)
        case _ =>
          logger.warn(s"[ChangeLiabilityReturnController][submitChangeLiabilityReturn] - status = ${response.status} && response.body = ${response.body}")
          InternalServerError(response.body)
      }
    }
  }

  def convertPreviousSubmittedReturnToCachedDraft(accountRef: String, formBundle: String, period: Int): Action[AnyContent] =
    Action.async { implicit request =>
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
