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

package builders

import models._
import java.time.LocalDate
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite

object PropertyDetailsBuilder  extends PlaySpec with GuiceOneServerPerSuite {

  def getPropertyDetailsValueRevalued: Option[PropertyDetailsValue] = {
    Some(new PropertyDetailsValue(anAcquisition = Some(true),
      isPropertyRevalued = Some(true),
      revaluedValue = Some(BigDecimal(1111.11)),
      revaluedDate = Some(LocalDate.of(1970,1,1))
    ))
  }

  def getPropertyDetailsValueFull: Option[PropertyDetailsValue] = {

    Some(new PropertyDetailsValue(
      anAcquisition = Some(true),
      isPropertyRevalued = Some(true),
      revaluedValue = Some(BigDecimal(1111.11)),
      revaluedDate = Some(LocalDate.of(1970,1,1)),
      isOwnedBeforePolicyYear = Some(true),
      ownedBeforePolicyYearValue = Some(BigDecimal(1111.11)),
      isNewBuild =  Some(true),
      newBuildValue = Some(BigDecimal(1111.11)),
      newBuildDate = Some(LocalDate.of(1970,1,1)),
      notNewBuildValue = Some(BigDecimal(1111.11)),
      notNewBuildDate = Some(LocalDate.of(1970,1,1)),
      isValuedByAgent =  Some(true)
    ))
  }

  def getPropertyDetailsPeriod: Option[PropertyDetailsPeriod] = {
    Some(new PropertyDetailsPeriod(isFullPeriod = Some(true)))
  }

  def getPropertyDetailsPeriodFull(periodKey : Int = 2015): Option[PropertyDetailsPeriod] = {
    val liabilityPeriods = List(LineItem("Liability",LocalDate.of(periodKey, 4, 1), LocalDate.of(periodKey, 8, 31)))
    val reliefPeriods = List(LineItem("Relief",LocalDate.of(periodKey, 9, 1), LocalDate.of(periodKey+1, 3, 31), Some("Relief")))
    Some(new PropertyDetailsPeriod(
      isFullPeriod = Some(false),
      liabilityPeriods = liabilityPeriods,
      reliefPeriods = reliefPeriods,
      isTaxAvoidance =  Some(true),
      taxAvoidanceScheme =  Some("taxAvoidanceScheme"),
      taxAvoidancePromoterReference =  Some("taxAvoidancePromoterReference"),
      supportingInfo = Some("supportingInfo"),
      isInRelief =  Some(true)
    ))
  }

  def getPropertyDetailsTitle: Option[PropertyDetailsTitle] = {
    Some(new PropertyDetailsTitle("titleNo"))
  }

  def getPropertyDetailsAddress(postCode: Option[String] = None): PropertyDetailsAddress = {
    new PropertyDetailsAddress("addr1", "addr2", Some("addr3"), Some("addr4"), postCode)
  }

  def getPropertyDetailsCalculated(liabilityAmount: Option[BigDecimal] = None): Option[PropertyDetailsCalculated] = {
    val liabilityPeriods = List(CalculatedPeriod(BigDecimal(1111.11), LocalDate.of(2015,4,1), LocalDate.of(2015,8,31), "Liability"))
    val reliefPeriods = List(CalculatedPeriod(BigDecimal(1111.11),LocalDate.of(2015,9,1), LocalDate.of(2016,3,31), "Relief", Some("Relief")))
    Some(new PropertyDetailsCalculated(liabilityAmount = liabilityAmount,
      liabilityPeriods = liabilityPeriods,
      reliefPeriods = reliefPeriods,
      professionalValuation = Some(true),
      valuationDateToUse = Some(LocalDate.of(1970,1,1))
    ))
  }

