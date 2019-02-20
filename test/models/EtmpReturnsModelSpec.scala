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

import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json

class EtmpReturnsModelSpec extends PlaySpec with OneServerPerSuite {

  "EtmpReturnsModel Response" should {

    "correctly parse the example json for the reliefReturnResponse" in {
      val jsonResponse =
        """
          |{
          |  "reliefDescription": "Property rental businesses",
          |  "formBundleNumber": "012345678912"
          |}
        """.stripMargin
      val exampleJson = Json.parse(jsonResponse)
      val request = exampleJson.as[EtmpReliefReturnResponse]

      request.reliefDescription must be("Property rental businesses")
    }


    "correctly parse the example json for the liabilityReturnResponse" in {
      val jsonResponse =
        """
          |{
          |  "mode": "Post",
          |  "propertyKey": "2",
          |  "liabilityAmount": "1234.12",
          |  "paymentReference": "aaaaaaaaaaaaaa",
          |  "formBundleNumber": "012345678912"
          |}
        """.stripMargin
      val exampleJson = Json.parse(jsonResponse)
      val request = exampleJson.as[EtmpLiabilityReturnResponse]

      request.formBundleNumber must be(Some("012345678912"))
    }

    "correctly parse the example json for a different liabilityReturnResponse" in {
      val jsonResponse =
        """
          |{
          |  "mode": "Pre-Calculation",
          |  "propertyKey": "1",
          |  "liabilityAmount": "999.99"
          |}
        """.stripMargin
      val exampleJson = Json.parse(jsonResponse)
      val request = exampleJson.as[EtmpLiabilityReturnResponse]

      request.formBundleNumber must be(None)
    }

    "correctly parse the example json for the response" in {
      val jsonResponse =
        """{
          |    "processingDate": "2001-12-17T09:30:47Z",
          |    "reliefReturnResponse": [
          |        {
          |            "reliefDescription": "Property rental businesses",
          |            "formBundleNumber": "012345678912"
          |        }
          |    ],
          |    "liabilityReturnResponse": [
          |        {
          |            "mode": "Post",
          |            "propertyKey": "2",
          |            "liabilityAmount": "1234.12",
          |            "paymentReference": "aaaaaaaaaaaaaa",
          |            "formBundleNumber": "012345678912"
          |        },
          |        {
          |            "mode": "Pre-Calculation",
          |            "propertyKey": "1",
          |            "liabilityAmount": "999.99"
          |        }
          |    ]
          |}""".stripMargin
      val exampleJson = Json.parse(jsonResponse)
      val request = exampleJson.as[SubmitEtmpReturnsResponse]

      request.reliefReturnResponse.get.size must be(1)
    }
  }
  "EtmpReturnsModel Request" should {
    "correctly parse the example json for the reliefReturn" in {
      val actualJson =
        """{ "reliefDescription": "Property rental businesses",
          |  "reliefStartDate": "2014-05-25",
          |  "reliefEndDate": "2014-10-25",
          |  "periodKey": "2015",
          |  "taxAvoidanceScheme": "12345678"
          |}""".stripMargin
      val exampleJson = Json.parse(actualJson)
      val request = exampleJson.as[EtmpReliefReturns]

      request.periodKey must be("2015")
    }

    "correctly parse the example json for the lineItems" in {
      val actualJson =
        """
          |{
          |  "propertyValue": 250000,
          |  "dateFrom": "2015-10-01",
          |  "dateTo": "2015-11-30",
          |  "type": "Relief",
          |  "reliefDescription": "Property rental businesses"
          |}
        """.stripMargin
      val exampleJson = Json.parse(actualJson)
      val request = exampleJson.as[EtmpLineItems]

      request.propertyValue must be(250000)
      request.dateFrom.getDayOfMonth must be(1)
      request.dateFrom.getMonthOfYear must be(10)
      request.dateFrom.getYear must be(2015)
    }

    "correctly parse the example json for the propertyDetails" in {
      val actualJson =
        """
          |{
          |  "titleNumber": "12345678",
          |  "address": {
          |    "addressLine1": "addressLine1",
          |    "addressLine2": "addressLine2",
          |    "countryCode": "GB",
          |    "postalCode": "postalCode"
          |  },
          |  "additionalDetails": "Extra title information"
          |}
        """.stripMargin
      val exampleJson = Json.parse(actualJson)
      val request = exampleJson.as[EtmpPropertyDetails]

      request.titleNumber must be(Some("12345678"))
    }

    "correctly parse the example json" in {
      val actualJson =
        """
          |{
          |  "acknowledgementReference": "ABCDEFGhijklmnopqrstuvwxyz123456",
          |  "reliefReturns": [
          |    {
          |      "reliefDescription": "Property rental businesses",
          |      "reliefStartDate": "2014-05-25",
          |      "reliefEndDate": "2014-10-25",
          |      "periodKey": "2015",
          |      "taxAvoidanceScheme": "12345678"
          |    },
          |    {
          |      "reliefDescription": "Dwellings opened to the public",
          |      "reliefStartDate": "2014-05-25",
          |      "reliefEndDate": "2014-10-25",
          |      "periodKey": "2014",
          |      "taxAvoidanceScheme": "Ab345678"
          |    }
          |  ],
          |  "liabilityReturns": [
          |    {
          |      "mode": "Post",
          |      "propertyKey": "aaaaaaaaaa",
          |      "periodKey": "2014",
          |      "propertyDetails": {
          |        "titleNumber": "12345678",
          |        "address": {
          |          "addressLine1": "addressLine1",
          |           "addressLine2": "addressLine2",
          |    "countryCode": "GB",
          |    "postalCode": "postalCode"
          |        },
          |        "additionalDetails": "Extra title information"
          |      },
          |      "dateOfAcquisition": "1999-10-01",
          |      "valueAtAcquisition": 250000,
          |      "dateOfValuation": "2014-10-25",
          |      "taxAvoidanceScheme": "10000000",
          |      "ninetyDayRuleApplies": true,
          |      "professionalValuation": true,
          |      "lineItems": [
          |        {
          |          "propertyValue": 250000,
          |          "dateFrom": "2015-10-01",
          |          "dateTo": "2015-11-30",
          |          "type": "Relief",
          |          "reliefDescription": "Property rental businesses"
          |        }
          |      ]
          |    }
          |  ]
          |}
        """.stripMargin
      val exampleJson = Json.parse(actualJson)
      val request = exampleJson.as[SubmitEtmpReturnsRequest]

      request.acknowledgementReference must be("ABCDEFGhijklmnopqrstuvwxyz123456")
      request.reliefReturns.isDefined must be(true)
      request.reliefReturns.get.size must be(2)
      request.liabilityReturns.isDefined must be(true)
      request.liabilityReturns.get.size must be(1)
    }
  }
}
