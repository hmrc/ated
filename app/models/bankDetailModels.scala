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

import play.api.libs.json.{Json, Reads, Writes, _}
import uk.gov.hmrc.crypto.Sensitive.{SensitiveBoolean, SensitiveString}
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive, SymmetricCryptoFactory}
import utils.JsonOptionDecryptor

import scala.language.implicitConversions

case class SortCode(firstElement: String, secondElement: String, thirdElement: String) {
  override def toString: String = s"$firstElement - $secondElement - $thirdElement"
}


object SortCode {

  implicit val formats: OFormat[SortCode] = Json.format[SortCode]
  val FIRST_ELEMENT_START = 0
  val SECOND_ELEMENT_START = 2
  val THIRD_ELEMENT_START = 4
  val SORT_CODE_LENGTH = 6

  def fromString(sixDigits: String): SortCode = {
    require(sixDigits.length == SORT_CODE_LENGTH, s"Invalid SortCode, must be $SORT_CODE_LENGTH characters in length")
    apply(sixDigits.substring(FIRST_ELEMENT_START, SECOND_ELEMENT_START),
      sixDigits.substring(SECOND_ELEMENT_START, THIRD_ELEMENT_START),
      sixDigits.substring(THIRD_ELEMENT_START, SORT_CODE_LENGTH))
  }
}

case class SensitiveSortCode(override val decryptedValue: SortCode) extends Sensitive[SortCode]

object SensitiveSortCode {
  implicit val crypto = SymmetricCryptoFactory.aesCrypto("P5xsJ9Nt+quxGZzB4DeLfw==")
  implicit val formats = {
    implicit val sensitiveStringCrypto: Format[SensitiveString] = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    Json.format[SensitiveSortCode]
  }
}

case class BicSwiftCode(swiftCode: String) {
  val strippedSwiftCode: String = swiftCode.replaceAll(" ", "")
  require(BicSwiftCode.isValid(strippedSwiftCode), s"$swiftCode is not a valid BicSwiftCode.")

  def bankCode: String = {
    val BANK_CODE_START = 0
    val BANK_CODE_END = 4
    strippedSwiftCode.substring(BANK_CODE_START, BANK_CODE_END)
  }

  def countryCode: String = {
    val COUNTRY_CODE_START = 4
    val COUNTRY_CODE_END = 6
    strippedSwiftCode.substring(COUNTRY_CODE_START, COUNTRY_CODE_END)
  }

  def locationCode: String = {
    val LOCATION_CODE_START = 6
    val LOCATION_CODE_END = 8
    strippedSwiftCode.substring(LOCATION_CODE_START, LOCATION_CODE_END)
  }
  def branchCode: String = {
    val BRANCH_CODE_START = 8
    val BRANCH_CODE_END = 11
    if (strippedSwiftCode.length >= BRANCH_CODE_END)
      strippedSwiftCode.substring(BRANCH_CODE_START, BRANCH_CODE_END)
    else
      ""
  }

  override def toString: String = {
    s"$bankCode $countryCode $locationCode $branchCode".trim
  }

}

case class SensitiveBicSwiftCode(override val decryptedValue: BicSwiftCode) extends Sensitive[BicSwiftCode]

object SensitiveBicSwiftCode {
  implicit val crypto = SymmetricCryptoFactory.aesCrypto("P5xsJ9Nt+quxGZzB4DeLfw==")
  implicit val formats = {
    implicit val sensitiveStringCrypto: Format[SensitiveString] = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    Json.format[SensitiveBicSwiftCode]
  }
}


object BicSwiftCode extends (String => BicSwiftCode){
  implicit val formats: OFormat[BicSwiftCode] = Json.format[BicSwiftCode]

  def isValid(swiftCode: String): Boolean = {
    val stripped = swiftCode.replaceAll(" ", "")
    val SWIFT_CODE_LENGTH_1 = 8
    val SWIFT_CODE_LENGTH_2 = 11
    stripped.length == SWIFT_CODE_LENGTH_1 || stripped.length == SWIFT_CODE_LENGTH_2
  }
}

case class Iban(iban: String) {
  val strippedIBan: String = iban.replaceAll(" ", "")
  require(Iban.isValid(strippedIBan), s"$iban is not a valid Iban.")

  override def toString = strippedIBan
}
object Iban extends (String => Iban){

  implicit val formats: OFormat[Iban] = Json.format[Iban]

  def isValid(iban: String): Boolean = {
    val stripped = iban.replaceAll(" ", "")
    val MIN_IBAN_LENGTH = 1
    val MAX_IBAN_LENGTH = 34
    stripped.length >= MIN_IBAN_LENGTH && stripped.length <= MAX_IBAN_LENGTH
  }
}

case class SensitiveIban( override val decryptedValue: Iban) extends Sensitive[Iban]

object SensitiveIban {
  implicit val crypto = SymmetricCryptoFactory.aesCrypto("P5xsJ9Nt+quxGZzB4DeLfw==")
  implicit val formats = {
    implicit val sensitiveStringCrypto: Format[SensitiveString] = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    Json.format[SensitiveIban]
  }
}

case class ProtectedBankDetails(hasUKBankAccount: Option[SensitiveBoolean],
                                     accountName: Option[SensitiveString],
                                     accountNumber: Option[SensitiveString],
                                     sortCode: Option[SensitiveSortCode],
                                     bicSwiftCode: Option[SensitiveBicSwiftCode],
                                     iban: Option[SensitiveIban])