  def getPropertyDetailsCalculatedNoValuation(liabilityAmount: Option[BigDecimal] = None): Option[PropertyDetailsCalculated] = {
    val liabilityPeriods = List(CalculatedPeriod(BigDecimal(1111.11), LocalDate.of(2015,4,1), LocalDate.of(2015,8,31), "Liability"))
    val reliefPeriods = Nil
    Some(new PropertyDetailsCalculated(liabilityAmount = liabilityAmount,
      liabilityPeriods = liabilityPeriods,
      reliefPeriods = reliefPeriods,
      professionalValuation = Some(false),
      acquistionDateToUse = Some(LocalDate.of(2015,4,1))
    ))
  }

  def getPropertyDetailsNoValuation(id: String,
                                    postCode: Option[String] = None,
                                    liabilityAmount: Option[BigDecimal] = None): PropertyDetails = {
    PropertyDetails(atedRefNo = "ated-ref-123",
      id = id,
      periodKey = 2015,
      addressProperty = getPropertyDetailsAddress(postCode),
      title = getPropertyDetailsTitle,
      value = getPropertyDetailsValueRevalued,
      period = getPropertyDetailsPeriod,
      calculated = getPropertyDetailsCalculatedNoValuation(liabilityAmount))
  }

  def getPropertyDetails(id: String,
                         postCode: Option[String] = None,
                         liabilityAmount: Option[BigDecimal] = None
                        ): PropertyDetails = {
    PropertyDetails(atedRefNo = s"ated-ref-$id",
      id = id,
      periodKey = 2015,
      addressProperty = getPropertyDetailsAddress(postCode),
      title = getPropertyDetailsTitle,
      value = getPropertyDetailsValueRevalued,
      period = getPropertyDetailsPeriod,
      calculated = getPropertyDetailsCalculated(liabilityAmount))
  }

  def getFullPropertyDetails(id: String,
                         postCode: Option[String] = None,
                         liabilityAmount: Option[BigDecimal] = None
                        ): PropertyDetails = {
    PropertyDetails(atedRefNo = "ated-ref-123",
      id = id,
      periodKey = 2015,
      addressProperty = getPropertyDetailsAddress(postCode),
      title = getPropertyDetailsTitle,
      value = getPropertyDetailsValueFull,
      period = getPropertyDetailsPeriodFull(),
      calculated = getPropertyDetailsCalculated(liabilityAmount))
    
  }

  def getFullPropertyDetailsNoReliefs(id: String,
                             postCode: Option[String] = None,
                             liabilityAmount: Option[BigDecimal] = None
                            ): PropertyDetails = {

    val noReliefPeriods = getPropertyDetailsPeriodFull().map(_.copy(reliefPeriods = Nil))
    val noCalculatedReliefPeriods = getPropertyDetailsCalculated(liabilityAmount).map(_.copy(reliefPeriods = Nil))
    PropertyDetails(atedRefNo = "ated-ref-123",
      id = id,
      periodKey = 2015,
      addressProperty = getPropertyDetailsAddress(postCode),
      title = getPropertyDetailsTitle,
      value = getPropertyDetailsValueFull,
      period = noReliefPeriods,
      calculated = noCalculatedReliefPeriods)
  }

  def getFullPropertyDetailsNoLiabilities(id: String,
                             postCode: Option[String] = None,
                             liabilityAmount: Option[BigDecimal] = None
                            ): PropertyDetails = {
    val noLiabilitiesPeriods = getPropertyDetailsPeriodFull().map(_.copy(liabilityPeriods = Nil))
    val noCalculatedLiabilities = getPropertyDetailsCalculated(liabilityAmount).map(_.copy(liabilityPeriods = Nil))
    PropertyDetails(atedRefNo = "ated-ref-123",
      id = id,
      periodKey = 2015,
      addressProperty = getPropertyDetailsAddress(postCode),
      title = getPropertyDetailsTitle,
      value = getPropertyDetailsValueFull,
      period = noLiabilitiesPeriods,
      calculated = noCalculatedLiabilities)

  }
}
