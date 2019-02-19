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

package services

import connectors.{AuthConnector, EmailConnector, EtmpReturnsConnector}
import models._
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import repository.PropertyDetailsMongoRepository
import utils._
import utils.AtedUtils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse, InternalServerException }

trait ChangeLiabilityService extends PropertyDetailsBaseService with ReliefConstants with NotificationService {

  def subscriptionDataService: SubscriptionDataService

  def convertSubmittedReturnToCachedDraft(atedRefNo: String, oldFormBundleNo: String, fromSelectedPrevReturn: Option[Boolean] = None, period: Option[Int] = None)(implicit hc: HeaderCarrier) = {
    for {
      cachedData <- retrieveDraftPropertyDetail(atedRefNo, oldFormBundleNo)
      cachedChangeLiability <- {
        cachedData match {
          case Some(x) if fromSelectedPrevReturn.isEmpty | fromSelectedPrevReturn.contains(false) => Future.successful(Option(x))
          case _ =>
            etmpConnector.getFormBundleReturns(atedRefNo, oldFormBundleNo.toString) map {
              response => response.status match {
                case OK =>
                  val liabilityReturn = response.json.as[FormBundleReturn]
                  val address = ChangeLiabilityUtils.generateAddressFromLiabilityReturn(liabilityReturn)
                  val title = ChangeLiabilityUtils.generateTitleFromLiabilityReturn(liabilityReturn)
                  val periodData = ChangeLiabilityUtils.generatePeriodFromLiabilityReturn(liabilityReturn)
                  val changeLiability = PropertyDetails(atedRefNo,
                    id = fromSelectedPrevReturn match {
                      case Some(true) => createPropertyKey
                      case _ => oldFormBundleNo
                    },
                    periodKey = fromSelectedPrevReturn match {
                      case Some(true) => period.get
                      case _ => liabilityReturn.periodKey.trim.toInt
                    },
                    address,
                    title,
                    period = fromSelectedPrevReturn match {
                      case Some(true) => None
                      case _ => Some(periodData)
                    },
                    value = Some(PropertyDetailsValue(isValuedByAgent = Some(liabilityReturn.professionalValuation),
                      isPropertyRevalued = Some(false),
                      partAcqDispDate = liabilityReturn.dateOfAcquisition,
                      revaluedValue = liabilityReturn.valueAtAcquisition)),
                    formBundleReturn = Some(liabilityReturn)
                  )
                  retrieveDraftPropertyDetails(atedRefNo) map {
                    list =>
                      val updatedList = list :+ changeLiability
                      updatedList.map(updateProp => propertyDetailsCache.cachePropertyDetails(updateProp))
                  }
                  Some(changeLiability)
                case status => None
              }
            }
        }
      }
    } yield {
      cachedChangeLiability
    }
  }

  def getAmountDueOrRefund(atedRefNo: String, id: String, propertyDetails: PropertyDetails, agentRefNo: Option[String] = None)
                          (implicit hc: HeaderCarrier): Future[(Option[BigDecimal], Option[BigDecimal])] = {

    def getLiabilityAmount(data: JsValue): (Option[BigDecimal], Option[BigDecimal]) = {
      val response = data.as[EditLiabilityReturnsResponseModel]
      val returnFound = response.liabilityReturnResponse.find(_.oldFormBundleNumber == id)
      (returnFound.map(_.liabilityAmount), returnFound.map(_.amountDueOrRefund))
    }

    ChangeLiabilityUtils.createPreCalculationRequest(propertyDetails, agentRefNo) match {
      case Some(requestModel) => etmpConnector.submitEditedLiabilityReturns(atedRefNo, requestModel) map {
        response =>
          response.status match {
          case OK => getLiabilityAmount(response.json)
          case status => throw new InternalServerException("[ChangeLiabilityService][getAmountDueOrRefund] No Liability Amount Found")
        }
      }
      case None => throw new InternalServerException("[ChangeLiabilityService][getAmountDueOrRefund] Invalid Data for the request")
    }
  }

