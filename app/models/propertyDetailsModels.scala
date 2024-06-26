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

package models

import java.time.{ZonedDateTime, ZoneId, LocalDate}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Writes._
import play.api.libs.json.Reads._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

case class PropertyDetailsAddress(line_1: String, line_2: String, line_3: Option[String], line_4: Option[String],
                                  postcode: Option[String] = None) {
  override def toString = {

    val line3display = line_3.map(line3 => s", $line3, ").fold("")(x => x)
    val line4display = line_4.map(line4 => s"$line4, ").fold("")(x => x)
    val postcodeDisplay = postcode.map(postcode1 => s"$postcode1").fold("")(x => x)
    s"$line_1, $line_2 $line3display$line4display$postcodeDisplay"
  }
}

object PropertyDetailsAddress {
  implicit val formats: OFormat[PropertyDetailsAddress] = Json.format[PropertyDetailsAddress]
}

case class PropertyDetailsTitle(titleNumber: String)

object PropertyDetailsTitle {
  implicit val formats: OFormat[PropertyDetailsTitle] = Json.format[PropertyDetailsTitle]
}


case class PropertyDetailsValue(anAcquisition: Option[Boolean] = None,
                                isPropertyRevalued: Option[Boolean] = None,
                                revaluedValue: Option[BigDecimal] = None,
                                revaluedDate: Option[LocalDate] = None,
                                partAcqDispDate: Option[LocalDate] = None,
                                isOwnedBeforePolicyYear: Option[Boolean] = None,
                                ownedBeforePolicyYearValue: Option[BigDecimal] = None,
                                isNewBuild: Option[Boolean] = None,
                                newBuildValue: Option[BigDecimal] = None,
                                isBuildDateKnown: Option[Boolean] = None,
                                newBuildDate: Option[LocalDate] = None,
                                isLocalAuthRegDateKnown: Option[Boolean] = None,
                                localAuthRegDate: Option[LocalDate] = None,
                                notNewBuildValue: Option[BigDecimal] = None,
                                notNewBuildDate: Option[LocalDate] = None,
                                isValuedByAgent: Option[Boolean] = None,
                                hasValueChanged: Option[Boolean] = None
                               )

object PropertyDetailsValue {

  implicit val propertyDetailsValueReads: Reads[PropertyDetailsValue] = (
    (JsPath \ "anAcquisition").readNullable[Boolean] and
      (JsPath \ "isPropertyRevalued").readNullable[Boolean] and
      (JsPath \ "revaluedValue").readNullable[BigDecimal] and
      (JsPath \ "revaluedDate").readNullable[LocalDate] and
      (JsPath \ "partAcqDispDate").readNullable[LocalDate] and
      (JsPath \ "isOwnedBeforePolicyYear").read[Boolean].map(Option(_)).orElse((JsPath \ "isOwnedBefore2012").readNullable[Boolean]) and
      (JsPath \ "ownedBeforePolicyYearValue").read[BigDecimal].map(Option(_)).orElse((JsPath \ "ownedBefore2012Value").readNullable[BigDecimal]) and
      (JsPath \ "isNewBuild").readNullable[Boolean] and
      (JsPath \ "newBuildValue").readNullable[BigDecimal] and
      (JsPath \ "isBuildDateKnown").readNullable[Boolean] and
      (JsPath \ "newBuildDate").readNullable[LocalDate] and
      (JsPath \ "isLocalAuthRegDateKnown").readNullable[Boolean] and
      (JsPath \ "localAuthRegDate").readNullable[LocalDate] and
      (JsPath \ "notNewBuildValue").readNullable[BigDecimal] and
      (JsPath \ "notNewBuildDate").readNullable[LocalDate] and
      (JsPath \ "isValuedByAgent").readNullable[Boolean] and
      (JsPath \ "hasValueChanged").readNullable[Boolean]
    )(PropertyDetailsValue.apply _)

  implicit val propertyDetailsValueWrites: OWrites[PropertyDetailsValue]=Json.writes[PropertyDetailsValue]
}

case class PropertyDetailsAcquisition(anAcquisition: Option[Boolean] = None)

object PropertyDetailsAcquisition {
  implicit val formats: OFormat[PropertyDetailsAcquisition] = Json.format[PropertyDetailsAcquisition]
}

case class HasValueChanged(hasValueChanged: Option[Boolean] = None)

