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
import uk.gov.hmrc.http.HeaderCarrier

object ReliefUtils extends ReliefUtils

trait ReliefUtils extends ReliefConstants {

  implicit class RichBoolean(val b: Boolean) {
    final def option[A](a: => A): Option[A] = if (b) Some(a) else None
  }

  def convertToSubmitReturnsRequest(atedReferenceNo: String, reliefs: Option[ReliefsTaxAvoidance], agentRefNo: Option[String] = None)
                                   (implicit hc: HeaderCarrier): Option[SubmitEtmpReturnsRequest] = {
    reliefs.flatMap { reliefOptions =>
      val etmpReliefs = createEtmpReliefs(reliefOptions.periodKey, reliefOptions)

      etmpReliefs match {
        case Nil => None
        case reliefList => Some(SubmitEtmpReturnsRequest(
          acknowledgementReference = SessionUtils.getUniqueAckNo,
          agentReferenceNumber = agentRefNo,
          reliefReturns = Some(etmpReliefs)))
      }
    }
  }

  def createEtmpRelief(periodKey: Int, reliefDescription: String, reliefStartDate: Option[LocalDate],
                       taxAvoidanceScheme: Option[String] = None,
                       taxAvoidancePromoterReference: Option[String] = None,
                       periodStartDate: LocalDate, periodEndDate: LocalDate): EtmpReliefReturns = {
    EtmpReliefReturns(periodKey = periodKey.toString, taxAvoidanceScheme = taxAvoidanceScheme, taxAvoidancePromoterReference = taxAvoidancePromoterReference ,reliefDescription = reliefDescription,
      reliefStartDate = reliefStartDate.fold(periodStartDate)(x => x), reliefEndDate = periodEndDate)
  }

  private def createEtmpReliefs(periodKey: Int, reliefsTaxAvoid: ReliefsTaxAvoidance): Seq[EtmpReliefReturns] = {

    Seq(
      reliefsTaxAvoid.reliefs.rentalBusiness.option(createEtmpRelief(periodKey, RentalBusinessDesc,
        reliefsTaxAvoid.reliefs.rentalBusinessDate,
        reliefsTaxAvoid.taxAvoidance.rentalBusinessScheme,
        reliefsTaxAvoid.taxAvoidance.rentalBusinessSchemePromoter,
        reliefsTaxAvoid.periodStartDate,
        reliefsTaxAvoid.periodEndDate)),
      reliefsTaxAvoid.reliefs.openToPublic.option(createEtmpRelief(periodKey, OpenToPublicDesc,
        reliefsTaxAvoid.reliefs.openToPublicDate,
        reliefsTaxAvoid.taxAvoidance.openToPublicScheme,
        reliefsTaxAvoid.taxAvoidance.openToPublicSchemePromoter,
        reliefsTaxAvoid.periodStartDate,
        reliefsTaxAvoid.periodEndDate)),
      reliefsTaxAvoid.reliefs.propertyDeveloper.option(createEtmpRelief(periodKey, PropDevDesc,
        reliefsTaxAvoid.reliefs.propertyDeveloperDate,
        reliefsTaxAvoid.taxAvoidance.propertyDeveloperScheme,
        reliefsTaxAvoid.taxAvoidance.propertyDeveloperSchemePromoter,
        reliefsTaxAvoid.periodStartDate,
        reliefsTaxAvoid.periodEndDate)),
      reliefsTaxAvoid.reliefs.propertyTrading.option(createEtmpRelief(periodKey, PropTradingDesc,
        reliefsTaxAvoid.reliefs.propertyTradingDate,
        reliefsTaxAvoid.taxAvoidance.propertyTradingScheme,
        reliefsTaxAvoid.taxAvoidance.propertyTradingSchemePromoter,
        reliefsTaxAvoid.periodStartDate,
        reliefsTaxAvoid.periodEndDate)),
      reliefsTaxAvoid.reliefs.lending.option(createEtmpRelief(periodKey, LendingDesc,
        reliefsTaxAvoid.reliefs.lendingDate,
        reliefsTaxAvoid.taxAvoidance.lendingScheme,
        reliefsTaxAvoid.taxAvoidance.lendingSchemePromoter,
        reliefsTaxAvoid.periodStartDate,
        reliefsTaxAvoid.periodEndDate)),
      reliefsTaxAvoid.reliefs.employeeOccupation.option(createEtmpRelief(periodKey, EmpOccDesc,
        reliefsTaxAvoid.reliefs.employeeOccupationDate,
        reliefsTaxAvoid.taxAvoidance.employeeOccupationScheme,
        reliefsTaxAvoid.taxAvoidance.employeeOccupationSchemePromoter,
        reliefsTaxAvoid.periodStartDate,
        reliefsTaxAvoid.periodEndDate)),
      reliefsTaxAvoid.reliefs.farmHouses.option(createEtmpRelief(periodKey, FarmHouseDesc,
        reliefsTaxAvoid.reliefs.farmHousesDate,
        reliefsTaxAvoid.taxAvoidance.farmHousesScheme,
        reliefsTaxAvoid.taxAvoidance.farmHousesSchemePromoter,
        reliefsTaxAvoid.periodStartDate,
        reliefsTaxAvoid.periodEndDate)),
      reliefsTaxAvoid.reliefs.socialHousing.option(createEtmpRelief(periodKey, SocialHouseDesc,
        reliefsTaxAvoid.reliefs.socialHousingDate,
        reliefsTaxAvoid.taxAvoidance.socialHousingScheme,
        reliefsTaxAvoid.taxAvoidance.socialHousingSchemePromoter,
        reliefsTaxAvoid.periodStartDate,
        reliefsTaxAvoid.periodEndDate)),
      reliefsTaxAvoid.reliefs.equityRelease.option(createEtmpRelief(periodKey, EquityReleaseDesc,
        reliefsTaxAvoid.reliefs.equityReleaseDate,
        reliefsTaxAvoid.taxAvoidance.equityReleaseScheme,
        reliefsTaxAvoid.taxAvoidance.equityReleaseSchemePromoter,
        reliefsTaxAvoid.periodStartDate,
        reliefsTaxAvoid.periodEndDate))
    ).flatten
  }

  def convertDraftReliefsToDraftDescription(x: ReliefsTaxAvoidance): Seq[String] = {
    Seq(
      x.reliefs.employeeOccupation.option(EmpOcc),
      x.reliefs.farmHouses.option(FarmHouse),
      x.reliefs.lending.option(Lending),
      x.reliefs.openToPublic.option(OpenToPublic),
      x.reliefs.propertyDeveloper.option(PropDev),
      x.reliefs.propertyTrading.option(PropTrading),
      x.reliefs.rentalBusiness.option(RentalBusiness),
      x.reliefs.socialHousing.option(SocialHouse),
      x.reliefs.socialHousing.option(EquityRelease)
    ).flatten
  }

  def atedReliefNameForEtmpReliefName(etmpReliefName: String): String = {
    val reliefsDescription = Map(
      RentalBusinessDesc -> RentalBusiness,
      OpenToPublicDesc -> OpenToPublic,
      PropDevDesc -> PropDev,
      PropTradingDesc -> PropTrading,
      LendingDesc -> Lending,
      EmpOccDesc -> EmpOcc,
      FarmHouseDesc -> FarmHouse,
      SocialHouseDesc -> SocialHouse,
      EquityReleaseDesc -> EquityRelease
    )
    reliefsDescription(etmpReliefName)
  }


}