  def calculateDraftChangeLiability(atedRefNo: String, id: String)
                                   (implicit hc: HeaderCarrier): Future[Option[PropertyDetails]] = {

    val agentRefNoFuture = authConnector.agentReferenceNo

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val propertyDetailsOpt = propertyDetailsList.find(_.id == id)
      val liabilityAmountOpt = propertyDetailsOpt.flatMap(_.calculated.flatMap(_.liabilityAmount))
      (propertyDetailsOpt, liabilityAmountOpt) match {
        case (Some(foundPropertyDetails), None) =>
          val calculatedValues = ChangeLiabilityUtils.changeLiabilityCalculated(foundPropertyDetails)
          val propertyDetailsWithCalculated = foundPropertyDetails.copy(calculated = Some(calculatedValues))
          for {
            agentRefNo <- agentRefNoFuture
            preCalcAmounts <- getAmountDueOrRefund(atedRefNo, id, propertyDetailsWithCalculated, agentRefNo)
          } yield {
            val updateCalculatedWithLiability = propertyDetailsWithCalculated.calculated.map(_.copy(liabilityAmount = preCalcAmounts._1, amountDueOrRefund = preCalcAmounts._2))
            Some(propertyDetailsWithCalculated.copy(calculated = updateCalculatedWithLiability))
          }

        case _ => Future.successful(propertyDetailsOpt)
      }
    }
    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def emailTemplate(data: JsValue, oldFormBundleNumber: String): String = {
    val response = data.as[EditLiabilityReturnsResponseModel]
    val returnFound = response.liabilityReturnResponse.find(_.oldFormBundleNumber == oldFormBundleNumber)
    returnFound.map(_.amountDueOrRefund) match {
      case Some(x) if x > 0 => "further_return_submit"
      case Some(x) if x < 0 => "amended_return_submit"
      case _ => "change_details_return_submit"
    }
  }

  def submitChangeLiability(atedRefNo: String, oldFormBundleNo: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val agentRefNoFuture = authConnector.agentReferenceNo
    val changeLiabilityReturnListFuture = retrieveDraftPropertyDetails(atedRefNo)
    for {
      changeLiabilityReturnList <- changeLiabilityReturnListFuture
      agentRefNo <- agentRefNoFuture
      submitStatus: HttpResponse <- {
        changeLiabilityReturnList.find(_.id == oldFormBundleNo) match {
          case Some(x) =>
            val editLiabilityRequest = ChangeLiabilityUtils.createPostRequest(x, agentRefNo)
            editLiabilityRequest match {
              case Some(a) => etmpConnector.submitEditedLiabilityReturns(atedRefNo, a)
              case None => Future.successful(HttpResponse(NOT_FOUND, None))
            }
          case None => Future.successful(HttpResponse(NOT_FOUND, None))
        }
      }
      subscriptionData <- subscriptionDataService.retrieveSubscriptionData(atedRefNo)
    } yield {
      submitStatus.status match {
        case OK =>
          deleteDraftPropertyDetail(atedRefNo, oldFormBundleNo)
          sendMail(subscriptionData.json, emailTemplate(submitStatus.json, oldFormBundleNo))
          HttpResponse(
            submitStatus.status,
            responseHeaders = submitStatus.allHeaders,
            responseJson = Some(Json.toJson(submitStatus.json.as[EditLiabilityReturnsResponseModel])),
            responseString = Some(Json.prettyPrint(Json.toJson(submitStatus.json.as[EditLiabilityReturnsResponseModel])))
          )
        case someStatus =>
          Logger.warn(s"[PropertyDetailsService][submitChangeLiability] status = $someStatus body = ${submitStatus.body}")
          submitStatus
      }
    }
  }
}

object ChangeLiabilityService extends ChangeLiabilityService {
  val propertyDetailsCache: PropertyDetailsMongoRepository = PropertyDetailsMongoRepository()
  val etmpConnector: EtmpReturnsConnector = EtmpReturnsConnector
  val authConnector: AuthConnector = AuthConnector
  val subscriptionDataService: SubscriptionDataService = SubscriptionDataService
  val emailConnector: EmailConnector = EmailConnector
}
