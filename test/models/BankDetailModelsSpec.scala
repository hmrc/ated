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


import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsString, JsValue, Json}
import uk.gov.hmrc.crypto.Sensitive.{SensitiveBoolean, SensitiveString}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter}

import scala.reflect.ClassManifestFactory.Nothing

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
    "decrypt the elements" when {
      "when there are protected bank details" in {
        val crypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]
        implicit val jsonCrypto: Encrypter with Decrypter = crypto.JsonCrypto

        val encryptedProtectedBankDetailsJson =
          s"""
             |{
             |    "hasBankDetails": false,
             |    "protectedBankDetails" : {
             |      "hasUKBankAccount" : "N3aBb38antBm3t1jI4zlhg==",
             |      "accountName" : "+FsJBNzKH38QPY9we5ebyQ==",
             |      "accountNumber" : "rn8JErJ9wM/7nQwJce9fOw==",
             |      "sortCode" : "F0vOiYU8dp8L7M7oHJ2lRTN4+H02TzWdW98bsF8tI1gsIwENDROLYtWfpunYaZqYxZVMOavTLAtmAQGCHjsVFA==",
             |      "bicSwiftCode" : "vrPsPHFTZTzDkAs47XbyLXkoaCflT3w4MM80DzAW3cM=",
             |       "iban" : "fT98XnPNxN88UtlRy/DiamnNU1JKYdD5nTfOSKSdBlU="
             |     }
             |}
          """.stripMargin

        val protectedBankDetails = ProtectedBankDetails(Some(SensitiveBoolean(true)),
          Some(SensitiveString("AcountName")), Some(SensitiveString("1111111")), Some(SensitiveSortCode(SortCode("00", "01", "02"))),
          Some(SensitiveBicSwiftCode(BicSwiftCode("12345678901"))), Some(SensitiveIban((Iban("iBanCode")))))

        val entity: BankDetailsModel = Json.fromJson(Json.parse(encryptedProtectedBankDetailsJson))(BankDetailsModel.format).asOpt.value

        entity.protectedBankDetails match {
          case Some(x) => x shouldBe protectedBankDetails
          case _ => Nothing
        }
      }
    }
    "encrypt/decrypt ProtectdBankDetails entity" in {
      val crypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]
      implicit val jsonCrypto: Encrypter with Decrypter = crypto.JsonCrypto

      val protectedBankDetails = ProtectedBankDetails(Some(SensitiveBoolean(true)),
        Some(SensitiveString("AcountName")), Some(SensitiveString("1111111")), Some(SensitiveSortCode(SortCode.fromString("000102"))),
        Some(SensitiveBicSwiftCode(BicSwiftCode("12345678901"))), Some(SensitiveIban((Iban("iBanCode")))))

      val json: JsValue = Json.toJson(protectedBankDetails)(ProtectedBankDetails.bankDetailsFormats)

      (json \ "hasUKBankAccount").get shouldBe JsString("N3aBb38antBm3t1jI4zlhg==")
      (json \ "accountName").get shouldBe JsString("+FsJBNzKH38QPY9we5ebyQ==")
      (json \ "accountNumber").get shouldBe JsString("rn8JErJ9wM/7nQwJce9fOw==")
      (json \ "sortCode").get shouldBe JsString("F0vOiYU8dp8L7M7oHJ2lRTN4+H02TzWdW98bsF8tI1gsIwENDROLYtWfpunYaZqYxZVMOavTLAtmAQGCHjsVFA==")
      (json \ "bicSwiftCode").get shouldBe JsString("vrPsPHFTZTzDkAs47XbyLXkoaCflT3w4MM80DzAW3cM=")
      (json \ "iban").get shouldBe JsString("fT98XnPNxN88UtlRy/DiamnNU1JKYdD5nTfOSKSdBlU=")
    }
  }
}
