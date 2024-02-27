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

package utils

import builders.PropertyDetailsBuilder
import models._
import java.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import org.scalatestplus.play.PlaySpec


class PropertyDetailsUtilsSpec extends PlaySpec with ReliefConstants with MockitoSugar with BeforeAndAfterEach {

  val periodStartDate = LocalDate.of(2015, 6, 2)
  val periodEndDate = LocalDate.of(2016, 1, 10)
  val periodKey = 2015

  implicit val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
  override def beforeEach(): Unit = {
    reset(mockServicesConfig)
  }

  "check the period start date" must {


    "Check that the revalued values are returned if the setting are correct" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(true),
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 2, 2)),
        partAcqDispDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(true),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )
      lazy val propertyDetailsPeriod = basicPropertyDetails.period.map(_.copy(isFullPeriod = Some(false)))
      lazy val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey, period = propertyDetailsPeriod))

      updatePropertyDetails.valuationDateToUse must be(Some(LocalDate.of(1970, 2, 2)))
      updatePropertyDetails.liabilityPeriods.headOption.get.startDate must be(LocalDate.of(2015, 4, 1))
      updatePropertyDetails.liabilityPeriods.headOption.get.value must be(BigDecimal(1111.11))
      updatePropertyDetails.liabilityPeriods.headOption.get.endDate must be(LocalDate.of(2015, 8, 31))
    }

    "Check that the revalued values are returned if the setting are correct and this is for a full period" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(true),
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 2, 2)),
        partAcqDispDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(true),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )

      lazy val propertyDetailsPeriod = basicPropertyDetails.period.map(_.copy(isFullPeriod = Some(true), liabilityPeriods = Nil, reliefPeriods = Nil))
      lazy val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey, period = propertyDetailsPeriod))

      updatePropertyDetails.valuationDateToUse must be(Some(LocalDate.of(1970, 2, 2)))
      updatePropertyDetails.liabilityPeriods.headOption.get.startDate must be(LocalDate.of(2015, 4, 1))
      updatePropertyDetails.liabilityPeriods.headOption.get.value must be(BigDecimal(1111.11))
      updatePropertyDetails.liabilityPeriods.headOption.get.endDate must be(LocalDate.of(2016, 3, 31))
    }

    "ignore the is full period if we have a period" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(true),
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 2, 2)),
        partAcqDispDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(true),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )

      lazy val propertyDetailsPeriod = basicPropertyDetails.period.map(_.copy(isFullPeriod = Some(true)))
      lazy val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey, period = propertyDetailsPeriod))

      updatePropertyDetails.valuationDateToUse must be(Some(LocalDate.of(1970, 2, 2)))
      updatePropertyDetails.liabilityPeriods.headOption.get.value must be(BigDecimal(1111.11))
      updatePropertyDetails.liabilityPeriods.headOption.get.startDate must be(LocalDate.of(2015, 4, 1))
      updatePropertyDetails.liabilityPeriods.headOption.get.endDate must be(LocalDate.of(2015, 8, 31))
    }

    "Check that the revalued values have no dates if we have no periods set" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(true),
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 2, 2)),
        partAcqDispDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(true),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )

      val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey, period = None))


      updatePropertyDetails.valuationDateToUse must be(Some(LocalDate.of(1970, 2, 2)))
      updatePropertyDetails.liabilityPeriods.isEmpty must be(true)
      updatePropertyDetails.reliefPeriods.isEmpty must be(true)
    }


    "Check that the owned before values are returned if the setting are correct" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(false),
        isPropertyRevalued = None,
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(true),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )

      val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey))

      updatePropertyDetails.valuationDateToUse must be(Some(LocalDate.of(2012, 4, 1)))
      updatePropertyDetails.liabilityPeriods.headOption.get.value must be(BigDecimal(22222.22))
    }

    "Check that the new build values are returned if the setting are correct" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(false),
        isPropertyRevalued = None,
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(false),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        localAuthRegDate = Some(LocalDate.of(1971, 1, 5)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )

      val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey))

      updatePropertyDetails.valuationDateToUse must be(Some(LocalDate.of(1971, 1, 1)))
      updatePropertyDetails.liabilityPeriods.headOption.get.value must be(BigDecimal(33333.33))
    }

    "Check that the not new build values are returned if the setting are correct" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(false),
        isPropertyRevalued = None,
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(false),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(false),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )

      val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey))

      updatePropertyDetails.valuationDateToUse must be(Some(LocalDate.of(1972, 1, 1)))
      updatePropertyDetails.liabilityPeriods.headOption.get.value must be(BigDecimal(44444.44))
    }


    "Check that the  acquisition values are returned for a new build" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(false),
        isPropertyRevalued = None,
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(false),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        localAuthRegDate = Some(LocalDate.of(1971, 1, 5)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )

      val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey))

      updatePropertyDetails.acquistionValueToUse must be(Some(BigDecimal(33333.33)))
      updatePropertyDetails.acquistionDateToUse must be(Some(LocalDate.of(1971, 1, 1)))
    }


    "acquisition date returned for a new build is None when no new dates entered" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(false),
        isPropertyRevalued = None,
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(false),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(33333.33)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )

      val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey))

      updatePropertyDetails.acquistionValueToUse must be(Some(BigDecimal(33333.33)))
      updatePropertyDetails.acquistionDateToUse must be(None)
    }

    "Check that the  acquisition values are returned for an old build" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(false),
        isPropertyRevalued = None,
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(false),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(false),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = None
      )

      val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey))

      updatePropertyDetails.acquistionValueToUse must be(Some(BigDecimal(44444.44)))
      updatePropertyDetails.acquistionDateToUse must be(Some(LocalDate.of(1972, 1, 1)))
    }

    "Override the valuation date if we have it populated" in {
      lazy val basicPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("postCode"))

      lazy val propertyDetailsValue = new PropertyDetailsValue(anAcquisition = Some(true),
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(1111.11)),
        revaluedDate = Some(LocalDate.of(1970, 1, 1)),
        isOwnedBeforePolicyYear = Some(true),
        ownedBeforePolicyYearValue = Some(BigDecimal(22222.22)),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(33333.33)),
        newBuildDate = Some(LocalDate.of(1971, 1, 1)),
        notNewBuildValue = Some(BigDecimal(44444.44)),
        notNewBuildDate = Some(LocalDate.of(1972, 1, 1)),
        isValuedByAgent = Some(true),
        partAcqDispDate = Some(LocalDate.of(1975, 1, 1))
      )

      val updatePropertyDetails = PropertyDetailsUtils.propertyDetailsCalculated(basicPropertyDetails.copy(value = Some(propertyDetailsValue), periodKey = periodKey))


      updatePropertyDetails.valuationDateToUse must be(Some(LocalDate.of(2012, 4, 1)))
      updatePropertyDetails.liabilityPeriods.headOption.get.value must be(BigDecimal(1111.11))
    }

  }

  "getTitleNumber" must {
    "returns some(titleNumber), if hasTitle is true" in {
      lazy val p = PropertyDetails(atedRefNo = "ated-ref-1", id = "1", periodKey = 2015,
        addressProperty = PropertyDetailsAddress(line_1 = "line1", line_2 = "line2", None, None),
        title = Some(PropertyDetailsTitle(titleNumber = "123456")))
      PropertyDetailsUtils.getTitleNumber(propertyDetails = p) must be(p.title.map(_.titleNumber))
    }
    "returns None, if hasTitle is false" in {
      val p = PropertyDetails(atedRefNo = "ated-ref-1", id = "1", periodKey = 2015, addressProperty = PropertyDetailsAddress(line_1 = "line1", line_2 = "line2", None, None), title = None)
      PropertyDetailsUtils.getTitleNumber(propertyDetails = p) must be(None)
    }
    "returns None, if hasTitle is empty" in {
      val p = PropertyDetails(atedRefNo = "ated-ref-1", id = "1", periodKey = 2015, addressProperty = PropertyDetailsAddress(line_1 = "line1", line_2 = "line2", None, None), title = Some(PropertyDetailsTitle(titleNumber = "")))
      PropertyDetailsUtils.getTitleNumber(propertyDetails = p) must be(None)
    }

  }

  "getAdditionalDetails" must {
    "returns some(additional), if we have a value" in {
      lazy val period = PropertyDetailsPeriod(supportingInfo = Some("testSupportingInfo"))
      lazy val p = PropertyDetails(atedRefNo = "ated-ref-1", id = "1", periodKey = 2015, addressProperty = PropertyDetailsAddress(line_1 = "line1", line_2 = "line2", None, None), title = None, period = Some(period))
      PropertyDetailsUtils.getAdditionalDetails(propertyDetails = p) must be(Some("testSupportingInfo"))
    }
    "returns None, if we have no additional details" in {
      lazy val period = PropertyDetailsPeriod(supportingInfo = None)
      lazy val p = PropertyDetails(atedRefNo = "ated-ref-1", id = "1", periodKey = 2015, addressProperty = PropertyDetailsAddress(line_1 = "line1", line_2 = "line2", None, None), title = None, period = Some(period))
      PropertyDetailsUtils.getAdditionalDetails(propertyDetails = p) must be(None)
    }
    "returns None, if additional details is empty" in {
      val period = PropertyDetailsPeriod(supportingInfo = Some(""))
      val p = PropertyDetails(atedRefNo = "ated-ref-1", id = "1", periodKey = 2015, addressProperty = PropertyDetailsAddress(line_1 = "line1", line_2 = "line2", None, None), title = None, period = Some(period))
      PropertyDetailsUtils.getAdditionalDetails(propertyDetails = p) must be(None)
    }

  }

  "getTaxAvoidanceScheme" must {
    "return tax avoidance if found" in {
      lazy val p = PropertyDetails(atedRefNo = "ated-ref-1", id = "1", periodKey = 2015,
        period = Some(PropertyDetailsPeriod(isFullPeriod = None, liabilityPeriods = Nil, reliefPeriods = Nil, isTaxAvoidance = Some(true), taxAvoidanceScheme = Some("12345678"), supportingInfo = None)),
        addressProperty = PropertyDetailsAddress(line_1 = "line1", line_2 = "line2", None, None),
        title = None)
      PropertyDetailsUtils.getTaxAvoidanceScheme(p) must be(Some("12345678"))
    }
  }


  "getAcquistionData" must {
    "return (None, None) if we have no data" in {
      val result = PropertyDetailsUtils.getAcquisitionData(None,periodKey)
      result._1.isDefined must be (false)
      result._2.isDefined must be (false)
    }
  }

  "getInitialValueForSubmission" must {
    "return (None, None) if we have no data" in {
      val result = PropertyDetailsUtils.getInitialValueForSubmission(None,periodKey)
      result.isDefined must be (false)
    }
  }

  "createLiabilityPeriods" must {
    val initialValue = BigDecimal(1000.00)
    val liability1 = LineItem("Liability",LocalDate.of(periodKey, 4, 1), LocalDate.of(periodKey, 8, 31))
    val liability2 = LineItem("Liability",LocalDate.of(periodKey, 9, 1), LocalDate.of(periodKey, 10, 31))
    val liability3 = LineItem("Liability",LocalDate.of(periodKey, 11, 1), LocalDate.of(periodKey+1, 1, 31))
    val liability4 = LineItem("Liability",LocalDate.of(periodKey+1, 2, 1), LocalDate.of(periodKey+1, 3, 31))

    "Return the line items with the default value if we have some" in {
      val expected = List(
        CalculatedPeriod(initialValue, liability1.startDate, liability1.endDate, liability1.lineItemType, liability1.description),
        CalculatedPeriod(initialValue, liability2.startDate, liability2.endDate, liability2.lineItemType, liability2.description),
        CalculatedPeriod(initialValue, liability3.startDate, liability3.endDate, liability3.lineItemType, liability3.description),
        CalculatedPeriod(initialValue, liability4.startDate, liability4.endDate, liability4.lineItemType, liability4.description)
      )

      val propertyDetails = getPropertyDetailsWithMultiplePeriods(List(liability1, liability2, liability3, liability4), Nil)
      val lineItems =  PropertyDetailsUtils.createLiabilityPeriods(periodKey, propertyDetails.period, initialValue)
      lineItems.size must be (4)
      lineItems must be (expected)
    }

    "Return the line items with the new value in all periods if the update date was earler than all periods" in {
      val updateValue = BigDecimal(2000.00)
      val updateDate = LocalDate.of(periodKey-2, 2, 10)
      val newValues = Some((updateDate, updateValue))

      val expected = List(
        CalculatedPeriod(updateValue, liability1.startDate, liability1.endDate, liability1.lineItemType, liability1.description),
        CalculatedPeriod(updateValue, liability2.startDate, liability2.endDate, liability2.lineItemType, liability2.description),
        CalculatedPeriod(updateValue, liability3.startDate, liability3.endDate, liability3.lineItemType, liability3.description),
        CalculatedPeriod(updateValue, liability4.startDate, liability4.endDate, liability4.lineItemType, liability4.description)
      )

      val propertyDetails = getPropertyDetailsWithMultiplePeriods(List(liability1, liability2, liability3, liability4), Nil)
      val lineItems =  PropertyDetailsUtils.createLiabilityPeriods(periodKey, propertyDetails.period, initialValue, newValues)
      lineItems.size must be (4)
      lineItems must be (expected)
    }

    "Return the line items with the default value for the first period, but set a new value for subsequent periods" in {
      val updateValue = BigDecimal(2000.00)
      val newValues = Some((liability2.startDate, updateValue))

      val expected = List(
        CalculatedPeriod(initialValue, liability1.startDate, liability1.endDate, liability1.lineItemType, liability1.description),
        CalculatedPeriod(updateValue, liability2.startDate, liability2.endDate, liability2.lineItemType, liability2.description),
        CalculatedPeriod(updateValue, liability3.startDate, liability3.endDate, liability3.lineItemType, liability3.description),
        CalculatedPeriod(updateValue, liability4.startDate, liability4.endDate, liability4.lineItemType, liability4.description)
      )

      val propertyDetails = getPropertyDetailsWithMultiplePeriods(List(liability1, liability2, liability3, liability4), Nil)
      val lineItems =  PropertyDetailsUtils.createLiabilityPeriods(periodKey, propertyDetails.period, initialValue, newValues)
      lineItems.size must be (4)
      lineItems must be (expected)
    }

    "Return the line items with the default value for the first two periods, but set a new value for subsequent periods" in {
      val updateValue = BigDecimal(2000.00)
      val newValues = Some((liability3.startDate, updateValue))

      val expected = List(
        CalculatedPeriod(initialValue, liability1.startDate, liability1.endDate, liability1.lineItemType, liability1.description),
        CalculatedPeriod(initialValue, liability2.startDate, liability2.endDate, liability2.lineItemType, liability2.description),
        CalculatedPeriod(updateValue, liability3.startDate, liability3.endDate, liability3.lineItemType, liability3.description),
        CalculatedPeriod(updateValue, liability4.startDate, liability4.endDate, liability4.lineItemType, liability4.description)
      )

      val propertyDetails = getPropertyDetailsWithMultiplePeriods(List(liability1, liability2, liability3, liability4), Nil)
      val lineItems =  PropertyDetailsUtils.createLiabilityPeriods(periodKey, propertyDetails.period, initialValue, newValues)
      lineItems.size must be (4)
      lineItems must be (expected)
    }

    "Return the line items with the default value falls within the first period, split this in two" in {
      val updateValue = BigDecimal(2000.00)
      val updateDate = LocalDate.of(periodKey, 5, 1)
      val newValues = Some((updateDate, updateValue))

      val expected = List(
        CalculatedPeriod(initialValue, liability1.startDate, updateDate.plusDays(-1), liability1.lineItemType, liability1.description),
        CalculatedPeriod(updateValue, updateDate, liability1.endDate, liability1.lineItemType, liability1.description),
        CalculatedPeriod(updateValue, liability2.startDate, liability2.endDate, liability2.lineItemType, liability2.description),
        CalculatedPeriod(updateValue, liability3.startDate, liability3.endDate, liability3.lineItemType, liability3.description),
        CalculatedPeriod(updateValue, liability4.startDate, liability4.endDate, liability4.lineItemType, liability4.description)
      )

      val propertyDetails = getPropertyDetailsWithMultiplePeriods(List(liability1, liability2, liability3, liability4), Nil)
      val lineItems =  PropertyDetailsUtils.createLiabilityPeriods(periodKey, propertyDetails.period, initialValue, newValues)
      lineItems.size must be (5)
      lineItems must be (expected)
    }

    "Return the line items with the default value falls within the final period, split this in two" in {
      val updateValue = BigDecimal(2000.00)
      val updateDate = LocalDate.of(periodKey+1, 2, 10)
      val newValues = Some((updateDate, updateValue))

      val expected = List(
        CalculatedPeriod(initialValue, liability1.startDate, liability1.endDate, liability1.lineItemType, liability1.description),
        CalculatedPeriod(initialValue, liability2.startDate, liability2.endDate, liability2.lineItemType, liability2.description),
        CalculatedPeriod(initialValue, liability3.startDate, liability3.endDate, liability3.lineItemType, liability3.description),
        CalculatedPeriod(initialValue, liability4.startDate, updateDate.plusDays(-1), liability4.lineItemType, liability4.description),
        CalculatedPeriod(updateValue, updateDate, liability4.endDate, liability4.lineItemType, liability4.description)
      )

      val propertyDetails = getPropertyDetailsWithMultiplePeriods(List(liability1, liability2, liability3, liability4), Nil)
      val lineItems =  PropertyDetailsUtils.createLiabilityPeriods(periodKey, propertyDetails.period, initialValue, newValues)
      lineItems.size must be (5)
      lineItems must be (expected)
    }

  }

  "createReliefPeriods" must {
    val initialValue = BigDecimal(1000.00)
    val relief1 = LineItem("Relief",LocalDate.of(periodKey, 4, 1), LocalDate.of(periodKey, 8, 31))
    val relief2 = LineItem("Relief",LocalDate.of(periodKey, 9, 1), LocalDate.of(periodKey, 10, 31))
    val relief3 = LineItem("Relief",LocalDate.of(periodKey, 11, 1), LocalDate.of(periodKey+1, 1, 31))
    val relief4 = LineItem("Relief",LocalDate.of(periodKey+1, 2, 1), LocalDate.of(periodKey+1, 3, 31))


    "Return the line items with the default value falls within the final period, split this in two" in {
      val updateValue = BigDecimal(2000.00)
      val updateDate = LocalDate.of(periodKey+1, 2, 10)
      val newValues = Some((updateDate, updateValue))

      val expected = List(
        CalculatedPeriod(initialValue, relief1.startDate, relief1.endDate, relief1.lineItemType, relief1.description),
        CalculatedPeriod(initialValue, relief2.startDate, relief2.endDate, relief2.lineItemType, relief2.description),
        CalculatedPeriod(initialValue, relief3.startDate, relief3.endDate, relief3.lineItemType, relief3.description),
        CalculatedPeriod(initialValue, relief4.startDate, updateDate.plusDays(-1), relief4.lineItemType, relief4.description),
        CalculatedPeriod(updateValue, updateDate, relief4.endDate, relief4.lineItemType, relief4.description)
      )

      val propertyDetails = getPropertyDetailsWithMultiplePeriods(Nil, List(relief1, relief2, relief3, relief4))
      val lineItems =  PropertyDetailsUtils.createReliefPeriods(propertyDetails.period, initialValue, newValues)
      lineItems.size must be (5)
      lineItems must be (expected)
    }

  }

  "getLineItemValues" must {
    "throw an exception if we have no value" in {
      val thrown = the[InternalServerException] thrownBy PropertyDetailsUtils.getLineItemValues(None, None)
      thrown.getMessage must include("[PropertyDetailsUtils][getLineItemValues] - No Value Found")
    }

    "only return the initial value if we have no change period" in {
      val result = PropertyDetailsUtils.getLineItemValues(None, Some(BigDecimal(55555.55)))
      result._1 must be (BigDecimal(55555.55))
      result._2.isDefined must be (false)
    }

    "not return a change of value if the value has stayed the same" in {
      val propertyDetailsValue = PropertyDetailsValue(
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(55555.55)),
        revaluedDate = Some(LocalDate.of(periodKey, 4, 1))
      )

      val (value, changeValueAndDate) = PropertyDetailsUtils.getLineItemValues(Some(propertyDetailsValue), Some(BigDecimal(55555.55)))
      value must be (BigDecimal(55555.55))
      changeValueAndDate.isDefined must be (false)
    }


    "return None if we only have the revalued date if that is all we have" in {
      val propertyDetailsValue = PropertyDetailsValue(
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(123.45)),
        revaluedDate = Some(LocalDate.of(periodKey, 4, 1))
      )

      val (value, changeValueAndDate) = PropertyDetailsUtils.getLineItemValues(Some(propertyDetailsValue), Some(BigDecimal(55555.55)))
      value must be (BigDecimal(55555.55))
      changeValueAndDate.isDefined must be (false)
    }

    "use the acquisition date if that is all we have" in {
      val propertyDetailsValue = PropertyDetailsValue(
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(123.45)),
        partAcqDispDate = Some(LocalDate.of(periodKey, 5, 1))
      )

      val (value, changeValueAndDate) = PropertyDetailsUtils.getLineItemValues(Some(propertyDetailsValue), Some(BigDecimal(55555.55)))
      value must be (BigDecimal(55555.55))
      changeValueAndDate.isDefined must be (true)
      changeValueAndDate.get._2 must be (BigDecimal(123.45))
      changeValueAndDate.get._1 must be (LocalDate.of(periodKey, 5, 1))
    }
  }
  def getPropertyDetailsWithMultiplePeriods(liabilityPeriods: List[LineItem], reliefPeriods: List[LineItem]) = {
    lazy val basicPropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("postCode"))
    val period = basicPropertyDetails.period.get.copy(reliefPeriods = reliefPeriods, liabilityPeriods = liabilityPeriods)
    basicPropertyDetails.copy(period = Some(period))
  }

  "getInitialValueForSubmission" must {
    "return None if we have no value object " in {
      PropertyDetailsUtils.getInitialValueForSubmission(None,periodKey).isDefined must be (false)
    }

    "return None we have a value object that isn't populated " in {
      PropertyDetailsUtils.getInitialValueForSubmission(Some(PropertyDetailsValue()),periodKey).isDefined must be (false)
    }

    "return the revalued value if this has been revalued but we has no build or ownedBefore answers " in {
      val propVal = PropertyDetailsValue(
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(1111.11)))

      PropertyDetailsUtils.getInitialValueForSubmission(Some(propVal),periodKey) must be (Some(BigDecimal(1111.11)))
    }

    "return the revalued value if this has been not benn revalued and we have no build or ownedBefore answers " in {
      val propVal = PropertyDetailsValue(
        isPropertyRevalued = Some(false),
        revaluedValue = Some(BigDecimal(1111.11)))

      PropertyDetailsUtils.getInitialValueForSubmission(Some(propVal),periodKey) must be (Some(BigDecimal(1111.11)))
    }


    "return the owned before value if this has been set, even if it's been revalued" in {
      val propVal = PropertyDetailsValue(isOwnedBeforePolicyYear = Some(true),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(3333.33)),
        notNewBuildValue = Some(BigDecimal(4444.44)),
        ownedBeforePolicyYearValue = Some(BigDecimal(2222.22)),
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(1111.11)))

      PropertyDetailsUtils.getInitialValueForSubmission(Some(propVal),periodKey) must be (Some(BigDecimal(2222.22)))
    }

    "return the new build value, even if it's been revalued" in {
      val propVal = PropertyDetailsValue(isOwnedBeforePolicyYear = Some(false),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(3333.33)),
        notNewBuildValue = Some(BigDecimal(4444.44)),
        ownedBeforePolicyYearValue = Some(BigDecimal(2222.22)),
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(1111.11)))

      PropertyDetailsUtils.getInitialValueForSubmission(Some(propVal),periodKey) must be (Some(BigDecimal(3333.33)))
    }

    "return the not new build value, even if it's been revalued" in {
      val propVal = PropertyDetailsValue(isOwnedBeforePolicyYear = Some(false),
        isNewBuild = Some(false),
        newBuildValue = Some(BigDecimal(3333.33)),
        notNewBuildValue = Some(BigDecimal(4444.44)),
        ownedBeforePolicyYearValue = Some(BigDecimal(2222.22)),
        isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(1111.11)))

      PropertyDetailsUtils.getInitialValueForSubmission(Some(propVal),periodKey) must be (Some(BigDecimal(4444.44)))
    }
  }

  "getAcquistionData" must {
    "return None if we have no value object " in {
      val res = PropertyDetailsUtils.getAcquisitionData(None,periodKey)
      res._1.isDefined must be (false)
      res._2.isDefined must be (false)
    }

    "return None we have a value object that isn't populated " in {
      val res = PropertyDetailsUtils.getAcquisitionData(Some(PropertyDetailsValue()),periodKey)
      res._1.isDefined must be (false)
      res._2.isDefined must be (false)
    }

    "return the revalued value if this has been revalued but we has no build or ownedBefore answers " in {
      val propVal = PropertyDetailsValue(
        isPropertyRevalued = Some(true),
        revaluedDate = Some(LocalDate.of(2013, 1, 1)),
        partAcqDispDate = Some(LocalDate.of(2013, 2, 2)),
        revaluedValue = Some(BigDecimal(1111.11)))

      val res = PropertyDetailsUtils.getAcquisitionData(Some(propVal),periodKey)
      res._1 must be (Some(BigDecimal(1111.11)))
      res._2 must be (Some(LocalDate.of(2013, 2, 2)))
    }

    "return the owned before value if this has been set, even if it's been revalued" in {
      val propVal = PropertyDetailsValue(isOwnedBeforePolicyYear = Some(true),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(3333.33)),
        newBuildDate = Some(LocalDate.of(2014, 1, 1)),
        notNewBuildValue = Some(BigDecimal(4444.44)),
        notNewBuildDate = Some(LocalDate.of(2015, 1, 1)),
        ownedBeforePolicyYearValue = Some(BigDecimal(2222.22)),
        isPropertyRevalued = Some(true),
        revaluedDate = Some(LocalDate.of(2013, 1, 1)),
        partAcqDispDate = Some(LocalDate.of(2013, 2, 2)),
        revaluedValue = Some(BigDecimal(1111.11)))

      val res = PropertyDetailsUtils.getAcquisitionData(Some(propVal),periodKey)
      res._1 must be (Some(BigDecimal(2222.22)))
      res._2 must be (Some(LocalDate.of(2012, 4, 1)))
    }

    "return the new build value, even if it's been revalued" in {
      val propVal = PropertyDetailsValue(isOwnedBeforePolicyYear = Some(false),
        isNewBuild = Some(true),
        newBuildValue = Some(BigDecimal(3333.33)),
        newBuildDate = Some(LocalDate.of(2014, 1, 1)),
        localAuthRegDate = Some(LocalDate.of(2014, 1, 5)),
        notNewBuildValue = Some(BigDecimal(4444.44)),
        notNewBuildDate = Some(LocalDate.of(2015, 1, 1)),
        ownedBeforePolicyYearValue = Some(BigDecimal(2222.22)),
        isPropertyRevalued = Some(true),
        revaluedDate = Some(LocalDate.of(2013, 1, 1)),
        partAcqDispDate = Some(LocalDate.of(2013, 2, 2)),
        revaluedValue = Some(BigDecimal(1111.11)))

      val res = PropertyDetailsUtils.getAcquisitionData(Some(propVal),periodKey)
      res._1 must be (Some(BigDecimal(3333.33)))
      res._2 must be (Some(LocalDate.of(2014, 1, 1)))
    }

    "return the not new build value, even if it's been revalued" in {
      val propVal = PropertyDetailsValue(isOwnedBeforePolicyYear = Some(false),
        isNewBuild = Some(false),
        newBuildValue = Some(BigDecimal(3333.33)),
        newBuildDate = Some(LocalDate.of(2014, 1, 1)),
        notNewBuildValue = Some(BigDecimal(4444.44)),
        notNewBuildDate = Some(LocalDate.of(2015, 1, 1)),
        ownedBeforePolicyYearValue = Some(BigDecimal(2222.22)),
        isPropertyRevalued = Some(true),
        revaluedDate = Some(LocalDate.of(2013, 1, 1)),
        partAcqDispDate = Some(LocalDate.of(2013, 2, 2)),
        revaluedValue = Some(BigDecimal(1111.11)))

      val res = PropertyDetailsUtils.getAcquisitionData(Some(propVal),periodKey)
      res._1 must be (Some(BigDecimal(4444.44)))
      res._2 must be (Some(LocalDate.of(2015, 1, 1)))
    }

    "return the not new build value, even if it's not been revalued" in {
      val propVal = PropertyDetailsValue(isOwnedBeforePolicyYear = None,
        isNewBuild = None,
        newBuildValue = Some(BigDecimal(3333.33)),
        newBuildDate = Some(LocalDate.of(2014, 1, 1)),
        notNewBuildValue = Some(BigDecimal(4444.44)),
        notNewBuildDate = Some(LocalDate.of(2015, 1, 1)),
        ownedBeforePolicyYearValue = Some(BigDecimal(2222.22)),
        isPropertyRevalued = Some(false),
        revaluedDate = Some(LocalDate.of(2013, 1, 1)),
        partAcqDispDate = Some(LocalDate.of(2013, 2, 2)),
        revaluedValue = Some(BigDecimal(1111.11)))

      val res = PropertyDetailsUtils.getAcquisitionData(Some(propVal),periodKey)
      res._1 must be (Some(BigDecimal(1111.11)))
      res._2 must be (Some(LocalDate.of(2013, 2, 2)))
    }
  }


  "getValuationDate" must {
    "return None if we have no value object " in {
      PropertyDetailsUtils.getValuationDate(None, None, periodKey).isDefined must be (false)
    }

    "return None we have a value object that isn't populated " in {
      PropertyDetailsUtils.getValuationDate(Some(PropertyDetailsValue()), None, periodKey).isDefined must be (false)
    }

    "return the revalued date if this is an aquisition that has been revalued " in {
      val propVal = PropertyDetailsValue(anAcquisition = Some(true), isPropertyRevalued = Some(true), revaluedDate = Some(LocalDate.of(periodKey, 4, 1)))
      PropertyDetailsUtils.getValuationDate(Some(propVal), Some(LocalDate.of(periodKey, 4, 1)), periodKey) must be (Some(LocalDate.of(periodKey, 4, 1)))
    }

    "return April 2012 if this is not revalued but was acquired before 2012 " in {
      val propVal = PropertyDetailsValue(anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(true))
      PropertyDetailsUtils.getValuationDate(Some(propVal),Some(LocalDate.of(2012, 4, 1)), 2015) must be (Some(LocalDate.of(2012, 4, 1)))
      PropertyDetailsUtils.getValuationDate(Some(propVal),Some(LocalDate.of(2012, 4, 1)), 2019) must be (Some(LocalDate.of(2017, 4, 1)))
    }


    "return April 2017 if this is not revalued but was acquired before 2017 " in {
      val propVal = PropertyDetailsValue(anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(true))
      PropertyDetailsUtils.getValuationDate(Some(propVal),Some(LocalDate.of(2013, 4, 1)), 2019) must be (Some(LocalDate.of(2017, 4, 1)))
    }

    "return April 2022 if this is not revalued but was acquired before 2022 and the feature flag is active" in {
      when(mockServicesConfig.getBoolean(ArgumentMatchers.any()))
        .thenReturn(true)
      val propVal = PropertyDetailsValue(anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(true))
      PropertyDetailsUtils.getValuationDate(Some(propVal), Some(LocalDate.of(2018, 4, 1)), 2023) must be(Some(LocalDate.of(2022, 4, 1)))
    }

    "return April 2022 if this is not revalued but was acquired before 2022 and the feature flag is inactive" in {
      when(mockServicesConfig.getBoolean(ArgumentMatchers.any()))
        .thenReturn(false)
      val propVal = PropertyDetailsValue(anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(true))
      PropertyDetailsUtils.getValuationDate(Some(propVal), Some(LocalDate.of(2018, 4, 1)), 2023) must be(Some(LocalDate.of(2017, 4, 1)))
    }

    "return the revalued date if this is an aquisition that has been revalued for policy year 2017" in {
      val propVal = PropertyDetailsValue(anAcquisition = Some(true), isPropertyRevalued = Some(true), revaluedDate = Some(LocalDate.of(2019, 4, 1)))
      PropertyDetailsUtils.getValuationDate(Some(propVal), Some(LocalDate.of(2019, 4, 1)), 2019) must be (Some(LocalDate.of(2019, 4, 1)))
    }


    "return the new build date if this is a new build" in {
      val propVal = PropertyDetailsValue(anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(false),
        isNewBuild = Some(true),
        newBuildDate = Some(LocalDate.of(periodKey, 4, 1)),
        localAuthRegDate = Some(LocalDate.of(periodKey, 4, 5)),
        notNewBuildDate = Some(LocalDate.of(periodKey, 5, 1)))
      PropertyDetailsUtils.getValuationDate(Some(propVal),Some(LocalDate.of(periodKey, 4, 1)), periodKey) must be (Some(LocalDate.of(periodKey, 4, 1)))
    }

    "return the not new build date if this is a not a new build" in {
      val propVal = PropertyDetailsValue(anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(false),
        isNewBuild = Some(false),
        newBuildDate = Some(LocalDate.of(periodKey, 4, 1)),
        notNewBuildDate = Some(LocalDate.of(periodKey, 5, 1)))
      PropertyDetailsUtils.getValuationDate(Some(propVal),Some(LocalDate.of(periodKey, 5, 1)), periodKey) must be (Some(LocalDate.of(periodKey, 5, 1)))
    }

    "return the valuation date if we have this but haven't made an acquistion" in {
      val propVal = PropertyDetailsValue(anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(false),
        isNewBuild = Some(false),
        newBuildDate = Some(LocalDate.of(periodKey, 4, 1)),
        notNewBuildDate = Some(LocalDate.of(periodKey, 5, 1)),
        isValuedByAgent = Some(true)
      )
      PropertyDetailsUtils.getValuationDate(Some(propVal),Some(LocalDate.of(periodKey, 6, 1)), periodKey) must be (Some(LocalDate.of(periodKey, 6, 1)))
    }

    "return the max valuation date if we have two" in {
      val propVal = PropertyDetailsValue(anAcquisition = Some(true), isOwnedBeforePolicyYear = Some(false),
        isNewBuild = Some(false),
        newBuildDate = Some(LocalDate.of(periodKey, 4, 1)),
        notNewBuildDate = Some(LocalDate.of(periodKey, 5, 1)),
        isValuedByAgent = Some(true),
        isPropertyRevalued = Some(true),
        revaluedDate = Some(LocalDate.of(periodKey, 7, 1))
      )
      PropertyDetailsUtils.getValuationDate(Some(propVal),Some(LocalDate.of(periodKey, 5, 1)), periodKey) must be (Some(LocalDate.of(periodKey, 7, 1)))
    }


    "return the revalued date we haven't also had a valuation" in {
      val propVal = PropertyDetailsValue(anAcquisition = Some(true), isOwnedBeforePolicyYear = Some(false),
        isNewBuild = Some(false),
        newBuildDate = Some(LocalDate.of(periodKey, 4, 1)),
        notNewBuildDate = Some(LocalDate.of(periodKey, 5, 1)),
        isValuedByAgent = Some(false),
        isPropertyRevalued = Some(true),
        revaluedDate = Some(LocalDate.of(periodKey, 7, 1))
      )
      PropertyDetailsUtils.getValuationDate(Some(propVal),Some(LocalDate.of(periodKey, 7, 1)), periodKey) must be (Some(LocalDate.of(periodKey, 7, 1)))
    }
  }



  "getLatestDate" must {
    "return None if we have no dates" in {
      PropertyDetailsUtils.getLatestDate(None, None). isDefined must be (false)
    }

    "return the first date if thats all we have" in {
      PropertyDetailsUtils.getLatestDate(Some(LocalDate.of(2015, 1, 1)), None) must be (Some(LocalDate.of(2015, 1, 1)))
    }

    "return the second date if thats all we have" in {
      PropertyDetailsUtils.getLatestDate(None, Some(LocalDate.of(2015, 3, 3))) must be (Some(LocalDate.of(2015, 3, 3)))
    }

    "return the latest date if it's the second one" in {
      PropertyDetailsUtils.getLatestDate(Some(LocalDate.of(2015, 1, 1)), Some(LocalDate.of(2015, 3, 3))) must be (Some(LocalDate.of(2015, 3, 3)))
    }

    "return the latest date if it's the first one" in {
      PropertyDetailsUtils.getLatestDate(Some(LocalDate.of(2016, 1, 1)), Some(LocalDate.of(2015, 3, 3))) must be (Some(LocalDate.of(2016, 1, 1)))
    }
  }

  "disposeLineItem" must {
    val initialValue = BigDecimal(1000.00)
    val updatedValue = BigDecimal(2000.00)
    val endDate = LocalDate.of(periodKey+1, 3, 31)
    val item1 = FormBundleProperty(initialValue, LocalDate.of(periodKey, 4, 1), LocalDate.of(periodKey, 8, 31), "Liability", None)
    val item2 = FormBundleProperty(updatedValue,LocalDate.of(periodKey, 9, 1), LocalDate.of(periodKey, 10, 31), "Liability", None)
    val item3 = FormBundleProperty(updatedValue,LocalDate.of(periodKey, 11, 1), LocalDate.of(periodKey+1, 1, 31), "Relief", None)
    val item4 = FormBundleProperty(updatedValue,LocalDate.of(periodKey+1, 2, 1), LocalDate.of(periodKey+1, 3, 31), "Liability", None)
    val lineItems = List(item1, item2, item3, item4)
    "return Nil if we have an empty list" in {
      PropertyDetailsUtils.disposeLineItems(periodKey.toString, Nil, None) must be (Nil)
    }

    "return the list unchanged if we have no dispose date" in {
      val expected = List(
        EtmpLineItems(item1.propertyValue, item1.dateFrom, item1.dateTo, item1.`type`, item1.reliefDescription),
        EtmpLineItems(item2.propertyValue, item2.dateFrom, item2.dateTo, item2.`type`, item2.reliefDescription),
        EtmpLineItems(item3.propertyValue, item3.dateFrom, item3.dateTo, item3.`type`, item3.reliefDescription),
        EtmpLineItems(item4.propertyValue, item4.dateFrom, item4.dateTo, item4.`type`, item4.reliefDescription)
      )
      PropertyDetailsUtils.disposeLineItems(periodKey.toString, lineItems, None) must be (expected)
    }

    "if the disposal date falls on the first date of the final period, this should be replaced with a disposal period" in {
      val expected = List(
        EtmpLineItems(item1.propertyValue, item1.dateFrom, item1.dateTo, item1.`type`, item1.reliefDescription),
        EtmpLineItems(item2.propertyValue, item2.dateFrom, item2.dateTo, item2.`type`, item2.reliefDescription),
        EtmpLineItems(item3.propertyValue, item3.dateFrom, item3.dateTo, item3.`type`, item3.reliefDescription),
        EtmpLineItems(item4.propertyValue, item4.dateFrom, item4.dateTo, TypeDeEnveloped, None)
      )
      PropertyDetailsUtils.disposeLineItems(periodKey.toString, lineItems, Some(item4.dateFrom)) must be (expected)
    }

    "if the disposal date falls on the first date of the second period, this should be replaced with a disposal period and all subsequent periods deleted" in {
      val expected = List(
        EtmpLineItems(item1.propertyValue, item1.dateFrom, item1.dateTo, item1.`type`, item1.reliefDescription),
        EtmpLineItems(item2.propertyValue, item2.dateFrom, endDate, TypeDeEnveloped, None)
      )
      PropertyDetailsUtils.disposeLineItems(periodKey.toString, lineItems, Some(item2.dateFrom)) must be (expected)
    }

    "if the disposal date falls in the middle of a period then this should be split in 2" in {
      val disposalDate = item2.dateFrom.plusDays(10)
      val expected = List(
        EtmpLineItems(item1.propertyValue, item1.dateFrom, item1.dateTo, item1.`type`, item1.reliefDescription),
        EtmpLineItems(item2.propertyValue, item2.dateFrom, disposalDate.plusDays(-1), item2.`type`, item2.reliefDescription),
        EtmpLineItems(item2.propertyValue, disposalDate, endDate, TypeDeEnveloped, None)
      )
      PropertyDetailsUtils.disposeLineItems(periodKey.toString, lineItems, Some(disposalDate)) must be (expected)
    }

    "if the disposal date falls on the first day of the year" in {
      val disposalDate = item1.dateFrom
      val expected = List(
        EtmpLineItems(item1.propertyValue, disposalDate, endDate, TypeDeEnveloped, None)
      )
      PropertyDetailsUtils.disposeLineItems(periodKey.toString, lineItems, Some(disposalDate)) must be (expected)
    }

    "throw an exception if the disposal date is after the end date" in {
      val disposalDate = endDate.plusDays(1)
      val thrown = the[InternalServerException] thrownBy PropertyDetailsUtils.disposeLineItems(periodKey.toString, lineItems, Some(disposalDate))
      thrown.getMessage must include("[PropertyDetailsUtils][disposeLineItems] - Disposal Date is after the end of the period")
    }

    "Check with complex data" in {
      val complexValue = BigDecimal(727000)
      val complexValue2 = BigDecimal(927000)
      val complexValue3 = BigDecimal(1227000)
      val item1 = FormBundleProperty(complexValue, LocalDate.of(periodKey, 4, 1), LocalDate.of(periodKey, 10, 31), "Liability", None)
      val item2 = FormBundleProperty(complexValue2, LocalDate.of(periodKey, 11, 01), LocalDate.of(periodKey+1, 1, 1), "Liability", None)
      val item3 = FormBundleProperty(complexValue2, LocalDate.of(periodKey+1, 1, 2), LocalDate.of(periodKey+1, 1, 31), "Relief", Some("Property developers"))
      val item4 = FormBundleProperty(complexValue2, LocalDate.of(periodKey+1, 2, 1), LocalDate.of(periodKey+1, 3, 7), "Relief", Some("Property rental businesses"))
      val item5 = FormBundleProperty(complexValue3, LocalDate.of(periodKey+1, 3, 8), LocalDate.of(periodKey+1, 3, 31), "Relief", Some("Property rental businesses"))
      val lineItems = List(item1, item2, item3, item4, item5)

      val disposalDate = LocalDate.of(periodKey+1, 1, 31)

      val expected = List(
        EtmpLineItems(item1.propertyValue, item1.dateFrom, item1.dateTo, item1.`type`, item1.reliefDescription),
        EtmpLineItems(item2.propertyValue, item2.dateFrom, item2.dateTo, item2.`type`, item2.reliefDescription),
        EtmpLineItems(item3.propertyValue, item3.dateFrom, disposalDate.plusDays(-1), item3.`type`, item3.reliefDescription),
        EtmpLineItems(item4.propertyValue, disposalDate, endDate, TypeDeEnveloped, None)
      )
      PropertyDetailsUtils.disposeLineItems(periodKey.toString, lineItems, Some(disposalDate)) must be (expected)
    }
  }
}