object HasValueChanged {
  implicit val formats: OFormat[HasValueChanged] = Json.format[HasValueChanged]
}

case class PropertyDetailsRevalued(isPropertyRevalued: Option[Boolean] = None,
                                   revaluedValue: Option[BigDecimal] = None,
                                   revaluedDate: Option[LocalDate] = None,
                                   partAcqDispDate: Option[LocalDate] = None)

object PropertyDetailsRevalued {
  implicit val formats: OFormat[PropertyDetailsRevalued] = Json.format[PropertyDetailsRevalued]
}

sealed trait OwnedBeforePolicyYear

case object IsOwnedBefore2012 extends OwnedBeforePolicyYear

case object IsOwnedBefore2017 extends OwnedBeforePolicyYear

case object IsOwnedBefore2022 extends OwnedBeforePolicyYear

case object NotOwnedBeforePolicyYear extends OwnedBeforePolicyYear

case class PropertyDetailsOwnedBefore(isOwnedBeforePolicyYear: Option[Boolean] = None,
                                      ownedBeforePolicyYearValue: Option[BigDecimal] = None) {

  def policyYear(periodKey: Int)(implicit servicesConfig: ServicesConfig) : OwnedBeforePolicyYear = {
    val valuation2022Active: Boolean = servicesConfig.getBoolean("feature.valuation2022DateActive")

    isOwnedBeforePolicyYear match {
      case Some(true) => periodKey match {
        case p if valuation2022Active && p >= 2023 => IsOwnedBefore2022
        case p if p >= 2018 && (!valuation2022Active || p < 2023) => IsOwnedBefore2017
        case p if p >= 2013 && p < 2018 => IsOwnedBefore2012
        case _ => throw new RuntimeException("Invalid liability period")
      }
      case _ => NotOwnedBeforePolicyYear
    }
  }
}

object PropertyDetailsOwnedBefore {
  implicit val formats: OFormat[PropertyDetailsOwnedBefore] = Json.format[PropertyDetailsOwnedBefore]
}

case class PropertyDetailsProfessionallyValued(isValuedByAgent: Option[Boolean] = None)

object PropertyDetailsProfessionallyValued {
  implicit val formats: OFormat[PropertyDetailsProfessionallyValued] = Json.format[PropertyDetailsProfessionallyValued]
}

case class PropertyDetailsNewBuild(
                                    isNewBuild: Option[Boolean] = None,
                                    newBuildValue: Option[BigDecimal] = None,
                                    newBuildDate: Option[LocalDate] = None,
                                    localAuthRegDate: Option[LocalDate] = None,
                                    notNewBuildValue: Option[BigDecimal] = None,
                                    notNewBuildDate: Option[LocalDate] = None
                                  )

object PropertyDetailsNewBuild {
  implicit val formats: OFormat[PropertyDetailsNewBuild] = Json.format[PropertyDetailsNewBuild]
}

case class PropertyDetailsIsNewBuild(isNewBuild: Option[Boolean] = None)

object PropertyDetailsIsNewBuild {
  implicit val formats: OFormat[PropertyDetailsIsNewBuild] = Json.format[PropertyDetailsIsNewBuild]
}

case class PropertyDetailsNewBuildDates(newBuildOccupyDate: Option[LocalDate] = None,
                                        newBuildRegisterDate: Option[LocalDate] = None)

object PropertyDetailsNewBuildDates {
  implicit val formats: OFormat[PropertyDetailsNewBuildDates] = Json.format[PropertyDetailsNewBuildDates]
}

case class PropertyDetailsNewBuildValue(newBuildValue: Option[BigDecimal] = None)

object PropertyDetailsNewBuildValue {
  implicit val formats: OFormat[PropertyDetailsNewBuildValue] = Json.format[PropertyDetailsNewBuildValue]
}

case class PropertyDetailsValueOnAcquisition(acquiredValue: Option[BigDecimal] = None)

object PropertyDetailsValueOnAcquisition {
  implicit val formats: OFormat[PropertyDetailsValueOnAcquisition] = Json.format[PropertyDetailsValueOnAcquisition]
}

case class PropertyDetailsDateOfAcquisition(acquiredDate: Option[LocalDate] = None)

object PropertyDetailsDateOfAcquisition {
  implicit val formats: OFormat[PropertyDetailsDateOfAcquisition] = Json.format[PropertyDetailsDateOfAcquisition]
}

