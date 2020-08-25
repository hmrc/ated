/*
 * Copyright 2020 HM Revenue & Customs
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
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsPath, JsSuccess, Json}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CryptoWithKeysFromConfig, Protected}

class BankDetailModelsSpec extends PlaySpec with GuiceOneServerPerSuite {

  "BicSwiftCode" must {

    "allow a valid Swift Code adding spaces" in {
      val swiftCode = BicSwiftCode("12345678901")
      swiftCode.bankCode must be("1234")
      swiftCode.countryCode must be("56")
      swiftCode.locationCode must be("78")
      swiftCode.branchCode must be("901")
      swiftCode.toString must be("1234 56 78 901")

      val swiftCodeWithSpaces = BicSwiftCode("1234 56 78 901")
      swiftCodeWithSpaces.bankCode must be("1234")
      swiftCodeWithSpaces.countryCode must be("56")
      swiftCodeWithSpaces.locationCode must be("78")
      swiftCodeWithSpaces.branchCode must be("901")
      swiftCodeWithSpaces.toString must be("1234 56 78 901")

      val swiftCodeWithSpacesNoBranch = BicSwiftCode("1234 56 78")
      swiftCodeWithSpacesNoBranch.bankCode must be("1234")
      swiftCodeWithSpacesNoBranch.countryCode must be("56")
      swiftCodeWithSpacesNoBranch.locationCode must be("78")
      swiftCodeWithSpacesNoBranch.toString must be("1234 56 78")

    }

    "fail if the Swift Code is not 8 or 11 characters" in {
      val thrown = the[java.lang.IllegalArgumentException] thrownBy BicSwiftCode("1234567")
      thrown.getMessage must include("requirement failed: 1234567 is not a valid BicSwiftCode")

      val thrownMiddle = the[java.lang.IllegalArgumentException] thrownBy BicSwiftCode("1234567890")
      thrownMiddle.getMessage must include("requirement failed: 1234567890 is not a valid BicSwiftCode")

      val thrownTooLong = the[java.lang.IllegalArgumentException] thrownBy BicSwiftCode("123456789012")
      thrownTooLong.getMessage must include("requirement failed: 123456789012 is not a valid BicSwiftCode")
    }

  }

  "Iban" must {
    "fail if we have too many characters" in {
      val invalidIban = "x" * 35
      val thrown = the[java.lang.IllegalArgumentException] thrownBy Iban(invalidIban)
      thrown.getMessage must include(s"requirement failed: $invalidIban is not a valid Iban")
    }

    "accept valid Ibans" in {
      val validIban = "x" * 34
      Iban(validIban).toString must be (validIban)
    }
  }

  "BankDetailsModel" must {
    "read" when {
      "when there are protected bank details" in {
        val crypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]
        implicit val jsonCrypto: CryptoWithKeysFromConfig = crypto.JsonCrypto

        val json =
          s"""
            |{
            | "hasBankDetails": false,
            |  "protectedBankDetails" : {
            |      "hasUKBankAccount" : "N3aBb38antBm3t1jI4zlhg==",
            |      "accountName" : "AgRrB7hjGHSPCP/tOoC4e1PkDrL+/GWL0fk3OSrFFg0=",
            |      "accountNumber" : "KB+g5/qReFmvl+V+YJlO4A==",
            |      "sortCode" : "F0vOiYU8dp8L7M7oHJ2lRaSekYjhPwCa0Dyu8Tw9bh7WiT1SveRojOCco4aPD/d9b7JjylAOv+GPowsmDoaRiQ==",
            |      "bicSwiftCode" : "+ZKJ7XVtuMrxNikqKNfLyQ==",
            |      "iban" : "+ZKJ7XVtuMrxNikqKNfLyQ=="
            |  }
            |}
          """.stripMargin

        val JsSuccess(success, JsPath(List())) = BankDetailsModel.format.reads(Json.parse(json))
        success.hasBankDetails mustBe false
        success.protectedBankDetails.get.hasUKBankAccount mustBe Protected(Some(true))
        success.protectedBankDetails.get.accountName mustBe Protected(Some("ATED Tax Payer"))
      }
    }
  }
}
