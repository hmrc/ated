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

package builders

import models._
import org.joda.time.{DateTime, LocalDate}
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.Protected

object ChangeLiabilityReturnBuilder extends PlaySpec with OneServerPerSuite {

  def generateFormBundleResponse(periodKey: Int): FormBundleReturn = {
    val formBundleAddress = FormBundleAddress("line1", "line2", None, None, None, "GB")
    val x = FormBundlePropertyDetails(Some("12345678"), formBundleAddress, additionalDetails = Some("supportingInfo"))
    val lineItem1 = FormBundleProperty(BigDecimal(5000000), new LocalDate(s"$periodKey-04-01"), new LocalDate(s"$periodKey-08-31"), "Liability", None)
    val lineItem2 = FormBundleProperty(BigDecimal(5000000), new LocalDate(s"$periodKey-09-01"), new LocalDate(s"${periodKey+1}-03-31"), "Relief", Some("Relief"))

    FormBundleReturn(periodKey = periodKey.toString,
      propertyDetails = x,
      dateOfAcquisition = None,
      valueAtAcquisition = None,
      dateOfValuation = new LocalDate(s"$periodKey-05-05"),
      localAuthorityCode = None,
      professionalValuation = true,
      taxAvoidanceScheme = Some("taxAvoidanceScheme"),
      ninetyDayRuleApplies = true,
      dateOfSubmission = new LocalDate(s"$periodKey-05-05"),
      liabilityAmount = BigDecimal(123.23),
      paymentReference = "payment-ref-123",
      lineItem = Seq(lineItem1, lineItem2))
  }

  def generateFormBundleResponse1(periodKey: Int): FormBundleReturn = {
    val formBundleAddress = FormBundleAddress("line1", "line2", None, None, None, "GB")
    val x = FormBundlePropertyDetails(Some("12345678"), formBundleAddress, additionalDetails = Some("supportingInfo"))
    val lineItem1 = FormBundleProperty(BigDecimal(5000000), new LocalDate(s"$periodKey-04-01"), new LocalDate(s"$periodKey-08-31"), "Liability", None)
    val lineItem2 = FormBundleProperty(BigDecimal(5000000), new LocalDate(s"$periodKey-09-01"), new LocalDate(s"${periodKey+1}-03-31"), "Relief", Some("Relief"))

    FormBundleReturn(periodKey = periodKey.toString,
      propertyDetails = x,
      dateOfAcquisition = Some(new LocalDate(s"$periodKey-05-05")),
      valueAtAcquisition = None,
      dateOfValuation = new LocalDate(s"$periodKey-05-05"),
      localAuthorityCode = None,
      professionalValuation = true,
      taxAvoidanceScheme = Some("taxAvoidanceScheme"),
      ninetyDayRuleApplies = true,
      dateOfSubmission = new LocalDate(s"$periodKey-05-05"),
      liabilityAmount = BigDecimal(123.23),
      paymentReference = "payment-ref-123",
      lineItem = Seq(lineItem1, lineItem2))
  }

  def generateChangeLiabilityReturn(periodKey: Int, formBundle: String, bankDetails: Option[BankDetailsModel] = None): PropertyDetails = {
    val formBundleResponse = generateFormBundleResponse(periodKey)
    val address = PropertyDetailsAddress("line1", "line2", None, None, None)
    val titleRef = PropertyDetailsTitle("12345678")

    PropertyDetails(atedRefNo = "ated-ref-123",
      id = formBundle,
      addressProperty = address,
      title = Some(titleRef),
      value = Some(generateLiabilityValueDetails(periodKey)),
      periodKey = periodKey,
      period = PropertyDetailsBuilder.getPropertyDetailsPeriodFull(periodKey),
      calculated = None,
      bankDetails = bankDetails,
      formBundleReturn = Some(formBundleResponse))
  }

  def generateChangeLiabilityReturnWithLiabilty(periodKey: Int, formBundle: String, bankDetails: Option[BankDetailsModel] = None): PropertyDetails = {
    val formBundleResponse = generateFormBundleResponse1(periodKey)
    val address = PropertyDetailsAddress("line1", "line2", None, None, None)
    val titleRef = PropertyDetailsTitle("12345678")

    PropertyDetails(atedRefNo = "ated-ref-123",
      id = formBundle,
      addressProperty = address,
      title = Some(titleRef),
      value = Some(generateLiabilityValueDetails(periodKey)),
      periodKey = periodKey,
      period = PropertyDetailsBuilder.getPropertyDetailsPeriodFull(periodKey),
      calculated = Some(generateCalculated1),
      bankDetails = bankDetails,
      formBundleReturn = Some(formBundleResponse))
  }

  def generateLiabilityValueDetails(periodKey: Int): PropertyDetailsValue = {
    PropertyDetailsValue(hasValueChanged = Some(false))
  }

  def generateLiabilityInternationalBankDetails: BankDetailsModel = {
    BankDetailsModel(hasBankDetails = true,
      bankDetails = Some(BankDetails(hasUKBankAccount = Some(false),
        accountName = Some("accountName"), accountNumber = None, sortCode = None,
        bicSwiftCode = Some(BicSwiftCode("12345678901")),
        iban = Some(Iban("IbanNumber"))
      )),
      protectedBankDetails = None
    )
  }

  def generateLiabilityBankDetails: BankDetailsModel = {
    BankDetailsModel(hasBankDetails = true,
      bankDetails = Some(BankDetails(hasUKBankAccount = Some(true),
        accountName = Some("accountName"), accountNumber = Some("1234567890"), sortCode = Some(SortCode("11", "22", "33")))),
      protectedBankDetails = None
    )
  }

