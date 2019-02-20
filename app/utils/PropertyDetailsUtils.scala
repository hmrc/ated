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

import models._
import org.joda.time.LocalDate
import uk.gov.hmrc.http.InternalServerException


object PropertyDetailsUtils extends ReliefConstants {

  val _2012ValuationPolicyDate = Some(new LocalDate("2012-04-01"))
  val _2017ValuationPolicyDate = Some(new LocalDate("2017-04-01"))

  def propertyDetailsCalculated(propertyDetails: PropertyDetails): PropertyDetailsCalculated = {

    def getProfessionalValuation(propertyDetails: PropertyDetails): Option[Boolean] = {
      propertyDetails.value.flatMap { v =>
        if (v.isPropertyRevalued.isDefined) Some(true) // this is set to true, because in user-journey, user can't select no and proceed on page
        else v.isValuedByAgent
      }
    }

    val valueToUse = getInitialValueForSubmission(propertyDetails.value, propertyDetails.periodKey)
    val (acquistionValueToUse, acquistionDateToUse) = getAcquisitionData(propertyDetails.value, propertyDetails.periodKey)
    val (lineItemValue, lineItemUpdateTuple) = getLineItemValues(propertyDetails.value, valueToUse)

    PropertyDetailsCalculated(
      liabilityPeriods = createLiabilityPeriods(propertyDetails.periodKey, propertyDetails.period, lineItemValue, lineItemUpdateTuple),
      reliefPeriods = createReliefPeriods(propertyDetails.period, lineItemValue, lineItemUpdateTuple),
      valuationDateToUse = getValuationDate(propertyDetails.value, acquistionDateToUse, propertyDetails.periodKey),
      acquistionValueToUse = acquistionValueToUse,
      acquistionDateToUse = acquistionDateToUse,
      professionalValuation = getProfessionalValuation(propertyDetails)
    )

  }

  def createLiabilityPeriods(periodKey: Int, propertyDetailsPeriod: Option[PropertyDetailsPeriod], initialValue: BigDecimal, updateValue: Option[(LocalDate, BigDecimal)] = None): Seq[CalculatedPeriod] = {
    propertyDetailsPeriod.map { periodVal =>
      val totalPeriods = periodVal.liabilityPeriods.size + periodVal.reliefPeriods.size
      if (periodVal.isFullPeriod == Some(true) && totalPeriods == 0) {
        val startDate = new LocalDate(s"${periodKey}-04-01")
        val endDate = new LocalDate(s"${periodKey}-04-01").plusYears(1).minusDays(1)
        val period = LineItem(TypeLiability, startDate, endDate)
        createCalculatedPeriod(period, initialValue, updateValue)
      } else {
        periodVal.liabilityPeriods.flatMap(createCalculatedPeriod(_, initialValue, updateValue))
      }
    }.getOrElse(Nil)

  }

  def createReliefPeriods(propertyDetailsPeriod: Option[PropertyDetailsPeriod], initialValue: BigDecimal, updateValue: Option[(LocalDate, BigDecimal)] = None): Seq[CalculatedPeriod] = {
    propertyDetailsPeriod.map { periodVal =>
      periodVal.reliefPeriods.flatMap(createCalculatedPeriod(_, initialValue, updateValue))
    }.getOrElse(Nil)

  }

  private def createCalculatedPeriod(lineItem: LineItem, initialValue: BigDecimal, updateValue: Option[(LocalDate, BigDecimal)] = None) = {
    updateValue match {
      case Some((valueDate, value)) if (!lineItem.startDate.isBefore(valueDate)) =>
        List(
          CalculatedPeriod(value, lineItem.startDate, lineItem.endDate, lineItem.lineItemType, lineItem.description)
        )
      case Some((valueDate, value)) if (lineItem.startDate.isBefore(valueDate) && lineItem.endDate.isAfter(valueDate)) => {
        List(
          CalculatedPeriod(initialValue, lineItem.startDate, valueDate.plusDays(-1), lineItem.lineItemType, lineItem.description),
          CalculatedPeriod(value, valueDate, lineItem.endDate, lineItem.lineItemType, lineItem.description)
        )
      }
      case _ =>
        List(
          CalculatedPeriod(initialValue, lineItem.startDate, lineItem.endDate, lineItem.lineItemType, lineItem.description)
        )
    }
  }


  def getEtmpPropertyDetails(property: PropertyDetails) = {
    val propertyDetailsAddress = EtmpAddress(addressLine1 = property.addressProperty.line_1,
      addressLine2 = property.addressProperty.line_2,
      addressLine3 = property.addressProperty.line_3,
      addressLine4 = property.addressProperty.line_4,
      countryCode = Gb,
      postalCode = property.addressProperty.postcode)

    EtmpPropertyDetails(titleNumber = getTitleNumber(property), address = propertyDetailsAddress,
      additionalDetails = getAdditionalDetails(property))
  }

