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

package services

import connectors.{EmailConnector, EtmpReturnsConnector}

import javax.inject.Inject
import models._
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.Json
import repository.{DisposeLiabilityReturnMongoRepository, DisposeLiabilityReturnMongoWrapper}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.ReliefUtils._
import utils.SessionUtils._
import utils.{AuthFunctionality, ChangeLiabilityUtils, PropertyDetailsUtils}

import scala.concurrent.{ExecutionContext, Future}

class DisposeLiabilityReturnServiceImpl @Inject()(val etmpReturnsConnector: EtmpReturnsConnector,
                                                  val disposeLiabilityReturnMongoWrapper: DisposeLiabilityReturnMongoWrapper,
                                                  val authConnector: AuthConnector,
                                                  val subscriptionDataService: SubscriptionDataService,
                                                  val emailConnector: EmailConnector,
                                                  override implicit val ec: ExecutionContext
                                                 ) extends DisposeLiabilityReturnService {
  lazy val disposeLiabilityReturnRepository: DisposeLiabilityReturnMongoRepository = disposeLiabilityReturnMongoWrapper()
}

trait DisposeLiabilityReturnService extends NotificationService with AuthFunctionality with Logging {

  def disposeLiabilityReturnRepository: DisposeLiabilityReturnMongoRepository

  def etmpReturnsConnector: EtmpReturnsConnector

  def authConnector: AuthConnector

  def subscriptionDataService: SubscriptionDataService

  def retrieveDraftDisposeLiabilityReturns(atedRefNo: String): Future[Seq[DisposeLiabilityReturn]] = {
    disposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(atedRefNo)
  }

  def retrieveDraftDisposeLiabilityReturn(atedRefNo: String, oldFormBundleNo: String)(implicit ec: ExecutionContext): Future[Option[DisposeLiabilityReturn]] = {
    retrieveDraftDisposeLiabilityReturns(atedRefNo) map {
      x => x.find(_.id == oldFormBundleNo)
    }
  }

