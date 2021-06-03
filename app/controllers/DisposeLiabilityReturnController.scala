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
import models.{BankDetails, DisposeLiability, DisposeLiabilityReturn}
import play.api.Logging
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.DisposeLiabilityReturnService
import uk.gov.hmrc.crypto.{ApplicationCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class DisposeLiabilityReturnControllerImpl @Inject()(
                                                    val disposeLiabilityReturnService: DisposeLiabilityReturnService,
                                                    val cc: ControllerComponents,
                                                    val crypto: ApplicationCrypto
                                                    ) extends BackendController(cc) with DisposeLiabilityReturnController {
  override implicit val ec: ExecutionContext = cc.executionContext
}

trait DisposeLiabilityReturnController extends BackendController with Logging {
  implicit val ec: ExecutionContext
  val crypto: ApplicationCrypto
  implicit lazy val compositeCrypto: CryptoWithKeysFromConfig = crypto.JsonCrypto
  implicit lazy val format: OFormat[DisposeLiabilityReturn] = DisposeLiabilityReturn.formats

  def disposeLiabilityReturnService: DisposeLiabilityReturnService

  def retrieveAndCacheDisposeLiabilityReturn(accountRef: String, formBundle: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      disposeLiabilityResponse <- disposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(accountRef, formBundle)
    } yield {
      disposeLiabilityResponse match {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
    }
  }

  def updateDisposalDate(atedRef: String, oldFormBundleNo: String): Action[JsValue] = Action.async(parse.json) {
    implicit request => withJsonBody[DisposeLiability] {
      updatedDate => disposeLiabilityReturnService.updateDraftDisposeLiabilityReturnDate(atedRef, oldFormBundleNo, updatedDate) map {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
    }
  }

  def updateHasBankDetails(atedRef: String, oldFormBundleNo: String): Action[JsValue] = Action.async(parse.json) {
    implicit request => withJsonBody[Boolean] {
      updatedValue => disposeLiabilityReturnService.updateDraftDisposeHasBankDetails(atedRef, oldFormBundleNo, updatedValue) map {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
    }
  }

  def updateBankDetails(atedRef: String, oldFormBundleNo: String): Action[JsValue] = Action.async(parse.json) {
    implicit request => withJsonBody[BankDetails] {
      updatedValue => disposeLiabilityReturnService.updateDraftDisposeBankDetails(atedRef, oldFormBundleNo, updatedValue) map {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
    }
  }

  def calculateDraftDisposal(atedRef: String, oldFormBundleNo: String): Action[AnyContent] = Action.async {
    implicit request =>
      disposeLiabilityReturnService.calculateDraftDispose(atedRef, oldFormBundleNo) map {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse("""{}"""))
      }
  }

  def submitDisposeLiabilityReturn(atedRef: String, oldFormBundleNo: String): Action[AnyContent] = Action.async { implicit request =>
    disposeLiabilityReturnService.submitDisposeLiability(atedRef, oldFormBundleNo) map { response =>
      response.status match {
        case OK => Ok(response.body)
        case status =>
          logger.warn(s"[DisposeLiabilityReturnController][submitDisposeLiabilityReturn] - status = ${response.status} && response.body = ${response.body}")
          InternalServerError(response.body)
      }
    }
  }

}
