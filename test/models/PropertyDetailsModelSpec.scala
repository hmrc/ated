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

import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class PropertyDetailsModelSpec extends PlaySpec with MockitoSugar {

  implicit val mockServicesConfig: ServicesConfig = mock[ServicesConfig]

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

  "PropertyDetailsValue Request" should {
    "read the value from isOwnedBeforePolicyYear for propertyDetailsValueReads" in {
      val existingDraftInMongo =
        """{ "anAcquisition": true,
          |  "isPropertyRevalued": true,
          |  "revaluedValue": "32432423",
          |  "revaluedDate": "2014-05-25",
          |  "partAcqDispDate": "12345678",
          |  "isOwnedBeforePolicyYear": true,
          |  "ownedBeforePolicyYearValue": "1599999",
          |  "isNewBuild": true,
          |  "newBuildValue": "12345678",
          |  "newBuildDate": "2014-05-25",
          |  "localAuthRegDate": "2014-10-25",
          |  "notNewBuildValue": "4646465",
          |  "notNewBuildDate": "12345678",
          |  "isValuedByAgent": true,
          |  "hasValueChanged": true
          |}""".stripMargin
      val exampleJson = Json.parse(existingDraftInMongo)
      val propertyDetailsValue = exampleJson.as[PropertyDetailsValue]
      propertyDetailsValue.isOwnedBeforePolicyYear must be (Some(true))
      propertyDetailsValue.ownedBeforePolicyYearValue must be (Some(1599999))
    }
  }

  "PropertyDetailsValue Request" should {
    "read the value from isOwnedBefore2012 for propertyDetailsValueReads" in {
      val existingDraftInMongo =
        """{ "anAcquisition": true,
          |  "isPropertyRevalued": true,
          |  "revaluedValue": "32432423",
          |  "revaluedDate": "2014-05-25",
          |  "partAcqDispDate": "12345678",
          |  "isOwnedBefore2012": true,
          |  "ownedBefore2012Value": "1599999",
          |  "isNewBuild": true,
          |  "newBuildValue": "12345678",
          |  "newBuildDate": "2014-05-25",
          |  "localAuthRegDate": "2014-10-25",
          |  "notNewBuildValue": "4646465",
          |  "notNewBuildDate": "12345678",
          |  "isValuedByAgent": true,
          |  "hasValueChanged": true
          |}""".stripMargin
      val exampleJson = Json.parse(existingDraftInMongo)
      val propertyDetailsValue = exampleJson.as[PropertyDetailsValue]
      propertyDetailsValue.isOwnedBeforePolicyYear must be (Some(true))
      propertyDetailsValue.ownedBeforePolicyYearValue must be (Some(1599999))
    }
  }

  "PropertyDetailsValue Request" should {
    "read the value from isOwnedBeforePolicyYear and ownedBefore2012Value for propertyDetailsValueReads" in {
      val existingDraftInMongo =
        """{ "anAcquisition": true,
          |  "isPropertyRevalued": true,
          |  "revaluedValue": "32432423",
          |  "revaluedDate": "2014-05-25",
          |  "partAcqDispDate": "12345678",
          |  "isOwnedBeforePolicyYear": true,
          |  "ownedBefore2012Value": "1599999",
          |  "isNewBuild": true,
          |  "newBuildValue": "12345678",
          |  "newBuildDate": "2014-05-25",
          |  "localAuthRegDate": "2014-10-25",
          |  "notNewBuildValue": "4646465",
          |  "notNewBuildDate": "12345678",
          |  "isValuedByAgent": true,
          |  "hasValueChanged": true
          |}""".stripMargin
      val exampleJson = Json.parse(existingDraftInMongo)
      val propertyDetailsValue = exampleJson.as[PropertyDetailsValue]
      propertyDetailsValue.isOwnedBeforePolicyYear must be (Some(true))
      propertyDetailsValue.ownedBeforePolicyYearValue must be (Some(1599999))
    }
  }

  "PropertyDetailsValue Request" should {
    "read the value as None for propertyDetailsValueReads" in {
      val existingDraftInMongo =
        """{ "anAcquisition": true,
          |  "isPropertyRevalued": true,
          |  "revaluedValue": "32432423",
          |  "revaluedDate": "2014-05-25",
          |  "partAcqDispDate": "12345678",
          |  "isNewBuild": true,
          |  "newBuildValue": "12345678",
          |  "newBuildDate": "2014-05-25",
          |  "localAuthRegDate": "2014-10-25",
          |  "notNewBuildValue": "4646465",
          |  "notNewBuildDate": "12345678",
          |  "isValuedByAgent": true,
          |  "hasValueChanged": true
          |}""".stripMargin
      val exampleJson = Json.parse(existingDraftInMongo)
      val propertyDetailsValue = exampleJson.as[PropertyDetailsValue]
      propertyDetailsValue.isOwnedBeforePolicyYear must be (None)
      propertyDetailsValue.ownedBeforePolicyYearValue must be (None)
    }
  }

  "PropertyDetailsValue Request" should {
    "read the value from isOwnedBeforePolicyYear instead of isOwnedBefore2012 for propertyDetailsValueReads" in {
      val existingDraftInMongo =
        """{ "anAcquisition": true,
          |  "isPropertyRevalued": true,
          |  "revaluedValue": "32432423",
          |  "revaluedDate": "2014-05-25",
          |  "partAcqDispDate": "12345678",
          |  "isOwnedBeforePolicyYear": true,
          |  "ownedBeforePolicyYearValue": "1599999",
          |  "isOwnedBefore2012": false,
          |  "ownedBefore2012Value": "1500000",
          |  "isNewBuild": true,
          |  "newBuildValue": "12345678",
          |  "newBuildDate": "2014-05-25",
          |  "localAuthRegDate": "2014-10-25",
          |  "notNewBuildValue": "4646465",
          |  "notNewBuildDate": "12345678",
          |  "isValuedByAgent": true,
          |  "hasValueChanged": true
          |}""".stripMargin
      val exampleJson = Json.parse(existingDraftInMongo)
      val propertyDetailsValue = exampleJson.as[PropertyDetailsValue]
      propertyDetailsValue.isOwnedBeforePolicyYear must be (Some(true))
      propertyDetailsValue.ownedBeforePolicyYearValue must be (Some(1599999))
    }
  }


}