object ProtectedBankDetails {
  def bankDetailsFormats(implicit crypto: Encrypter with Decrypter): OFormat[ProtectedBankDetails] = {
    /*implicit val encryptedOptionStringFormats: Writes[SensitiveString] = JsonEncryption.sensitiveEncrypter[String, SensitiveString]
    implicit val encryptedOptionSortCodeFormats: Writes[SensitiveSortCode] = JsonEncryption.sensitiveEncrypter[Option[SortCode], SensitiveSortCode]
    implicit val encryptedOptionSwiftBicCodeFormats: Writes[SensitiveBicSwiftCode] = JsonEncryption.sensitiveEncrypter[BicSwiftCode, SensitiveBicSwiftCode]
    implicit val encryptedOptionIbanFormats: Writes[SensitiveIban] = JsonEncryption.sensitiveEncrypter[Iban, SensitiveIban]
    implicit val encryptedOptionBooleanFormats: Writes[SensitiveBoolean] = JsonEncryption.sensitiveEncrypter[Boolean, SensitiveBoolean]*/

    implicit val decryptedOptionBooleanFormats = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveBoolean.apply)
    implicit val decryptedOptionStringFormats = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    implicit val decryptedOptionSortCodeFormats = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveSortCode.apply)
    implicit val decryptedOptionBicSiftCodeFormats = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveBicSwiftCode.apply)
    implicit val decryptedOptionIbanFormats = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveIban.apply)

    Json.format[ProtectedBankDetails]
  }
}

case class BankDetails(hasUKBankAccount: Option[Boolean] = None,
                        accountName: Option[String] = None,
                        accountNumber: Option[String] = None,
                        sortCode: Option[SortCode] = None,
                        bicSwiftCode: Option[BicSwiftCode] = None,
                        iban: Option[Iban] = None)

object BankDetails {
  implicit lazy val format: OFormat[BankDetails] = Json.format[BankDetails]
}

object BankDetailsConversions {
  implicit def bankDetails2Protected(bankDetails: BankDetails): ProtectedBankDetails = {
    //implicit def plain2Protected[A](value: A): Protected[A] = Protected(value)
    ProtectedBankDetails(
      bankDetails.hasUKBankAccount.map(x => SensitiveBoolean(x)),
      bankDetails.accountName.map(x => SensitiveString(x)),
      bankDetails.accountNumber.map(x => SensitiveString(x)),
      bankDetails.sortCode.map(x => SensitiveSortCode(x)),
      bankDetails.bicSwiftCode.map(x => SensitiveBicSwiftCode(x)),
      bankDetails.iban.map(x => SensitiveIban(x))
    )
  }

  implicit def protected2BankDetails(protectedBankDetails: ProtectedBankDetails): BankDetails = {
    //implicit def protected2Plain[A](value: Protected[A]): A = value.decryptedValue
    BankDetails(
      protectedBankDetails.hasUKBankAccount.map(x => x.decryptedValue),
      protectedBankDetails.accountName.map(x => x.decryptedValue),
      protectedBankDetails.accountNumber.map(x => x.decryptedValue),
      protectedBankDetails.sortCode.map(x => x.decryptedValue),
      protectedBankDetails.bicSwiftCode.map(x => x.decryptedValue),
      protectedBankDetails.iban.map(x => x.decryptedValue)
    )
  }
}

case class BankDetailsModel(hasBankDetails: Boolean = false,
                            bankDetails: Option[BankDetails] = None,
                            protectedBankDetails: Option[ProtectedBankDetails] = None)

object BankDetailsModel {
  def format(implicit crypto: Encrypter with Decrypter): Format[BankDetailsModel] = {
    val reads = new Reads[BankDetailsModel] {
      override def reads(json: JsValue): JsResult[BankDetailsModel] = {
        val hasBankDetails: Option[Boolean] = (json \ "hasBankDetails").asOpt[Boolean]
        val bankDetails: Option[BankDetails] = (json \ "bankDetails").asOpt[BankDetails]
        val protectedJson: Option[ProtectedBankDetails] = (json \ "protectedBankDetails")
          .asOpt[JsValue]
          .flatMap(ProtectedBankDetails.bankDetailsFormats.reads(_).asOpt)

        (hasBankDetails, bankDetails, protectedJson) match {
          case (Some(hsb), bd, pbd) => JsSuccess(BankDetailsModel(hsb, bd, pbd))
          case _                    => JsError("[BankDetailsModel] Failed to read bank details model")
        }
      }
    }

    val writes: Writes[BankDetailsModel] = new Writes[BankDetailsModel] {
      override def writes(o: BankDetailsModel): JsValue = {
        val bankDetails = o.bankDetails match {
          case Some(bd) => Json.obj("bankDetails" -> Json.toJson(bd))
          case _        => Json.obj()
        }

        val protectedBankDetails = o.protectedBankDetails match {
          case Some(pbd) => Json.obj("protectedBankDetails" -> ProtectedBankDetails.bankDetailsFormats.writes(pbd))
          case _         => Json.obj()
        }

        Json.obj(
          "hasBankDetails" -> o.hasBankDetails
        ) ++ bankDetails ++ protectedBankDetails
      }
    }

    Format(reads, writes)
  }
}
