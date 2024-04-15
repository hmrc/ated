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

import models.CryptoFormatsSpec.SensitiveTestEntity
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{Format, JsPath, JsSuccess, JsValue, Json, OFormat}
import uk.gov.hmrc.crypto.Sensitive.{SensitiveBigDecimal, SensitiveBoolean, SensitiveString}
import uk.gov.hmrc.crypto.json.{JsonEncryption}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter, Sensitive, SymmetricCryptoFactory}

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

  "decrypt the elements" in {
    val jsonString =
      """{
      "normalString"    :"unencrypted",
      "encryptedString" : "3TW3L1raxsKBYuKvtKqPEQ==",
      "encryptedBoolean": "YhWm43Ad3rW5Votdy855Kg==",
      "encryptedNumber" : "Z/ipDOvm7C3ck/TBkiteAg=="
    }"""

    val entity = Json.fromJson(Json.parse(jsonString))(SensitiveTestEntity.formats).asOpt.value
    println(s"MOHAN MOHAN MOHAN ${entity}")
    entity shouldBe SensitiveTestEntity(
      "unencrypted",
      SensitiveString("encrypted"),
      SensitiveBoolean(true),
      SensitiveBigDecimal(BigDecimal("234"))
    )
  }

  "BankDetailsModel" must {
    "read" when {
      "when there are protected bank details" in {
        val crypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]
        implicit val jsonCrypto: Encrypter with Decrypter = crypto.JsonCrypto

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

        val json1 =
          s"""
             |{
             |      "hasUKBankAccount" : "N3aBb38antBm3t1jI4zlhg==",
             |      "accountName" : "+FsJBNzKH38QPY9we5ebyQ==",
             |      "accountNumber" : "rn8JErJ9wM/7nQwJce9fOw==",
             |      "sortCode" : "F0vOiYU8dp8L7M7oHJ2lRTN4+H02TzWdW98bsF8tI1gsIwENDROLYtWfpunYaZqYxZVMOavTLAtmAQGCHjsVFA==",
             |      "bicSwiftCode" : "vrPsPHFTZTzDkAs47XbyLXkoaCflT3w4MM80DzAW3cM=",
             |      "iban" : "fT98XnPNxN88UtlRy/DiamnNU1JKYdD5nTfOSKSdBlU="
             |}
          """.stripMargin

        val json2 =
          s"""
             |{
             |      "decryptedValue" : "3TW3L1raxsKBYuKvtKqPEQ=="
             |}
          """.stripMargin



        /*val sensitiveTestFormCrypto: Format[ProtectedBankDetails] = {
          implicit val s: OFormat[ProtectedBankDetails] = ProtectedBankDetails.bankDetailsFormats
          JsonEncryption.sensitiveEncrypterDecrypter(SensitiveProtectedBankDetails.apply)
        }

        val bankDetailsObj = BankDetails(hasUKBankAccount = Some(true), Some("AcountName"), Some("1111111"), Some(SortCode("00", "01", "02")),
          Some(BicSwiftCode("12345678901")), Some(Iban("iBanCode")))
        val protectedBankDetails = ProtectedBankDetails(Some(true), Some("AcountName"), Some("1111111"), Some(SortCode("00", "01", "02")),
          Some(BicSwiftCode("12345678901")), Some(Iban("iBanCode")))
        val bankDetailsModel = BankDetailsModel(true, Some(bankDetailsObj), Some(protectedBankDetails))

        val dumbleBee: JsValue = Json.toJson(protectedBankDetails)
        val gregorypeck = ProtectedBankDetails.format.reads(dumbleBee)*/



        //val encryptedValue = sensitiveTestFormCrypto.writes(SensitiveProtectedBankDetails(protectedBankDetails))
        //val decrypted = sensitiveTestFormCrypto.reads(Json.parse(json1))
        //decrypted.asOpt.value.decryptedValue.accountName.get.toString shouldBe "AcountName"
        //val entity: ProtectedBankDetails  = Json.fromJson(Json.parse(json1))(ProtectedBankDetails.format).asOpt.value
       // entity.accountName shouldBe Some("ATED Tax Payer")
       // entity.hasUKBankAccount mustBe Some(true)

       // println(entity.toString)


        //val sensitiveTestFormCryptoIban: Format[SensitiveIban] = {
        //  implicit val s: OFormat[Iban] = Iban.formats
       //   JsonEncryption.sensitiveEncrypterDecrypter(SensitiveIban.apply)
       // }


        val protectedBankDetails = ProtectedBankDetails(Some(SensitiveBoolean(true)),
          Some(SensitiveString("AcountName")), Some(SensitiveString("1111111")), Some(SensitiveSortCode(SortCode("00", "01", "02"))),
          Some(SensitiveBicSwiftCode(BicSwiftCode("12345678901"))), Some(SensitiveIban((Iban("iBanCode")))))

        val jsonsdf = Json.toJson(protectedBankDetails)(ProtectedBankDetails.bankDetailsFormats)
        println(s"MOHAN MOHAN ENCRYPTED = ${jsonsdf}")
       // val decrypted = sensitiveTestFormCryptoIban.reads(Json.parse(json2))
        val entity1 = Json.fromJson(Json.parse(json1))(ProtectedBankDetails.bankDetailsFormats).asOpt.value
        println(s"MOHAN MOHAN >>>>>>>>> ${entity1}")
        //entity1  shouldBe ProtectedBankDetails(Some(Sen))



        /*BankDetailsModel.format.reads(Json.parse(json)) match {
          case JsSuccess(success, JsPath(List())) =>
            success.hasBankDetails mustBe false
            success.protectedBankDetails.get.hasUKBankAccount mustBe true
            success.protectedBankDetails.get.accountName mustBe "ATED Tax Payer"
          case _ => fail()
        }*/

        val entity = Json.fromJson(Json.parse(json1))(ProtectedBankDetails.bankDetailsFormats).asOpt.value

        println(s"MOHAN MOHAN ${entity}")


      }
    }
  }
}



object CryptoFormatsSpec {
  implicit val crypto = SymmetricCryptoFactory.aesCrypto("P5xsJ9Nt+quxGZzB4DeLfw==")

  case class SensitiveTestEntity(
                                  normalString    : String,
                                  encryptedString : SensitiveString,
                                  encryptedBoolean: SensitiveBoolean,
                                  encryptedNumber : SensitiveBigDecimal
                                )

  object SensitiveTestEntity {
    implicit val formats = {
      implicit val sensitiveStringCrypto    : Format[SensitiveString]     = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
      implicit val sensitiveBooleanCrypto   : Format[SensitiveBoolean]    = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveBoolean.apply)
      implicit val sensitiveBigDecimalCrypto: Format[SensitiveBigDecimal] = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveBigDecimal.apply)
      Json.format[SensitiveTestEntity]
    }
  }

  case class TestForm(
                       name   : String,
                       sname  : String,
                       amount : Int,
                       sortCode : SortCode,
                       isValid: Boolean
                     )

  object TestForm {
    implicit val formats: OFormat[TestForm] = Json.format[TestForm]

  }

  case class SensitiveTestForm(override val decryptedValue: TestForm) extends Sensitive[TestForm]

  object SensitiveTestForm {
    implicit val formats: OFormat[SensitiveTestForm] = Json.format[SensitiveTestForm]

  }
}