case class PropertyDetailsFullTaxPeriod(isFullPeriod: Option[Boolean] = None)

object PropertyDetailsFullTaxPeriod {
  implicit val formats: OFormat[PropertyDetailsFullTaxPeriod] = Json.format[PropertyDetailsFullTaxPeriod]
}

case class PropertyDetailsDatesLiable(startDate: LocalDate,
                                      endDate: LocalDate)

object PropertyDetailsDatesLiable {
  implicit val formats: OFormat[PropertyDetailsDatesLiable] = Json.format[PropertyDetailsDatesLiable]
}

case class IsFullTaxPeriod(isFullPeriod: Boolean, datesLiable: Option[PropertyDetailsDatesLiable])

object IsFullTaxPeriod {
  implicit val formats: OFormat[IsFullTaxPeriod] = Json.format[IsFullTaxPeriod]
}


case class PeriodChooseRelief(reliefDescription: String)

object PeriodChooseRelief {
  implicit val formats: OFormat[PeriodChooseRelief] = Json.format[PeriodChooseRelief]
}


case class PropertyDetailsDatesInRelief(startDate: LocalDate,
                                        endDate: LocalDate,
                                        description: Option[String] = None)

object PropertyDetailsDatesInRelief {
  implicit val formats: OFormat[PropertyDetailsDatesInRelief] = Json.format[PropertyDetailsDatesInRelief]
}


case class PropertyDetailsInRelief(isInRelief: Option[Boolean] = None)


object PropertyDetailsInRelief {
  implicit val formats: OFormat[PropertyDetailsInRelief] = Json.format[PropertyDetailsInRelief]
}

case class PropertyDetailsTaxAvoidance(isTaxAvoidance: Option[Boolean] = None,
                                       taxAvoidanceScheme: Option[String] = None,
                                       taxAvoidancePromoterReference: Option[String] = None)


object PropertyDetailsTaxAvoidance {
  implicit val formats: OFormat[PropertyDetailsTaxAvoidance] = Json.format[PropertyDetailsTaxAvoidance]
}

case class PropertyDetailsSupportingInfo(supportingInfo: String)


object PropertyDetailsSupportingInfo {
  implicit val formats: OFormat[PropertyDetailsSupportingInfo] = Json.format[PropertyDetailsSupportingInfo]
}

case class LineItem(lineItemType: String, startDate: LocalDate, endDate: LocalDate, description: Option[String] = None)

object LineItem {
  implicit val formats: OFormat[LineItem] = Json.format[LineItem]
}

case class PropertyDetailsPeriod(isFullPeriod: Option[Boolean] = None,
                                 isTaxAvoidance: Option[Boolean] = None,
                                 taxAvoidanceScheme: Option[String] = None,
                                 taxAvoidancePromoterReference: Option[String] = None,
                                 supportingInfo: Option[String] = None,
                                 isInRelief: Option[Boolean] = None,
                                 liabilityPeriods: List[LineItem] = Nil,
                                 reliefPeriods: List[LineItem] = Nil)

object PropertyDetailsPeriod {
  implicit val formats: OFormat[PropertyDetailsPeriod] = Json.format[PropertyDetailsPeriod]
}

case class CalculatedPeriod(value: BigDecimal,
                            startDate: LocalDate,
                            endDate: LocalDate,
                            lineItemType: String,
                            description: Option[String] = None
                           )

object CalculatedPeriod {
  implicit val formats: OFormat[CalculatedPeriod] = Json.format[CalculatedPeriod]
}

case class PropertyDetailsCalculated(valuationDateToUse: Option[LocalDate] = None,
                                     acquistionValueToUse: Option[BigDecimal] = None,
                                     acquistionDateToUse: Option[LocalDate] = None,
                                     professionalValuation: Option[Boolean] = Some(false),
                                     liabilityPeriods: Seq[CalculatedPeriod] = Nil,
                                     reliefPeriods: Seq[CalculatedPeriod] = Nil,
                                     liabilityAmount: Option[BigDecimal] = None,
                                     amountDueOrRefund: Option[BigDecimal] = None,
                                     timeStamp: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC")))

object PropertyDetailsCalculated {
  implicit val formats: OFormat[PropertyDetailsCalculated] = Json.format[PropertyDetailsCalculated]
}