  def getLatestDate(firstDate: Option[LocalDate], secondDate: Option[LocalDate]) = {
    (firstDate, secondDate) match {
      case (Some(x), None) => Some(x)
      case (None, Some(y)) => Some(y)
      case (Some(x), Some(y)) if x.isBefore(y) => Some(y)
      case (Some(x), Some(y)) if !x.isBefore(y) => Some(x)
      case _ => None
    }
  }

  def getValuationDate(propertyDetailsValue: Option[PropertyDetailsValue], acquistionDateToUse: Option[LocalDate], periodKey: Int): Option[LocalDate] = {

    def getBasicValuationDate(value: PropertyDetailsValue): Option[LocalDate] = {
      val ownedBeforeData = PropertyDetailsOwnedBefore(value.isOwnedBeforePolicyYear, value.ownedBeforePolicyYearValue)
      (ownedBeforeData.policyYear(periodKey), value.isNewBuild) match {
        case (IsOwnedBefore2012, _) => _2012ValuationPolicyDate
        case (IsOwnedBefore2017, _) => _2017ValuationPolicyDate
        case (NotOwnedBeforePolicyYear, Some(true)) => calculateEarliestDate(value.newBuildDate, value.localAuthRegDate)
        case (NotOwnedBeforePolicyYear, Some(false)) => value.notNewBuildDate
        case _ => None
      }
    }

    propertyDetailsValue match {
      case None => None
      case Some(value) =>
        (value.isValuedByAgent, value.anAcquisition, value.isPropertyRevalued) match {
          case (Some(true), Some(true), Some(true)) => getLatestDate(acquistionDateToUse, value.revaluedDate)
          case (Some(true), _, _) => acquistionDateToUse
          case (_, Some(true), Some(true)) => value.revaluedDate
          case _ => getBasicValuationDate(value)
        }
    }
  }

  def getInitialValueForSubmission(propertyDetailsValue: Option[PropertyDetailsValue], periodKey: Int): Option[BigDecimal] = {
    propertyDetailsValue match {
      case None => None
      case Some(value) =>
        val ownedBefore = PropertyDetailsOwnedBefore(value.isOwnedBeforePolicyYear, value.ownedBeforePolicyYearValue)
        (ownedBefore.policyYear(periodKey), value.isNewBuild, value.isPropertyRevalued) match {
          case (IsOwnedBefore2012 | IsOwnedBefore2017, _, _) => value.ownedBeforePolicyYearValue
          case (NotOwnedBeforePolicyYear, Some(true), _) => value.newBuildValue
          case (NotOwnedBeforePolicyYear, Some(false), _) => value.notNewBuildValue
          case (_, _, Some(true)) => value.revaluedValue
          case (_, _, Some(false)) => value.revaluedValue
          case _ => None
        }
    }
  }


  def getAcquisitionValueAndDate(value: PropertyDetailsValue, periodKey: Int): (Option[BigDecimal], Option[LocalDate]) = {
    val ownedBefore = PropertyDetailsOwnedBefore(value.isOwnedBeforePolicyYear, value.ownedBeforePolicyYearValue)
    (ownedBefore.policyYear(periodKey), value.isNewBuild, value.isPropertyRevalued) match {
      case (IsOwnedBefore2012, _, _) => (value.ownedBeforePolicyYearValue, _2012ValuationPolicyDate)
      case (IsOwnedBefore2017, _, _) => (value.ownedBeforePolicyYearValue, _2017ValuationPolicyDate)
      case (NotOwnedBeforePolicyYear, Some(true), _) => (value.newBuildValue, calculateEarliestDate(value.newBuildDate,
        value.localAuthRegDate))
      case (NotOwnedBeforePolicyYear, Some(false), _) => (value.notNewBuildValue, value.notNewBuildDate)
      case (_, _, Some(true)) => (value.revaluedValue, value.partAcqDispDate)
      case (_, _, Some(false)) => (value.revaluedValue, value.partAcqDispDate)
      case _ => (None, None)
    }
  }

  def getAcquisitionData(propertyDetailsValue: Option[PropertyDetailsValue], periodKey: Int): (Option[BigDecimal], Option[LocalDate]) = {
    propertyDetailsValue match {
      case None => (None, None)
      case Some(value) => getAcquisitionValueAndDate(value, periodKey)
    }
  }

  def getLineItemValues(propertyDetailsValue: Option[PropertyDetailsValue], initialValue: Option[BigDecimal]): (BigDecimal, Option[(LocalDate, BigDecimal)]) = {
    def getUpdateValue(initialValue: BigDecimal) = {
      propertyDetailsValue.flatMap {
        value =>
          (value.isPropertyRevalued, value.revaluedValue, value.partAcqDispDate) match {
            case (_, Some(revalue), _) if revalue == initialValue => None
            case (Some(true), Some(revalue), Some(revaluedAcquiredDate)) =>
              Some(revaluedAcquiredDate, revalue)
            case _ => None
          }
      }
    }

    initialValue match {
      case Some(x) => (x, getUpdateValue(x))
      case _ => throw new InternalServerException("[PropertyDetailsUtils][getLineItemValues] - No Value Found")
    }
  }


