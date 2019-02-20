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

package utils

import models.BankDetailsConversions._
import models._
import org.joda.time.LocalDate
import utils.PropertyDetailsUtils._
import utils.ReliefUtils.{Gb, Post, PreCalculation}

object ChangeLiabilityUtils extends ReliefConstants {

  def generateAddressFromLiabilityReturn(x: FormBundleReturn): PropertyDetailsAddress = {

    PropertyDetailsAddress(
      line_1 = x.propertyDetails.address.addressLine1,
      line_2 = x.propertyDetails.address.addressLine2,
      line_3 = x.propertyDetails.address.addressLine3,
      line_4 = x.propertyDetails.address.addressLine4,
      postcode = x.propertyDetails.address.postalCode)
  }

  def generateTitleFromLiabilityReturn(x: FormBundleReturn): Option[PropertyDetailsTitle] = {
    x.propertyDetails.titleNumber.map(titleNumber => PropertyDetailsTitle(titleNumber))
  }

  def generatePeriodFromLiabilityReturn(x: FormBundleReturn): PropertyDetailsPeriod = {
    val liabilityPeriods = x.lineItem.filter(_.`type` != TypeRelief).map(lineItem =>
      LineItem(lineItemType = lineItem.`type`, startDate = lineItem.dateFrom, endDate = lineItem.dateTo, description = lineItem.reliefDescription)
    )
    val reliefPeriods = x.lineItem.filter(_.`type` == TypeRelief).map(lineItem =>
      LineItem(lineItemType = lineItem.`type`, startDate = lineItem.dateFrom, endDate = lineItem.dateTo, description = lineItem.reliefDescription)
    )
    val isFullPeriod = (x.lineItem.size) match {
      case 1 =>
        val startDate = new LocalDate(s"${x.periodKey}-04-01")
        val endDate = startDate.plusYears(1).minusDays(1)
        x.lineItem.headOption.map(item =>
          item.dateFrom == startDate && item.dateTo == endDate
        )
      case _ =>
        Some(false)
    }

    PropertyDetailsPeriod(
      liabilityPeriods = liabilityPeriods.toList,
      reliefPeriods = reliefPeriods.toList,
      isTaxAvoidance = x.taxAvoidanceScheme.fold(Some(false))(a => Some(true)),
      taxAvoidanceScheme = x.taxAvoidanceScheme,
      taxAvoidancePromoterReference = x.taxAvoidancePromoterReference,
      supportingInfo = x.propertyDetails.additionalDetails,
      isInRelief = None,
      isFullPeriod = isFullPeriod
    )
  }


  def getChangeLiabilityProfessionalValuation(changeLiability: PropertyDetails): Option[Boolean] = {
    changeLiability.value.flatMap { v =>
      v.hasValueChanged match {
        case Some(true) =>
          if (v.isPropertyRevalued.isDefined) Some(true)
          else v.isValuedByAgent
        case Some(false) => changeLiability.formBundleReturn.map(_.professionalValuation)
        case None => changeLiability.formBundleReturn.map(_.professionalValuation)
      }
    }
  }


  def changeLiabilityCalculated(changeLiability: PropertyDetails, liabilityAmount: Option[BigDecimal] = None) = {

    val valueToUse = changeLiabilityInitialValueForPeriod(changeLiability)
    val (acquisitionValueToUse, acquisitionDateToUse) = getAcquisitionData(changeLiability)
    val valuationDate = getChangeLiabilityValuationDate(changeLiability, acquisitionDateToUse)
    val (lineItemValue, lineItemUpdateTuple) = PropertyDetailsUtils.getLineItemValues(changeLiability.value, valueToUse)

    PropertyDetailsCalculated(
      liabilityPeriods = PropertyDetailsUtils.createLiabilityPeriods(changeLiability.periodKey, changeLiability.period, lineItemValue, lineItemUpdateTuple),
      reliefPeriods = PropertyDetailsUtils.createReliefPeriods(changeLiability.period, lineItemValue, lineItemUpdateTuple),
      valuationDateToUse = valuationDate,
      acquistionValueToUse = acquisitionValueToUse,
      acquistionDateToUse = acquisitionDateToUse,
      professionalValuation = getChangeLiabilityProfessionalValuation(changeLiability),
      liabilityAmount = liabilityAmount
    )
  }

  private def getChangeLiabilityValuationDate(changeLiability: PropertyDetails, acquisitionDateToUse: Option[LocalDate]): Option[LocalDate] = {
    changeLiability.value.flatMap(_.hasValueChanged ) match {
      case Some(false) => changeLiability.formBundleReturn.flatMap(_.dateOfAcquisition)
      case Some(true)  => getValuationDate(changeLiability.value, acquisitionDateToUse, changeLiability.periodKey)
      case _ => None
    }
  }

  def changeLiabilityInitialValueForPeriod(changeLiability: PropertyDetails): Option[BigDecimal] = {
    def getEarliestValue(formBundlePeriods : Option[Seq[FormBundleProperty]]) :Option[BigDecimal] = {
      implicit val localDateOrdering: Ordering[LocalDate] = Ordering.by(_.toDate.getTime)
      implicit val lineItemOrdering: Ordering[FormBundleProperty] = Ordering.by(_.dateFrom)

      formBundlePeriods.flatMap{
        formBundle =>
          formBundle.sorted.headOption.map(_.propertyValue)
      }
    }

    changeLiability.value.flatMap(_.hasValueChanged ) match {
      case Some(false) => getEarliestValue(changeLiability.formBundleReturn.map(_.lineItem))
      case Some(true) =>
          PropertyDetailsUtils.getInitialValueForSubmission(changeLiability.value, changeLiability.periodKey)
      case _ => None
    }
  }

