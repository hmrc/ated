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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.FormBundleService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class FormBundleReturnsControllerImpl @Inject()(
                                                 val cc: ControllerComponents,
                                                 val formBundleService: FormBundleService
                                               ) extends BackendController(cc) with FormBundleReturnsController {
  override implicit val ec: ExecutionContext = cc.executionContext
}

trait FormBundleReturnsController extends BackendController {
  implicit val ec: ExecutionContext
  def formBundleService: FormBundleService

  def getFormBundleReturns(accountRef: String, formBundle: String): Action[AnyContent] = Action.async { implicit request =>
    formBundleService.getFormBundleReturns(accountRef, formBundle) map { responseReceived =>
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
