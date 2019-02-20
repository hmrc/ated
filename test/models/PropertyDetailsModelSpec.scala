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

package models

import org.scalatestplus.play.PlaySpec

class PropertyDetailsModelSpec extends PlaySpec {

  ".policyYear on PropertyDetailsOwnedBefore" should {

    "return IsOwnedBefore2017" when {
      "the property is owned before 2017 for chargeable return year 2018-19" in {
        model.policyYear(2018) must be(IsOwnedBefore2017)
      }
      "the property is owned before 2017 for chargeable return year 2019-20" in {
        model.policyYear(2019) must be(IsOwnedBefore2017)
      }
    }

    "return IsOwnedBefore2012" when {
      "the property is owned before 2012 for chargeable return year 2013-14" in {
        model.policyYear(2013) must be(IsOwnedBefore2012)
      }
      "the property is owned before 2012 for chargeable return year 2014-15" in {
        model.policyYear(2014) must be(IsOwnedBefore2012)
      }
      "the property is owned before 2012 for chargeable return year 2015-16" in {
        model.policyYear(2015) must be(IsOwnedBefore2012)
      }
      "the property is owned before 2012 for chargeable return year 2016-17" in {
        model.policyYear(2016) must be(IsOwnedBefore2012)
      }
      "the property is owned before 2012 for chargeable return year 2017-18" in {
        model.policyYear(2017) must be(IsOwnedBefore2012)
      }
    }

    "return a NotOwnedBeforePolicyYear" when {
      "for any chargeable return year for NOT owned before policy year" in {
        model.copy(isOwnedBeforePolicyYear = None).policyYear(2016) must be(NotOwnedBeforePolicyYear)
      }
    }

    "throw an exception" when {
      "for chargeable return year 2012-13" in {
        val thrown = the[RuntimeException] thrownBy model.policyYear(2012)
        thrown.getMessage must include("Invalid liability period")
      }
    }
  }

  val model = PropertyDetailsOwnedBefore(Some(true), Some(1000000))

}
