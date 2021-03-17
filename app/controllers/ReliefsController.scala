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

import audit.Auditable

import javax.inject.{Inject, Named, Singleton}
import models.ReliefsTaxAvoidance
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.ReliefsService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.AuthFunctionality

import scala.concurrent.ExecutionContext

@Singleton
class ReliefsControllerImpl @Inject()(val cc: ControllerComponents,
                                      val reliefsService: ReliefsService,
                                      val authConnector: AuthConnector,
                                      val auditConnector: AuditConnector,
                                      @Named("appName") val appName: String) extends BackendController(cc) with ReliefsController {
  val audit: Audit = new Audit(s"ATED:$appName", auditConnector)
  override implicit val ec: ExecutionContext = cc.executionContext
}

trait ReliefsController extends BackendController with Auditable with AuthFunctionality with Logging {
  implicit val ec: ExecutionContext

  def reliefsService: ReliefsService

  def authConnector: AuthConnector

  def saveDraftReliefs(atedRefNo: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    retrieveAgentRefNumberFor { refNo =>
      withJsonBody[ReliefsTaxAvoidance] { draftRelief =>
        reliefsService.saveDraftReliefs(atedRefNo, draftRelief) map { reliefs =>
					auditSaveDraftReliefs(atedRefNo, draftRelief, refNo)
          Ok(Json.toJson(reliefs))
        }
      }
    }
  }

  def retrieveDraftReliefs(atedRefNo: String, periodKey: Int): Action[AnyContent] = Action.async { _ =>
    reliefsService.retrieveDraftReliefs(atedRefNo).map { reliefs =>
      reliefs.find(_.periodKey == periodKey) match {
        case Some(x) => Ok(Json.toJson(x))
        case None => NotFound(Json.parse( """{}"""))
      }
    }
  }

  def submitDraftReliefs(atedRefNo: String, periodKey: Int): Action[AnyContent] = Action.async { implicit request =>
    reliefsService.submitAndDeleteDraftReliefs(atedRefNo, periodKey).map { responseOfSubmit =>
      responseOfSubmit.status match {
        case OK => Ok(responseOfSubmit.body)
        case NOT_FOUND => NotFound(responseOfSubmit.body)
        case BAD_REQUEST => BadRequest(responseOfSubmit.body)
        case SERVICE_UNAVAILABLE => ServiceUnavailable(responseOfSubmit.body)
        case INTERNAL_SERVER_ERROR | _ =>
          logger.warn(
            s"""[ReliefsController][submitDraftReliefs] - response.status = ${responseOfSubmit.status}
                |&& response.body = ${responseOfSubmit.body}""".stripMargin)
          InternalServerError(responseOfSubmit.body)
      }
    }
  }

  def deleteDraftReliefs(atedRefNo: String): Action[AnyContent] = Action.async { _ =>
    reliefsService.deleteAllDraftReliefs(atedRefNo).map {
      case Nil => Ok
      case a =>
        logger.warn(
          s"""[ReliefsController][deleteDraftReliefs] - && body = $a""")
        InternalServerError(Json.toJson(a))
    }
  }

  def deleteDraftReliefsByYear(atedRefNo: String, periodKey: Int): Action[AnyContent] = Action.async { _ =>
    reliefsService.deleteAllDraftReliefByYear(atedRefNo, periodKey).map {
      case Nil => Ok
      case a =>
        logger.warn(
          s"""[ReliefsController][deleteDraftReliefs] - && body = $a""")
        InternalServerError(Json.toJson(a))
    }
  }


