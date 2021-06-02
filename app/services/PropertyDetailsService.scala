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

package services

import connectors.{EmailConnector, EtmpReturnsConnector}

import javax.inject.Inject
import models._
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import repository.{PropertyDetailsMongoRepository, PropertyDetailsMongoWrapper}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse, InternalServerException}
import utils.AtedUtils._
import utils._

import scala.concurrent.{ExecutionContext, Future}

class PropertyDetailsServiceImpl @Inject()(val etmpConnector: EtmpReturnsConnector,
                                           val authConnector: AuthConnector,
                                           val subscriptionDataService: SubscriptionDataService,
                                           val emailConnector: EmailConnector,
                                           val propertyDetailsMongoWrapper: PropertyDetailsMongoWrapper,
                                           override implicit val ec: ExecutionContext
                                          ) extends PropertyDetailsService {
  lazy val propertyDetailsCache: PropertyDetailsMongoRepository = propertyDetailsMongoWrapper()
}

trait PropertyDetailsService extends PropertyDetailsBaseService with ReliefConstants with NotificationService with AuthFunctionality {

  implicit val ec: ExecutionContext

  def subscriptionDataService: SubscriptionDataService

  def retrievePeriodDraftPropertyDetails(atedRefNo: String, periodKey: Int)(implicit ec: ExecutionContext): Future[Seq[PropertyDetails]] = {

    propertyDetailsCache.fetchPropertyDetails(atedRefNo).map {
      propertyDetailsList =>
        propertyDetailsList.filter(currentDraft => currentDraft.periodKey == periodKey)
    }
  }

