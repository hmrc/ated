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

import play.api.libs.json._
import uk.gov.hmrc.crypto.json._
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, Protected, ApplicationCrypto}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads, Writes}

case class SortCode(firstElement: String, secondElement: String, thirdElement: String) {
  override def toString: String = s"$firstElement - $secondElement - $thirdElement"
}


object SortCode {

  implicit val formats = Json.format[SortCode]
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

case class BicSwiftCode(swiftCode: String) {
  val strippedSwiftCode = swiftCode.replaceAll(" ", "")
  require(BicSwiftCode.isValid(strippedSwiftCode), s"$swiftCode is not a valid BicSwiftCode.")

  def bankCode = {
    val BANK_CODE_START = 0
    val BANK_CODE_END = 4
    strippedSwiftCode.substring(BANK_CODE_START, BANK_CODE_END)
  }

  def countryCode = {
    val COUNTRY_CODE_START = 4
    val COUNTRY_CODE_END = 6
    strippedSwiftCode.substring(COUNTRY_CODE_START, COUNTRY_CODE_END)
  }

  def locationCode = {
    val LOCATION_CODE_START = 6
    val LOCATION_CODE_END = 8
    strippedSwiftCode.substring(LOCATION_CODE_START, LOCATION_CODE_END)
  }
  def branchCode = {
    val BRANCH_CODE_START = 8
    val BRANCH_CODE_END = 11
    if (strippedSwiftCode.length >= BRANCH_CODE_END)
      strippedSwiftCode.substring(BRANCH_CODE_START, BRANCH_CODE_END)
    else
      ""
  }

  override def toString = {
    s"$bankCode $countryCode $locationCode $branchCode".trim
  }

}


object BicSwiftCode extends (String => BicSwiftCode){
  implicit val formats = Json.format[BicSwiftCode]

  def isValid(swiftCode: String) = {
    val stripped = swiftCode.replaceAll(" ", "")
    val SWIFT_CODE_LENGTH_1 = 8
    val SWIFT_CODE_LENGTH_2 = 11
    stripped.length == SWIFT_CODE_LENGTH_1 || stripped.length == SWIFT_CODE_LENGTH_2
  }
}

case class Iban(iban: String) {
  val strippedIBan = iban.replaceAll(" ", "")
  require(Iban.isValid(strippedIBan), s"$iban is not a valid Iban.")

  override def toString = strippedIBan
}
object Iban extends (String => Iban){

  implicit val formats = Json.format[Iban]

  def isValid(iban: String) = {
    val stripped = iban.replaceAll(" ", "")
    val MIN_IBAN_LENGTH = 1
    val MAX_IBAN_LENGTH = 34
    stripped.length >= MIN_IBAN_LENGTH && stripped.length <= MAX_IBAN_LENGTH
  }
}

case class ProtectedBankDetails(hasUKBankAccount: Protected[Option[Boolean]],
                                     accountName: Protected[Option[String]],
                                     accountNumber: Protected[Option[String]],
                                     sortCode: Protected[Option[SortCode]],
                                     bicSwiftCode: Protected[Option[BicSwiftCode]],
                                     iban: Protected[Option[Iban]])

object ProtectedBankDetails {

  implicit val crypto = ApplicationCrypto.JsonCrypto

  object JsonOptionStringEncryption extends JsonEncryptor[Option[String]]
  object JsonOptionSortCodeEncryption extends JsonEncryptor[Option[SortCode]]
  object JsonOptionBicSwiftCodeEncryption extends JsonEncryptor[Option[BicSwiftCode]]
  object JsonOptionIbanEncryption extends JsonEncryptor[Option[Iban]]
  object JsonOptionBooleanEncryption extends JsonEncryptor[Option[Boolean]]

  object JsonOptionStringDecryption extends utils.JsonOptionDecryptor[String]
  object JsonOptionSortCodeDecryption extends utils.JsonOptionDecryptor[SortCode]
  object JsonOptionBicSwiftCodeDecryption extends utils.JsonOptionDecryptor[BicSwiftCode]
  object JsonOptionIbanDecryption extends utils.JsonOptionDecryptor[Iban]
  object JsonOptionBooleanDecryption extends utils.JsonOptionDecryptor[Boolean]

  implicit val bankDetailsFormats = {
    implicit val encryptedOptionStringFormats = JsonOptionStringEncryption
    implicit val encryptedOptionSortCodeFormats = JsonOptionSortCodeEncryption
    implicit val encryptedOptionSwiftBicCodeFormats = JsonOptionBicSwiftCodeEncryption
    implicit val encryptedOptionIbanFormats = JsonOptionIbanEncryption
    implicit val encryptedOptionBooleanFormats = JsonOptionBooleanEncryption

    implicit val decryptedOptionStringFormats = JsonOptionStringDecryption
    implicit val decryptedOptionSortCodeFormats = JsonOptionSortCodeDecryption
    implicit val decryptedOptionBicSiftCodeFormats = JsonOptionBicSwiftCodeDecryption
    implicit val decryptedOptionIbanFormats = JsonOptionIbanDecryption
    implicit val decryptedOptionBooleanFormats = JsonOptionBooleanDecryption

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
  implicit val format = Json.format[BankDetails]
}

object BankDetailsConversions {
  implicit def bankDetails2Protected(bankDetails: BankDetails): ProtectedBankDetails = {
    implicit def plain2Protected[A](value: A): Protected[A] = Protected(value)
    ProtectedBankDetails(
      bankDetails.hasUKBankAccount,
      bankDetails.accountName,
      bankDetails.accountNumber,
      bankDetails.sortCode,
      bankDetails.bicSwiftCode,
      bankDetails.iban
    )
  }

  implicit def protected2BankDetails(protectedBankDetails: ProtectedBankDetails): BankDetails = {
    implicit def protected2Plain[A](value: Protected[A]): A = value.decryptedValue
    BankDetails(
      protectedBankDetails.hasUKBankAccount,
      protectedBankDetails.accountName,
      protectedBankDetails.accountNumber,
      protectedBankDetails.sortCode,
      protectedBankDetails.bicSwiftCode,
      protectedBankDetails.iban
    )
  }
}

case class BankDetailsModel(hasBankDetails: Boolean = false,
                            bankDetails: Option[BankDetails] = None,
                            protectedBankDetails: Option[ProtectedBankDetails] = None)

object BankDetailsModel {
  implicit val format = Json.format[BankDetailsModel]
}
