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


import org.scalatest.matchers.should.Matchers.*
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}
import crypto.MongoCryptoProvider

import scala.reflect.ClassManifestFactory.Nothing

class BankDetailModelsSpec extends PlaySpec with GuiceOneServerPerSuite {

  private val mongoCrypto: MongoCryptoProvider = app.injector.instanceOf[MongoCryptoProvider]

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
    "round-trip ProtectedBankDetails through the GCM provider" in {
      given jsonCrypto: (Encrypter & Decrypter) = mongoCrypto.gcmCrypto

      val protectedBankDetails = ProtectedBankDetails(Some(SensitiveHasUKBankAccount(Some(true))),
        Some(SensitiveAccountName(Some("AcountName"))), Some(SensitiveAccountNumber(Some("1111111"))), Some(SensitiveSortCode(Some(SortCode.fromString("000102")))),
        Some(SensitiveBicSwiftCode(Some(BicSwiftCode("12345678901")))), Some(SensitiveIban(Some(Iban("iBanCode")))))

      val json: JsValue = Json.toJson(protectedBankDetails)(ProtectedBankDetails.bankDetailsFormats)

      (json \ "hasUKBankAccount").get should not be JsString("N3aBb38antBm3t1jI4zlhg==")
      ProtectedBankDetails.bankDetailsFormats.reads(json).get shouldBe protectedBankDetails
    }

    "throw when protectedBankDetails cannot be decrypted (whole read fails, not None)" in {
      given jsonCrypto: (Encrypter & Decrypter) = mongoCrypto.gcmCrypto
      val foreignCrypto = SymmetricCryptoFactory.aesGcmCrypto("yY/qUIQgQjMjzPzyU8qCJ4GiTvtgfUv4UYVbrto7Puk=")
      val model = BankDetailsModel(hasBankDetails = true, protectedBankDetails = Some(ProtectedBankDetails(
        Some(SensitiveHasUKBankAccount(Some(true))), Some(SensitiveAccountName(Some("x"))), None, None, None, None)))

      val written = Json.toJson(model)(BankDetailsModel.format(using foreignCrypto))
      a [SecurityException] must be thrownBy Json.fromJson(written)(BankDetailsModel.format)
    }
  }
}