  def createDraftPropertyDetails(atedRefNo: String, periodKey: Int, updatedAddress: PropertyDetailsAddress)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      Future.successful(Some(PropertyDetails(atedRefNo = atedRefNo, periodKey = periodKey, id = createPropertyKey, addressProperty = updatedAddress)))
    }
    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftPropertyDetailsAddress(atedRefNo: String, id: String, updatedAddress: PropertyDetailsAddress)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          foundPropertyDetails.copy(addressProperty = updatedAddress, calculated = None)
      }
      Future.successful(updatedPropertyDetails)
    }
    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftPropertyDetailsTitle(atedRefNo: String, id: String, updatedTitle: PropertyDetailsTitle)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          foundPropertyDetails.copy(title = Some(updatedTitle), calculated = None)
      }
      Future.successful(updatedPropertyDetails)
    }
    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }


  def calculateDraftPropertyDetails(atedRefNo: String, id: String)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    retrieveAgentRefNumberFor { agentRefNo =>
      def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
        val propertyDetailsOpt = propertyDetailsList.find(_.id == id)
        val liabilityAmountOpt = propertyDetailsOpt.flatMap(_.calculated.flatMap(_.liabilityAmount))
        (propertyDetailsOpt, liabilityAmountOpt) match {
          case (Some(foundPropertyDetails), None) =>
            val calculatedValues = PropertyDetailsUtils.propertyDetailsCalculated(foundPropertyDetails)
            val propertyDetailsWithCalculated = foundPropertyDetails.copy(calculated = Some(calculatedValues))
            for {
              liabilityAmount <- getLiabilityAmount(atedRefNo, id, propertyDetailsWithCalculated, agentRefNo)
            } yield {
              val updateCalculatedWithLiability = propertyDetailsWithCalculated.calculated.map(_.copy(liabilityAmount = liabilityAmount))
              Some(propertyDetailsWithCalculated.copy(calculated = updateCalculatedWithLiability))
            }

          case _ => Future.successful(propertyDetailsOpt)
        }
      }

      cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
    }
  }

  def getLiabilityAmount(atedRefNo: String, id: String,
                         propertyDetails: PropertyDetails, agentRefNo: Option[String] = None)
                        (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[BigDecimal]] = {
    def getLiabilityAmount(response: JsValue): Option[BigDecimal] = {
      val liabilityResponses = response.as[SubmitEtmpReturnsResponse]
      val result = liabilityResponses.liabilityReturnResponse.flatMap {
        _.find(liability => liability.propertyKey.equals(id))
      }
      result.map(_.liabilityAmount)
    }
    val etmpSubmitReturnRequest = LiabilityUtils.createPreCalculationReturnsRequest(id, propertyDetails, agentRefNo)
    etmpSubmitReturnRequest match {
      case Some(returnRequest) => etmpConnector.submitReturns(atedRefNo, returnRequest).map { response =>
        response.status match {
          case OK => getLiabilityAmount(response.json)
          case BAD_REQUEST => throw new BadRequestException(response.body)
          case status => throw new InternalServerException("[PropertyDetailsService][getLiabilityAmount] No Liability Amount Found")
        }
      }
      case None => throw new InternalServerException("[PropertyDetailsService][getLiabilityAmount] Invalid Data for the request")
    }
  }

  def cacheDraftTaxAvoidance(atedRefNo: String, id: String, updatedDetails: PropertyDetailsTaxAvoidance)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>

          if (updatedDetails.isTaxAvoidance == foundPropertyDetails.period.flatMap(_.isTaxAvoidance) &&
            updatedDetails.taxAvoidanceScheme == foundPropertyDetails.period.flatMap(_.taxAvoidanceScheme) &&
            updatedDetails.taxAvoidancePromoterReference == foundPropertyDetails.period.flatMap(_.taxAvoidancePromoterReference))
            foundPropertyDetails
          else {
            val updatedPeriod = foundPropertyDetails.period.map(_.copy(
              isTaxAvoidance = updatedDetails.isTaxAvoidance,
              taxAvoidanceScheme = updatedDetails.taxAvoidanceScheme,
              taxAvoidancePromoterReference = updatedDetails.taxAvoidancePromoterReference
            ))
            foundPropertyDetails.copy(period = updatedPeriod, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }
    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftSupportingInfo(atedRefNo: String, id: String, updatedDetails: PropertyDetailsSupportingInfo)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>

          if (foundPropertyDetails.period.flatMap(_.supportingInfo).contains(updatedDetails.supportingInfo))
            foundPropertyDetails
          else {
            val updatedPeriod = foundPropertyDetails.period.map(_.copy(
              supportingInfo = Some(updatedDetails.supportingInfo)
            ))
            foundPropertyDetails.copy(period = updatedPeriod, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }
    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftHasBankDetails(atedRefNo: String, id: String, hasBankDetails: Boolean)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          val oldBankDetails = foundPropertyDetails.bankDetails.getOrElse(BankDetailsModel())
          val updatedBankDetails = if (hasBankDetails)
            oldBankDetails.copy(hasBankDetails = hasBankDetails)
          else
            oldBankDetails.copy(hasBankDetails = hasBankDetails, protectedBankDetails = None, bankDetails = None)

          foundPropertyDetails.copy(bankDetails = Some(updatedBankDetails))
      }
      Future.successful(updatedPropertyDetails)
    }
    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftBankDetails(atedRefNo: String, id: String, newBankDetails: BankDetails)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      import models.BankDetailsConversions._
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          val oldBankDetails = foundPropertyDetails.bankDetails.getOrElse(BankDetailsModel(hasBankDetails = true))
          val updatedBankDetails = oldBankDetails.copy(protectedBankDetails = Some(newBankDetails))
          foundPropertyDetails.copy(bankDetails = Some(updatedBankDetails))
      }
      Future.successful(updatedPropertyDetails)
    }
    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def submitDraftPropertyDetail(atedRefNo: String, id: String)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    retrieveAgentRefNumberFor { agentRefNo =>
      val propertyDetailsFuture = retrieveDraftPropertyDetail(atedRefNo, id)
      (for {
        propertyDetails <- propertyDetailsFuture
        submitResponse <- {
          propertyDetails match {
            case Some(x) =>
              val etmpSubmitReturnRequest = LiabilityUtils.createPostReturnsRequest(id, x, agentRefNo)
              etmpSubmitReturnRequest match {
                case Some(returnRequest) => etmpConnector.submitReturns(atedRefNo, returnRequest)
                case None => Future.successful(HttpResponse(NOT_FOUND, ""))
              }
            case None => Future.successful(HttpResponse(NOT_FOUND, ""))
          }
        }
        subscriptionData <- subscriptionDataService.retrieveSubscriptionData(atedRefNo)
      } yield {
        submitResponse.status match {
          case OK =>
            for {
              _ <- deleteDraftPropertyDetail(atedRefNo, id)
              _ <- sendMail (subscriptionData.json, "chargeable_return_submit")
            } yield HttpResponse(
              submitResponse.status,
              json = Json.toJson(submitResponse.json.as[SubmitEtmpReturnsResponse]),
              headers = submitResponse.headers)
          case _ =>
            Future.successful(submitResponse)
        }
      }).flatten
    }
  }

  def deleteChargeableDraft(atedRefNo: String, id: String)(implicit ec: ExecutionContext): Future[Seq[PropertyDetails]] = {
    for {
      _ <- propertyDetailsCache.deletePropertyDetailsByfieldName(atedRefNo, id)
      reliefsList <- propertyDetailsCache.fetchPropertyDetailsById(atedRefNo, id)
    } yield {
      reliefsList
    }
  }

}