  def retrieveAndCacheDisposeLiabilityReturn(atedRefNo: String, oldFormBundleNo: String)
                                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[DisposeLiabilityReturn]] = {
    for {
      cachedData <- retrieveDraftDisposeLiabilityReturn(atedRefNo, oldFormBundleNo)
      cachedDisposeLiability <- {
        cachedData match {
          case Some(x) =>
            Future.successful(Option(convertBankDetails(x)))
          case None =>
            etmpReturnsConnector.getFormBundleReturns(atedRefNo, oldFormBundleNo) flatMap {
              response =>
                response.status match {
                  case OK =>
                    val formBundleReturn = response.json.as[FormBundleReturn]
                    val dispose = DisposeLiability(dateOfDisposal = None, periodKey = formBundleReturn.periodKey.trim.toInt)
                    val disposeLiability = DisposeLiabilityReturn(atedRefNo = atedRefNo, id = oldFormBundleNo, formBundleReturn = formBundleReturn, disposeLiability = Some(dispose))
                    disposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(disposeLiability).flatMap { _ =>
                      retrieveDraftDisposeLiabilityReturn(atedRefNo, oldFormBundleNo)
                    }
                  case _ => Future.successful(None)
                }
            }
        }
      }
    } yield {
      cachedDisposeLiability
    }
  }

  def updateDraftDisposeLiabilityReturnDate(atedRefNo: String, oldFormBundleNo: String, updatedDate: DisposeLiability)(implicit ec: ExecutionContext): Future[Option[DisposeLiabilityReturn]] = {
    for {
      disposeLiabilityReturnList <- retrieveDraftDisposeLiabilityReturns(atedRefNo)
      disposeLiabilityOpt <- {
        disposeLiabilityReturnList.find(_.id == oldFormBundleNo) match {
          case Some(x) =>
            val updatedReturn = x.copy(disposeLiability = Some(updatedDate), calculated = None)
            disposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(updatedReturn).flatMap(x => Future.successful(Some(updatedReturn)))
          case None => Future.successful(None)
        }
      }
    } yield {
      disposeLiabilityOpt
    }
  }

  def updateDraftDisposeHasBankDetails(atedRefNo: String, oldFormBundleNo: String, hasBankDetails: Boolean)(
    implicit ec: ExecutionContext): Future[Option[DisposeLiabilityReturn]] = {
    val disposeLiabilityReturnListFuture = retrieveDraftDisposeLiabilityReturns(atedRefNo)
    for {
      disposeLiabilityReturnList <- disposeLiabilityReturnListFuture
      disposeLiabilityOpt <- {
        disposeLiabilityReturnList.find(_.id == oldFormBundleNo) match {
          case Some(x) =>
            val oldBankDetails = x.bankDetails.getOrElse(BankDetailsModel())
            val updatedBankDetails = if (hasBankDetails)
              oldBankDetails.copy(hasBankDetails = hasBankDetails)
            else
              oldBankDetails.copy(hasBankDetails = hasBankDetails, bankDetails = None, protectedBankDetails = None)
            val updatedReturn = x.copy(bankDetails = Some(updatedBankDetails), calculated = None)
            disposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(updatedReturn).flatMap(x => Future.successful(Some(updatedReturn)))
          case None => Future.successful(None)
        }
      }
    } yield {
      disposeLiabilityOpt
    }
  }

  def updateDraftDisposeBankDetails(atedRefNo: String, oldFormBundleNo: String, updatedValue: BankDetails)(
    implicit ec: ExecutionContext): Future[Option[DisposeLiabilityReturn]] = {
    import models.BankDetailsConversions._
    val disposeLiabilityReturnListFuture = retrieveDraftDisposeLiabilityReturns(atedRefNo)
    for {
      disposeLiabilityReturnList <- disposeLiabilityReturnListFuture
      disposeLiabilityOpt <- {
        disposeLiabilityReturnList.find(_.id == oldFormBundleNo) match {
          case Some(x) =>
            val oldBankDetails = x.bankDetails.getOrElse(BankDetailsModel(hasBankDetails = true))
            val updatedBankDetails: BankDetailsModel = oldBankDetails.copy(protectedBankDetails = Some(updatedValue))
            val updatedReturn = x.copy(bankDetails = Some(updatedBankDetails), calculated = None)
            val result = disposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(updatedReturn).flatMap(x => Future.successful(Some(updatedReturn)))
            result
          case None => Future.successful(None)
        }
      }
    } yield {
      disposeLiabilityOpt
    }
  }

  def calculateDraftDispose(atedRefNo: String, oldFormBundleNo: String)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisposeLiabilityReturn]] = {
    retrieveAgentRefNumberFor { agentRefNo =>
      val disposeLiabilityReturnListFuture = retrieveDraftDisposeLiabilityReturns(atedRefNo)
      for {
        disposeLiabilityReturnList <- disposeLiabilityReturnListFuture
        disposeLiabilityOpt <- {
          disposeLiabilityReturnList.find(_.id == oldFormBundleNo) match {
            case Some(x) =>
              getPreCalculationAmounts(atedRefNo, x.formBundleReturn,
                x.disposeLiability.fold(DisposeLiability(periodKey = x.formBundleReturn.periodKey.trim.toInt))(a => a),
                oldFormBundleNo, agentRefNo) flatMap { calculated =>
                val updatedReturn = x.copy(calculated = Some(calculated))
                disposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(updatedReturn).flatMap(_ => Future.successful(Some(convertBankDetails(updatedReturn))))
              }
            case None => Future.successful(None)
          }
        }
      } yield {
        disposeLiabilityOpt
      }
    }
  }


  def getPreCalculationAmounts(atedRefNo: String, x: FormBundleReturn, disposalDate: DisposeLiability, oldFormBNo: String,
                               agentRefNo: Option[String] = None)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[DisposeCalculated] = {
    def generateEditReturnRequest: EditLiabilityReturnsRequestModel = {
      val liabilityReturn = EditLiabilityReturnsRequest(oldFormBundleNumber = oldFormBNo,
        mode = PreCalculation,
        periodKey = x.periodKey,
        propertyDetails = getEtmpPropertyDetails(x.propertyDetails),
        dateOfAcquisition = x.dateOfAcquisition,
        valueAtAcquisition = x.valueAtAcquisition,
        dateOfValuation = x.dateOfValuation,
        taxAvoidanceScheme = x.taxAvoidanceScheme,
        localAuthorityCode = x.localAuthorityCode,
        professionalValuation = x.professionalValuation,
        ninetyDayRuleApplies = x.ninetyDayRuleApplies,
        lineItem = PropertyDetailsUtils.disposeLineItems(x.periodKey, x.lineItem, disposalDate.dateOfDisposal),
        bankDetails = None)
      EditLiabilityReturnsRequestModel(acknowledgmentReference = getUniqueAckNo, agentReferenceNumber = agentRefNo, liabilityReturn = Seq(liabilityReturn))
    }

    etmpReturnsConnector.submitEditedLiabilityReturns(atedRefNo, editedLiabilityReturns = generateEditReturnRequest, disposal = true) map {
      response =>
        response.status match {
          case OK =>
            val responseData = response.json.as[EditLiabilityReturnsResponseModel]
            responseData.liabilityReturnResponse.find(_.oldFormBundleNumber == oldFormBNo)
              .fold(DisposeCalculated(BigDecimal(0), BigDecimal(0)))(a => DisposeCalculated(liabilityAmount = a.liabilityAmount,
                amountDueOrRefund = a.amountDueOrRefund))
          case status =>
            logger.warn(s"[DisposeLiabilityReturnService][getPreCalculationAmounts] - response status = $status, response body = ${response.body}")
            throw new RuntimeException("pre-calculation-request returned wrong status")
        }
    }
  }

  def deleteDisposeLiabilityDraft(atedRefNo: String, oldFormBundleNo: String)(
    implicit ec: ExecutionContext): Future[Seq[DisposeLiabilityReturn]] = {
    for {
      disposeLiabilityReturnList <- retrieveDraftDisposeLiabilityReturns(atedRefNo)
      updatedListAfterDelete <- {
        disposeLiabilityReturnList.find(_.id == oldFormBundleNo) match {
          case Some(_) =>
            val filteredList = disposeLiabilityReturnList.filterNot(_.id == oldFormBundleNo)
            filteredList
              .map {
                dispLiab =>
                  disposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(dispLiab)
                    .flatMap(
                      _ => Future.successful(filteredList)
                    )
              }.headOption getOrElse Future.successful(Nil)
          case None => Future.successful(Nil)
        }
      }
    } yield {
      updatedListAfterDelete
    }
  }

  //scalastyle:off method.length
  def submitDisposeLiability(atedRefNo: String, oldFormBundleNo: String)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    retrieveAgentRefNumberFor { agentRefNo =>
      val disposeLiabilityReturnListFuture = retrieveDraftDisposeLiabilityReturns(atedRefNo)

      def generateEditReturnRequest(x: DisposeLiabilityReturn, agentRefNo: Option[String]): EditLiabilityReturnsRequestModel = {
        val liabilityReturn = EditLiabilityReturnsRequest(oldFormBundleNumber = oldFormBundleNo,
          mode = Post,
          periodKey = x.formBundleReturn.periodKey,
          propertyDetails = getEtmpPropertyDetails(x.formBundleReturn.propertyDetails),
          dateOfAcquisition = x.formBundleReturn.dateOfAcquisition,
          valueAtAcquisition = x.formBundleReturn.valueAtAcquisition,
          dateOfValuation = x.formBundleReturn.dateOfValuation,
          taxAvoidanceScheme = x.formBundleReturn.taxAvoidanceScheme,
          localAuthorityCode = x.formBundleReturn.localAuthorityCode,
          professionalValuation = x.formBundleReturn.professionalValuation,
          ninetyDayRuleApplies = x.formBundleReturn.ninetyDayRuleApplies,
          lineItem = PropertyDetailsUtils.disposeLineItems(x.formBundleReturn.periodKey, x.formBundleReturn.lineItem, x.disposeLiability.flatMap(_.dateOfDisposal)),
          bankDetails = ChangeLiabilityUtils.getEtmpBankDetails(x.bankDetails))
        EditLiabilityReturnsRequestModel(acknowledgmentReference = getUniqueAckNo, agentReferenceNumber = agentRefNo, liabilityReturn = Seq(liabilityReturn))
      }

      (for {
        disposeLiabilityReturnList <- disposeLiabilityReturnListFuture
        submitStatus: HttpResponse <- {
          disposeLiabilityReturnList.find(_.id == oldFormBundleNo) match {
            case Some(x) => etmpReturnsConnector.submitEditedLiabilityReturns(atedRefNo, generateEditReturnRequest(x, agentRefNo), disposal = true)
            case None => Future.successful(HttpResponse(NOT_FOUND, ""))
          }
        }
        subscriptionData <- subscriptionDataService.retrieveSubscriptionData(atedRefNo)
      } yield {
        submitStatus.status match {
          case OK =>
            for {
              _ <- deleteDisposeLiabilityDraft(atedRefNo, oldFormBundleNo)
              _ <- sendMail(subscriptionData.json, "disposal_return_submit")
            } yield HttpResponse(
              submitStatus.status,
              json = Json.toJson(submitStatus.json.as[EditLiabilityReturnsResponseModel]),
              headers = submitStatus.headers
            )
          case someStatus =>
            logger.warn(s"[DisposeLiabilityReturnService][submitDisposeLiability] status = $someStatus body = ${submitStatus.body}")
            Future.successful(submitStatus)
        }
      }).flatten
    }
  }

  private def convertBankDetails(cachedData: DisposeLiabilityReturn): DisposeLiabilityReturn = {
    import models.BankDetailsConversions._
    cachedData.bankDetails.flatMap(_.protectedBankDetails) match {
      case Some(y) =>
        val newBankDetails = cachedData.bankDetails.map(_.copy(bankDetails = Some(y), protectedBankDetails = None))
        cachedData.copy(bankDetails = newBankDetails)
      case None => cachedData
    }
  }

  private def getEtmpPropertyDetails(property: FormBundlePropertyDetails) = {
    val propertyDetailsAddress = EtmpAddress(addressLine1 = property.address.addressLine1,
      addressLine2 = property.address.addressLine2,
      addressLine3 = property.address.addressLine3,
      addressLine4 = property.address.addressLine4,
      countryCode = property.address.countryCode,
      postalCode = property.address.postalCode)

    EtmpPropertyDetails(titleNumber = property.titleNumber, address = propertyDetailsAddress,
      additionalDetails = property.additionalDetails)
  }
}
