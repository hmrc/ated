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

import builders.PropertyDetailsBuilder
import models._
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import org.scalatestplus.play.OneServerPerSuite

class LiabilityUtilsSpec extends PlaySpec with ReliefConstants with OneServerPerSuite {

  val periodStartDate = new LocalDate("2015-04-01")
  val periodEndDate = new LocalDate("2016-03-30")

  "createEtmpDraftReturns" must {

    "Return a valid POST request if we have all the relevant data and the Liable From date is required" in {

      lazy val propertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("postCode"))
      lazy val liabilities = LiabilityUtils.createPostReturnsRequest("1", propertyDetails)

      liabilities.isDefined must be(true)
      liabilities.get.liabilityReturns.isDefined must be(true)
      lazy val returns = liabilities.get.liabilityReturns.get
      returns.size must be(1)
      returns.head.propertyKey must be("1")
      returns.head.mode must be(Post)
    }

    "Return a valid PRE_CALC when we have both reliefs and liabilities" in {
      lazy val propertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("postCode"))
      lazy val liabilites = propertyDetails.period.get.liabilityPeriods
      lazy val reliefs = propertyDetails.period.get.reliefPeriods
      liabilites.isEmpty must be(false)
      reliefs.isEmpty must be(false)

      lazy val liabilities = LiabilityUtils.createPreCalculationReturnsRequest("1", propertyDetails)

      liabilities.isDefined must be(true)
      liabilities.get.liabilityReturns.isDefined must be(true)
      lazy val liabilityreturns = liabilities.get.liabilityReturns.get
      liabilityreturns.size must be(1)
      liabilityreturns.head.propertyKey must be("1")
      liabilityreturns.head.mode must be(PreCalculation)
      liabilityreturns.head.lineItems.size must be (liabilites.size + reliefs.size)

      liabilities.get.reliefReturns.isDefined must be (false)
    }

    "Return a valid PRE_CALC when we only have liabilities" in {
      lazy val popertyDetailsNoReliefs = PropertyDetailsBuilder.getFullPropertyDetailsNoReliefs("1", Some("postCode"))
      lazy val liabilites = popertyDetailsNoReliefs.period.get.liabilityPeriods
      lazy val reliefs = popertyDetailsNoReliefs.period.get.reliefPeriods
      liabilites.isEmpty must be(false)
      reliefs.isEmpty must be(true)

      lazy val liabilities = LiabilityUtils.createPreCalculationReturnsRequest("1", popertyDetailsNoReliefs)

      liabilities.isDefined must be(true)
      liabilities.get.liabilityReturns.isDefined must be(true)
      lazy val liabilityreturns = liabilities.get.liabilityReturns.get
      liabilityreturns.size must be(1)
      liabilityreturns.head.propertyKey must be("1")
      liabilityreturns.head.mode must be(PreCalculation)
      liabilityreturns.head.lineItems.size must be (liabilites.size)

      liabilities.get.reliefReturns.isDefined must be (false)
    }

    "Return a valid PRE_CALC when we only have reliefs" in {
      lazy val popertyDetailsNoLiabilities = PropertyDetailsBuilder.getFullPropertyDetailsNoLiabilities("1", Some("postCode"))
      lazy val liabilites = popertyDetailsNoLiabilities.period.get.liabilityPeriods
      lazy val reliefs = popertyDetailsNoLiabilities.period.get.reliefPeriods
      liabilites.isEmpty must be(true)
      reliefs.isEmpty must be(false)

      lazy val liabilities = LiabilityUtils.createPreCalculationReturnsRequest("1", popertyDetailsNoLiabilities)

      liabilities.isDefined must be(true)
      liabilities.get.liabilityReturns.isDefined must be(true)
      lazy val liabilityreturns = liabilities.get.liabilityReturns.get
      liabilityreturns.size must be(1)
      liabilityreturns.head.propertyKey must be("1")
      liabilityreturns.head.mode must be(PreCalculation)
      liabilityreturns.head.lineItems.size must be (reliefs.size)

      liabilities.get.reliefReturns.isDefined must be (false)
    }

    "return a valid calc if we haven't got a valuation value" in {
      lazy val popertyDetailsNoReliefs = PropertyDetailsBuilder.getPropertyDetailsNoValuation("1", Some("postCode"))
      lazy val liabilities = LiabilityUtils.createPreCalculationReturnsRequest("1", popertyDetailsNoReliefs)

      liabilities.get.liabilityReturns.get.head.dateOfValuation must be(liabilities.get.liabilityReturns.get.head.dateOfAcquisition.get)
    }


    "Return None if we don't have any calculated details" in {
      lazy val propertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("postCode"))
      lazy val liabilities = LiabilityUtils.createPreCalculationReturnsRequest("1", propertyDetails.copy(value = None, calculated = None))
      liabilities.isDefined must be(false)
    }
  }
}