  def getAdditionalDetails(propertyDetails: PropertyDetails): Option[String] = {
    propertyDetails.period.flatMap(_.supportingInfo) match {
      case Some(x) if (!x.trim().isEmpty) => Some(x)
      case _ => None
    }
  }

  def getTitleNumber(propertyDetails: PropertyDetails): Option[String] = {
    propertyDetails.title.map(_.titleNumber) match {
      case Some(x) if (!x.trim().isEmpty) => Some(x)
      case _ => None
    }
  }

  def getLineItems(propertyCalc: PropertyDetailsCalculated) = {
    implicit val localDateOrdering: Ordering[LocalDate] = Ordering.by(_.toDate.getTime)
    implicit val lineItemOrdering: Ordering[EtmpLineItems] = Ordering.by(_.dateFrom)

    val etmpReliefLineItems = propertyCalc.reliefPeriods.map(item =>
      EtmpLineItems(item.value, item.startDate, item.endDate, item.lineItemType, item.description)
    )
    val etmpLiabilityLineItems = propertyCalc.liabilityPeriods.map(item =>
      EtmpLineItems(item.value, item.startDate, item.endDate, item.lineItemType, item.description)
    )
    (etmpLiabilityLineItems ++ etmpReliefLineItems).sorted
  }

  def getTaxAvoidanceScheme(propertyDetails: PropertyDetails): Option[String] = {
    propertyDetails.period.flatMap(x => x.taxAvoidanceScheme)
  }

  def getTaxAvoidancePromoterReference(propertyDetails: PropertyDetails): Option[String] = {
    propertyDetails.period.flatMap(x => x.taxAvoidancePromoterReference)
  }


  def getNinetyDayRuleApplies(propertyDetails: PropertyDetails): Boolean = {
    propertyDetails.value.flatMap(_.isNewBuild).fold(false)(a => a)
  }

  def populateBankDetails(propertyDetails: Option[PropertyDetails]): Option[PropertyDetails] = {
    import models.BankDetailsConversions._
    propertyDetails.map {
      foundDetails =>

        foundDetails.bankDetails.flatMap(_.protectedBankDetails) match {
          case Some(y) =>
            val newBankDetails = foundDetails.bankDetails.map(_.copy(bankDetails = Some(y), protectedBankDetails = None))
            foundDetails.copy(bankDetails = newBankDetails)
          case None => foundDetails
        }
    }
  }

  def disposeLineItems(periodKey: String, lineItems: Seq[FormBundleProperty], dateOfDisposal: Option[LocalDate]): Seq[EtmpLineItems] = {
    val disposalEndDate = new LocalDate(s"$periodKey-3-31").plusYears(1)

    def createDisposeLineItem(item: FormBundleProperty): Seq[EtmpLineItems] = {
      dateOfDisposal match {
        case Some(x) if (x.isAfter(disposalEndDate)) =>
          throw new InternalServerException("[PropertyDetailsUtils][disposeLineItems] - Disposal Date is after the end of the period")
        case Some(x) if (x.equals(item.dateFrom)) => {
          List(EtmpLineItems(propertyValue = item.propertyValue,
            dateFrom = item.dateFrom,
            dateTo = disposalEndDate,
            `type` = TypeDeEnveloped, None
          ))
        }
        case Some(x) if (x.isBefore(item.dateFrom)) => Nil
        case Some(x) if (x.isAfter(item.dateFrom) && !x.isAfter(item.dateTo)) =>
          List(
            EtmpLineItems(propertyValue = item.propertyValue,
              dateFrom = item.dateFrom,
              dateTo = x.plusDays(-1),
              `type` = item.`type`,
              reliefDescription = item.reliefDescription
            ),
            EtmpLineItems(propertyValue = item.propertyValue,
              dateFrom = x,
              dateTo = disposalEndDate,
              `type` = TypeDeEnveloped, None
            )
          )
        case _ =>
          List(EtmpLineItems(propertyValue = item.propertyValue,
            dateFrom = item.dateFrom,
            dateTo = item.dateTo,
            `type` = item.`type`,
            reliefDescription = item.reliefDescription
          ))
      }
    }

    lineItems.flatMap(createDisposeLineItem(_))
  }

  private def calculateEarliestDate(firstOccDate: Option[LocalDate], localAuthRegDate: Option[LocalDate]): Option[LocalDate] = {
    (firstOccDate, localAuthRegDate) match {
      case (Some(a), Some(b)) if (a.isBefore(b) || a.isEqual(b)) => Some(a)
      case (Some(a), Some(b)) => Some(b)
      case _ => None
    }
  }
}