  def generateLiabilityBankDetailsBlank: BankDetailsModel = {
    BankDetailsModel(hasBankDetails = true,
      bankDetails = Some(BankDetails(hasUKBankAccount = Some(true), accountName = Some(""), accountNumber = Some(""), sortCode = Some(SortCode("", "", "")))),
      protectedBankDetails = None
    )
  }

  def generateLiabilityBankDetailsNone: BankDetailsModel = {
    BankDetailsModel(hasBankDetails = true,
      bankDetails = Some(BankDetails(hasUKBankAccount = Some(true), accountName = None, accountNumber = None, sortCode = None)),
      protectedBankDetails = None
    )
  }

  def generateLiabilityBankDetailsNoBankDetails: BankDetailsModel = {
    BankDetailsModel(hasBankDetails = false,
      bankDetails = None,
      protectedBankDetails = None
    )
  }

  def covertToProtectedBankDetails(bankDetailsModel : BankDetailsModel): BankDetailsModel = {
    val pd = bankDetailsModel.bankDetails.map{
      bd =>
        ProtectedBankDetails(
          Protected(bd.hasUKBankAccount),
          Protected(bd.accountName),
          Protected(bd.accountNumber),
          Protected(bd.sortCode),
          Protected(bd.bicSwiftCode),
          Protected(bd.iban)
        )
    }
    bankDetailsModel.copy(bankDetails = None, protectedBankDetails = pd)
  }

  def generateLiabilityProtectedInternationalBankDetails: BankDetailsModel = {
    covertToProtectedBankDetails(generateLiabilityInternationalBankDetails)
  }

  def generateLiabilityProtectedBankDetails: BankDetailsModel = {
    covertToProtectedBankDetails(generateLiabilityBankDetails)
  }

  def generateLiabilityProtectedBankDetailsBlank: BankDetailsModel = {
    covertToProtectedBankDetails(generateLiabilityBankDetailsNone)
  }

  def generateLiabilityProtectedBankDetailsNone: BankDetailsModel = {
    covertToProtectedBankDetails(generateLiabilityBankDetailsBlank)
  }

  def generateLiabilityProtectedBankDetailsNoBankDetails: BankDetailsModel = {
    covertToProtectedBankDetails(generateLiabilityBankDetailsNoBankDetails)
  }


  def updateChangeLiabilityReturnWithAddress(periodKey: Int, formBundle: String, addressRef: PropertyDetailsAddress) = {
    val x = generateChangeLiabilityReturn(periodKey, formBundle)
    x.copy(addressProperty = addressRef)
  }

  def updateChangeLiabilityReturnWithTitle(periodKey: Int, formBundle: String, title: PropertyDetailsTitle) = {
    val x = generateChangeLiabilityReturn(periodKey, formBundle)
    x.copy(title = Some(title))
  }

  def updateChangeLiabilityReturnWithValue(periodKey: Int, formBundle: String, value: PropertyDetailsValue) = {
    val x = generateChangeLiabilityReturn(periodKey, formBundle)
    x.copy(value = Some(value))
  }

  def updateChangeLiabilityReturnWithPeriod(periodKey: Int, formBundle: String, period: PropertyDetailsPeriod) = {
    val x = generateChangeLiabilityReturn(periodKey, formBundle)
    x.copy(period = Some(period))
  }

  def updateChangeLiabilityReturnWithBankDetails(periodKey: Int, formBundle: String, bankDetails: BankDetailsModel) = {
    val x = generateChangeLiabilityReturn(periodKey, formBundle)
    x.copy(bankDetails = Some(bankDetails))
  }

  def updateChangeLiabilityReturnWithProtectedBankDetails(periodKey: Int, formBundle: String, bankDetails: BankDetailsModel) = {
    val x = generateChangeLiabilityReturn(periodKey, formBundle)
    x.copy(bankDetails = Some(bankDetails))
  }

  def generateCalculated = {
    val liabilityPeriods = List(CalculatedPeriod(BigDecimal(2500000),new LocalDate("2015-4-1"), new LocalDate("2016-3-31"), "Liability"))
    val reliefPeriods = Nil
    PropertyDetailsCalculated(liabilityPeriods = liabilityPeriods, reliefPeriods = reliefPeriods,
      valuationDateToUse = Some(new LocalDate("2015-5-15")),
      acquistionDateToUse = None, acquistionValueToUse = None, professionalValuation = Some(true),
      liabilityAmount = Some(4500),
      amountDueOrRefund = Some(BigDecimal(-500.00)))
  }

  def generateCalculated1 = {
    val liabilityPeriods = List(CalculatedPeriod(BigDecimal(2500000),new LocalDate("2015-4-1"), new LocalDate("2016-3-31"), "Liability"))
    val reliefPeriods = Nil
    PropertyDetailsCalculated(liabilityPeriods = liabilityPeriods, reliefPeriods = reliefPeriods,
      valuationDateToUse = Some(new LocalDate("2015-5-15")),
      acquistionDateToUse = None, acquistionValueToUse = None, professionalValuation = Some(true),
      liabilityAmount = None,
      amountDueOrRefund = Some(BigDecimal(-500.00)))
  }

  def generateEditLiabilityReturnResponse(oldFormBundleNo: String) = {
    val liability = EditLiabilityReturnsResponse("Post", oldFormBundleNo, Some("1234567890123"), liabilityAmount = BigDecimal(2000.00), amountDueOrRefund = BigDecimal(-500.00), paymentReference = Some("payment-ref-123"))
    EditLiabilityReturnsResponseModel(processingDate = new DateTime("2016-04-20T12:41:41.839+01:00"), liabilityReturnResponse = Seq(liability), accountBalance = BigDecimal(1200.00))
  }

}