  private def auditSaveDraftReliefs(atedRefNo: String, reliefsTaxAvoid: ReliefsTaxAvoidance, agentRefNo: Option[String])
                                   (implicit hc: HeaderCarrier): Unit = {

    val basicReliefsMap = Map(
      "submittedBy" -> atedRefNo,
      "isAgent" -> agentRefNo.isDefined.toString,
      "accountRef" -> atedRefNo,
      "agentRefNo" -> agentRefNo.toString,
      "reliefs : employeeOccupation" -> reliefsTaxAvoid.reliefs.employeeOccupation.toString,
      "reliefs : farmHouses" -> reliefsTaxAvoid.reliefs.farmHouses.toString,
      "reliefs : lending" -> reliefsTaxAvoid.reliefs.lending.toString,
      "reliefs : openToPublic" -> reliefsTaxAvoid.reliefs.openToPublic.toString,
      "reliefs : propertyDeveloper" -> reliefsTaxAvoid.reliefs.propertyDeveloper.toString,
      "reliefs : propertyTrading" -> reliefsTaxAvoid.reliefs.propertyTrading.toString,
      "reliefs : rentalBusiness" -> reliefsTaxAvoid.reliefs.rentalBusiness.toString,
      "reliefs : socialHousing" -> reliefsTaxAvoid.reliefs.socialHousing.toString,
      "reliefs : equityRelease" -> reliefsTaxAvoid.reliefs.equityRelease.toString
    )

    val taxAvoidanceMap = List(
      reliefsTaxAvoid.taxAvoidance.employeeOccupationScheme.map(scheme => "reliefs : employeeOccupationScheme" -> scheme),
      reliefsTaxAvoid.taxAvoidance.employeeOccupationSchemePromoter.map(scheme => "reliefs : employeeOccupationSchemePromoter" -> scheme),
      reliefsTaxAvoid.taxAvoidance.farmHousesScheme.map(scheme => "reliefs : farmHousesScheme" -> scheme),
      reliefsTaxAvoid.taxAvoidance.farmHousesSchemePromoter.map(scheme => "reliefs : farmHousesSchemePromoter" -> scheme),
      reliefsTaxAvoid.taxAvoidance.lendingScheme.map(scheme => "reliefs : lendingScheme" -> scheme),
      reliefsTaxAvoid.taxAvoidance.lendingSchemePromoter.map(scheme => "reliefs : lendingSchemePromoter" -> scheme),
      reliefsTaxAvoid.taxAvoidance.openToPublicScheme.map(scheme => "reliefs : openToPublicScheme" -> scheme),
      reliefsTaxAvoid.taxAvoidance.openToPublicSchemePromoter.map(scheme => "reliefs : openToPublicSchemePromoter" -> scheme),
      reliefsTaxAvoid.taxAvoidance.propertyDeveloperScheme.map(scheme => "reliefs : propertyDeveloperScheme" -> scheme),
      reliefsTaxAvoid.taxAvoidance.propertyDeveloperSchemePromoter.map(scheme => "reliefs : propertyDeveloperSchemePromoter" -> scheme),
      reliefsTaxAvoid.taxAvoidance.propertyTradingScheme.map(scheme => "reliefs : propertyTradingScheme" -> scheme),
      reliefsTaxAvoid.taxAvoidance.propertyTradingSchemePromoter.map(scheme => "reliefs : propertyTradingSchemePromoter" -> scheme),
      reliefsTaxAvoid.taxAvoidance.rentalBusinessScheme.map(scheme => "reliefs : rentalBusinessScheme" -> scheme),
      reliefsTaxAvoid.taxAvoidance.rentalBusinessSchemePromoter.map(scheme => "reliefs : rentalBusinessSchemePromoter" -> scheme),
      reliefsTaxAvoid.taxAvoidance.socialHousingScheme.map(scheme => "reliefs : socialHousingScheme" -> scheme),
      reliefsTaxAvoid.taxAvoidance.socialHousingSchemePromoter.map(scheme => "reliefs : socialHousingSchemePromoter" -> scheme),
      reliefsTaxAvoid.taxAvoidance.equityReleaseSchemePromoter.map(scheme => "reliefs : equityReleaseScheme" -> scheme),
      reliefsTaxAvoid.taxAvoidance.equityReleaseScheme.map(scheme => "reliefs : equityReleaseSchemePromoter" -> scheme)
    ).flatten.toMap

    sendDataEvent("saveDraftReliefs", detail = basicReliefsMap ++ taxAvoidanceMap)
  }

}