  private def getAcquisitionData(changeLiability: PropertyDetails): (Option[BigDecimal], Option[LocalDate]) = {
    changeLiability.value match {
      case None => (None, None)
      case Some(value) =>
        value.hasValueChanged match {
          case Some(true) => PropertyDetailsUtils.getAcquisitionValueAndDate(value, changeLiability.periodKey)
          case Some(false) => (changeLiability.formBundleReturn.flatMap(_.valueAtAcquisition), changeLiability.formBundleReturn.flatMap(_.dateOfAcquisition))
          case None => (None, None)
        }

    }
  }

  def getNinetyDayRuleApplies(x: PropertyDetails): Boolean = {
    val ninetyDayRuleApplies = x.formBundleReturn.map(_.ninetyDayRuleApplies).getOrElse(false)
    x.value.map {
      b => b.hasValueChanged.fold(ninetyDayRuleApplies)(a =>
        if (a) {b.isNewBuild.fold(ninetyDayRuleApplies)(c => c)} else ninetyDayRuleApplies)
    }.fold(ninetyDayRuleApplies)(a => a)
  }

  def getEtmpBankDetails(x: Option[BankDetailsModel]): Option[EtmpBankDetails] = {
    def createInternationalBankDetails(bankDetails: BankDetails) : Option[EtmpBankDetails] = {
      (bankDetails.accountName, bankDetails.bicSwiftCode, bankDetails.iban) match {
        case (Some(accountName), Some(bicSwiftCode), Some(iban)) =>
          Some(EtmpBankDetails(accountName = accountName,
            ukAccount = None,
            internationalAccount = Some(InternationalAccount(bicSwiftCode = bicSwiftCode.strippedSwiftCode, iban = iban.strippedIBan))))
        case _ => None
      }
    }
    def createUkBankDetails(bankDetails: BankDetails) : Option[EtmpBankDetails] = {
      (bankDetails.accountName, bankDetails.sortCode, bankDetails.accountNumber) match {
        case (Some(accountName), Some(sortCode), Some(accountNo)) if (accountName.length > 0 && accountNo.length > 0) =>
          Some(EtmpBankDetails(accountName = accountName,
            ukAccount = Some(UKAccount(
              sortCode = sortCode.firstElement + sortCode.secondElement + sortCode.thirdElement,
              accountNumber = accountNo)),
            internationalAccount = None
          ))
        case _ => None
      }
    }
    x.flatMap(_.protectedBankDetails).fold(None: Option[EtmpBankDetails]) {
      a => val bd: BankDetails = protected2BankDetails(a) // implicit conversion of bank-details
        (x.map(_.hasBankDetails), bd.hasUKBankAccount) match {
          case (Some(true), Some(true)) =>
            createUkBankDetails(bd)
          case (Some(true), Some(false)) =>
            createInternationalBankDetails(bd)
          case _ => None
        }
    }
  }

  def createPreCalculationRequest(propertyDetails: PropertyDetails, agentRefNo: Option[String] = None) =
    createChangeLiabilityReturnRequest(propertyDetails, PreCalculation, agentRefNo)

  def createPostRequest(propertyDetails: PropertyDetails, agentRefNo: Option[String] = None) =
    createChangeLiabilityReturnRequest(propertyDetails, Post, agentRefNo)

  def createChangeLiabilityReturnRequest(propertyDetails: PropertyDetails, mode: String, agentRefNo: Option[String] = None)
  : Option[EditLiabilityReturnsRequestModel] = {
      propertyDetails.calculated match {
        case Some(c) =>
          (c.valuationDateToUse, c.professionalValuation) match {
            case (Some(dateOfVal), Some(professionallyValued)) =>
              val liabilityReturns = createLiabilityReturns(propertyDetails, c, mode, agentRefNo, dateOfVal, professionallyValued)
              Some(EditLiabilityReturnsRequestModel(SessionUtils.getUniqueAckNo, agentRefNo, liabilityReturns))
            case _ => None
          }
        case None => None
      }
  }

  private def createLiabilityReturns(a: PropertyDetails, c: PropertyDetailsCalculated,
                                     mode: String, agentRefNo: Option[String] = None,
                                     valuationDateToUse : LocalDate,
                                     professionallyValued : Boolean) = {

    val liabilityReturn = EditLiabilityReturnsRequest(
      oldFormBundleNumber = a.id,
      mode = mode,
      periodKey = "" + a.periodKey,
      propertyDetails = getEtmpPropertyDetails(a),
      dateOfAcquisition = c.acquistionDateToUse,
      valueAtAcquisition = c.acquistionValueToUse,
      dateOfValuation = valuationDateToUse,
      taxAvoidanceScheme = getTaxAvoidanceScheme(a),
      taxAvoidancePromoterReference = getTaxAvoidancePromoterReference(a),
      professionalValuation = professionallyValued,
      localAuthorityCode = None,
      ninetyDayRuleApplies = getNinetyDayRuleApplies(a),
      lineItem = getLineItems(c),
      bankDetails = getEtmpBankDetails(a.bankDetails))

    List(liabilityReturn)
  }

}
