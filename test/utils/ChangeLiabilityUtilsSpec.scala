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

package utils

import builders.{PropertyDetailsBuilder, ChangeLiabilityReturnBuilder}
import models._
import org.joda.time.LocalDate
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import uk.gov.hmrc.crypto.Protected
import uk.gov.hmrc.http.InternalServerException

class ChangeLiabilityUtilsSpec extends PlaySpec with OneServerPerSuite {

  "ChangeLiabilityUtils" must {
    "generateAddressFromLiabilityReturn" must {
      "will generate PropertyDetailsAddressRef from FormBundleReturn" in {
        val inputFormBundleResponse = FormBundleReturn("2015", FormBundlePropertyDetails(titleNumber = None, address = FormBundleAddress(addressLine1 = "line1", addressLine2 = "line2", addressLine3 = Some("line3"), addressLine4 = Some("line4"), None, countryCode = "GB"), additionalDetails = Some("additional details")), dateOfValuation = LocalDate.now(), professionalValuation = true, ninetyDayRuleApplies = false, dateOfAcquisition = None, valueAtAcquisition = None, localAuthorityCode = None, dateOfSubmission = LocalDate.now(), taxAvoidanceScheme = Some("12345678"), liabilityAmount = BigDecimal(123.23), paymentReference = "payment-ref-123", lineItem = Seq())
        ChangeLiabilityUtils.generateAddressFromLiabilityReturn(inputFormBundleResponse) must be(PropertyDetailsAddress(line_1 = "line1", line_2 = "line2", line_3 = Some("line3"), line_4 = Some("line4"), postcode = None))
      }
    }

    "generatePeriodFromLiabilityReturn" must {
      "will generate PropertyDetailsPeriod from FormBundleReturn, if taxAvoidance exist" in {
        val inputFormBundleResponse = FormBundleReturn("2015", FormBundlePropertyDetails(titleNumber = Some("1234"),
          address = FormBundleAddress(addressLine1 = "line1", addressLine2 = "line2", addressLine3 = Some("line3"), addressLine4 = Some("line4"), None, countryCode = "GB"),
          additionalDetails = Some("additional details")),
          dateOfValuation = LocalDate.now(), professionalValuation = true, ninetyDayRuleApplies = false, dateOfAcquisition = None, valueAtAcquisition = None, localAuthorityCode = Some("1234"),
          dateOfSubmission = LocalDate.now(), taxAvoidanceScheme = Some("12345678"), liabilityAmount = BigDecimal(123.23), paymentReference = "payment-ref-123", lineItem = Seq())

        val result = ChangeLiabilityUtils.generatePeriodFromLiabilityReturn(inputFormBundleResponse)
        result must be(PropertyDetailsPeriod(isFullPeriod = Some(false), isInRelief = None, liabilityPeriods = Nil, reliefPeriods = Nil, isTaxAvoidance = Some(true), taxAvoidanceScheme = Some("12345678"), supportingInfo = Some("additional details")))
      }

      "will generate PropertyDetailsPeriod from FormBundleReturn, if taxAvoidance does not exist" in {
        val inputFormBundleResponse = FormBundleReturn("2015", FormBundlePropertyDetails(titleNumber = Some("1234"),
          address = FormBundleAddress(addressLine1 = "line1", addressLine2 = "line2", addressLine3 = Some("line3"), addressLine4 = Some("line4"), None, countryCode = "GB"),
          additionalDetails = Some("additional details")),
          dateOfValuation = LocalDate.now(), professionalValuation = true, ninetyDayRuleApplies = false, dateOfAcquisition = None, valueAtAcquisition = None,
          localAuthorityCode = Some("1234"), dateOfSubmission = LocalDate.now(), taxAvoidanceScheme = None,
          liabilityAmount = BigDecimal(123.23), paymentReference = "payment-ref-123", lineItem = Seq())

        val result = ChangeLiabilityUtils.generatePeriodFromLiabilityReturn(inputFormBundleResponse)
        result must be(PropertyDetailsPeriod(isFullPeriod = Some(false), isInRelief = None, liabilityPeriods = Nil, reliefPeriods = Nil, isTaxAvoidance = Some(false), taxAvoidanceScheme = None, supportingInfo = Some("additional details")))

      }

      "will generate PropertyDetailsPeriod from FormBundleReturn, if we have a single Full Period Liability" in {
        val inputFormBundleResponse = FormBundleReturn("2015", FormBundlePropertyDetails(titleNumber = Some("1234"),
          address = FormBundleAddress(addressLine1 = "line1", addressLine2 = "line2", addressLine3 = Some("line3"), addressLine4 = Some("line4"), None, countryCode = "GB"),
          additionalDetails = Some("additional details")),
          dateOfValuation = LocalDate.now(), professionalValuation = true, ninetyDayRuleApplies = false, dateOfAcquisition = None, valueAtAcquisition = None,
          localAuthorityCode = Some("1234"), dateOfSubmission = LocalDate.now(), taxAvoidanceScheme = None,
          liabilityAmount = BigDecimal(123.23), paymentReference = "payment-ref-123",
          lineItem = Seq(FormBundleProperty(5000000, new LocalDate("2015-04-01"), new LocalDate("2016-03-31"), "Liability", None)))

        val result = ChangeLiabilityUtils.generatePeriodFromLiabilityReturn(inputFormBundleResponse)
        result must be(PropertyDetailsPeriod(isFullPeriod = Some(true), isInRelief = None,
          liabilityPeriods = List(LineItem(lineItemType = "Liability", startDate = new LocalDate("2015-04-01"), endDate = new LocalDate("2016-03-31"))),
          reliefPeriods = Nil, isTaxAvoidance = Some(false), taxAvoidanceScheme = None, supportingInfo = Some("additional details")))

      }
    }


    "getNinetyDayRuleApplies" must {
      "return form-bundle-return nintyDayRule value, if propertyValue questionaire is defined with all values as None" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val result1 = ChangeLiabilityUtils.getNinetyDayRuleApplies(cL1)
        result1 must be(true)
      }

      "return form-bundle-return nintyDayRule value, if propertyValue questionaire is defined but hasValueChanged is not defined" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val cL2 = cL1.copy(value = Some(PropertyDetailsValue(hasValueChanged = None)))
        val result1 = ChangeLiabilityUtils.getNinetyDayRuleApplies(cL2)
        result1 must be(true)
      }

      "return form-bundle-return nintyDayRule value, if propertyValue questionaire is defined and hasValueChanged is true, but isNewBuild is true" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val cL2 = cL1.copy(value = Some(PropertyDetailsValue(isNewBuild = Some(false), hasValueChanged = Some(true))))
        val result1 = ChangeLiabilityUtils.getNinetyDayRuleApplies(cL2)
        result1 must be(false)
      }

      "return form-bundle-return nintyDayRule value, if propertyValue questionaire is defined and hasValueChanged is true, but isNewBuild is not defined" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val cL2 = cL1.copy(value = Some(PropertyDetailsValue(isNewBuild = None, hasValueChanged = Some(true))))
        val result1 = ChangeLiabilityUtils.getNinetyDayRuleApplies(cL2)
        result1 must be(true)
      }

      "return form-bundle-return nintyDayRule value, if propertyValue questionaire is not defined" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val cL2 = cL1.copy(value = None)
        val result1 = ChangeLiabilityUtils.getNinetyDayRuleApplies(cL2)
        result1 must be(true)
      }
    }

    "getEtmpBankDetails" must {

      val bankDetails = ChangeLiabilityReturnBuilder.generateLiabilityProtectedInternationalBankDetails
      "International Bank Account" must {
        "return etmp bank details model, for valid bank details for None UK Bank Account" in {
          val expectedResponse = EtmpBankDetails("accountName",None, Some(InternationalAccount("12345678901", "IbanNumber")))
          val result1 = ChangeLiabilityUtils.getEtmpBankDetails(Some(bankDetails))
          result1 must be(Some(expectedResponse))
        }

        "return None, for bank details - with no SwiftBicCode" in {
          val noSwiftBic = bankDetails.protectedBankDetails.map(_.copy(bicSwiftCode = Protected(None)))
          val bankDetailsWithNoSwiftBic = bankDetails.copy(protectedBankDetails = noSwiftBic)
          val result1 = ChangeLiabilityUtils.getEtmpBankDetails(Some(bankDetailsWithNoSwiftBic))
          result1 must be(None)
        }

        "return None, for bank details - with no Iban" in {
          val noIban = bankDetails.protectedBankDetails.map(_.copy(iban = Protected(None)))
          val bankDetailsWithNoIban = bankDetails.copy(protectedBankDetails = noIban)
          val result1 = ChangeLiabilityUtils.getEtmpBankDetails(Some(bankDetailsWithNoIban))
          result1 must be(None)
        }

        "return None, for bank details - with account Name" in {
          val noAccountName = bankDetails.protectedBankDetails.map(_.copy(accountName = Protected(None)))
          val bankDetailsWithNoAccount = bankDetails.copy(protectedBankDetails = noAccountName)
          val result1 = ChangeLiabilityUtils.getEtmpBankDetails(Some(bankDetailsWithNoAccount))
          result1 must be(None)
        }
      }

      "UK Bank Account" must {
        "return etmp bank details model, for valid bank details for UK Bank Account" in {
          val expectedResponse = EtmpBankDetails("accountName", Some(UKAccount("112233", "1234567890")), None)
          val result1 = ChangeLiabilityUtils.getEtmpBankDetails(Some(ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetails))
          result1 must be(Some(expectedResponse))
        }

        "return None, for bank details - with no accountname, accountNumber or sortCode" in {
          val result1 = ChangeLiabilityUtils.getEtmpBankDetails(Some(ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetailsBlank))
          result1 must be(None)
        }
        "return None, for bank details - with accountname, accountNumber or sortCode as None" in {
          val cL1 = ChangeLiabilityReturnBuilder.updateChangeLiabilityReturnWithProtectedBankDetails(2015, "123456789012", ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetailsNone)
          val result1 = ChangeLiabilityUtils.getEtmpBankDetails(cL1.bankDetails)
          result1 must be(None)
        }
        "return None, for No bank details and No bank account" in {
          val cL1 = ChangeLiabilityReturnBuilder.updateChangeLiabilityReturnWithProtectedBankDetails(2015, "123456789012", ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetailsNoBankDetails)
          val result1 = ChangeLiabilityUtils.getEtmpBankDetails(cL1.bankDetails)
          result1 must be(None)
        }
      }

      "return None, if hasBankDetails is false or hasUKBankAccount is false, or if any of the required string is of 0 length" in {
        val bank1 = BankDetailsModel(hasBankDetails = true, bankDetails = Some(BankDetails(hasUKBankAccount = Some(false), accountName = None, accountNumber = None, sortCode = None)))
        val result1 = ChangeLiabilityUtils.getEtmpBankDetails(Some(ChangeLiabilityReturnBuilder.covertToProtectedBankDetails(bank1)))
        result1 must be(None)

        val bank2 = BankDetailsModel(hasBankDetails = false, bankDetails = Some(BankDetails(hasUKBankAccount = None, accountName = None, accountNumber = None, sortCode = None)))
        val result2 = ChangeLiabilityUtils.getEtmpBankDetails(Some(ChangeLiabilityReturnBuilder.covertToProtectedBankDetails(bank2)))
        result2 must be(None)

        val bank3 = BankDetailsModel(hasBankDetails = true, bankDetails = Some(BankDetails(hasUKBankAccount = Some(true), accountName = Some(""), accountNumber = Some("123"), sortCode = None)))
        val result3 = ChangeLiabilityUtils.getEtmpBankDetails(Some(ChangeLiabilityReturnBuilder.covertToProtectedBankDetails(bank3)))
        result3 must be(None)

        val bank4 = BankDetailsModel(hasBankDetails = true, bankDetails = Some(BankDetails(hasUKBankAccount = Some(true), accountName = None, accountNumber = None, sortCode = None)))
        val result4 = ChangeLiabilityUtils.getEtmpBankDetails(Some(ChangeLiabilityReturnBuilder.covertToProtectedBankDetails(bank4)))
        result4 must be(None)

        val bank5 = BankDetailsModel(hasBankDetails = true, bankDetails = None)
        val result5 = ChangeLiabilityUtils.getEtmpBankDetails(Some(ChangeLiabilityReturnBuilder.covertToProtectedBankDetails(bank5)))
        result5 must be(None)
      }
    }

    "createChangeLiabilityReturnRequest" must {
      "return None, if calculated is cached with wrong data, like valuationDateToUse is empty" in {
        val calc1 = ChangeLiabilityReturnBuilder.generateCalculated
        val calc2 = calc1.copy(valuationDateToUse = None)
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val cL2 = cL1.copy(calculated = Some(calc2))
        val result1 = ChangeLiabilityUtils.createChangeLiabilityReturnRequest(cL2, "mode")
        result1 must be(None)
      }
    }


    "changeLiabilityCalculated" must {
      "override value due to overrideValuationDateIfValuedByAgent method, if valuedByAgent = Some(true)" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2015)
        val v2 = value1.copy(hasValueChanged = Some(true), isValuedByAgent = Some(true), anAcquisition = Some(true),
          isPropertyRevalued = Some(true),
          revaluedValue = Some(BigDecimal(5000.00)),
          revaluedDate = Some(new LocalDate("2015-05-15"))
        )
        val cL2 = cL1.copy(value = Some(v2))
        val result1 = ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        result1.valuationDateToUse must be(Some(new LocalDate("2015-05-15")))
      }

      "throw an exception if liabilityValueDetails is not defined" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val cL2 = cL1.copy(value = None)

        val thrown = the[InternalServerException] thrownBy ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        thrown.getMessage must include("[PropertyDetailsUtils][getLineItemValues] - No Value Found")
      }

      "throw an exception if liabilityValueDetails is defined but hasValueChanged is not defined" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2015)
        val v2 = value1.copy(hasValueChanged = None)
        val cL2 = cL1.copy(value = Some(v2))
        val thrown = the[InternalServerException] thrownBy ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        thrown.getMessage must include("[PropertyDetailsUtils][getLineItemValues] - No Value Found")
      }

      "return (Some(valuationDate),Some(valueToUse)), due to getValueAndDate - anAcquisition = true & isPropertyRevalued = true" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2015)
        val v2 = value1.copy(hasValueChanged = Some(true), anAcquisition = Some(true), isPropertyRevalued = Some(true), revaluedValue = Some(BigDecimal(1500000)), revaluedDate = Some(new LocalDate("2015-05-15")))
        val cL2 = cL1.copy(value = Some(v2))
        val result1 = ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        result1.valuationDateToUse must be(Some(new LocalDate("2015-05-15")))
      }

      "return (Some(valuationDate),Some(valueToUse)), due to getValueAndDate - anAcquisition = false & isOwnedBefore2012 = true" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2015)
        val v2 = value1.copy(hasValueChanged = Some(true), anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(true), ownedBeforePolicyYearValue = Some(BigDecimal(1500000)))
        val cL2 = cL1.copy(value = Some(v2))
        val result1 = ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        result1.valuationDateToUse must be(Some(new LocalDate("2012-04-01")))
      }

      "return (Some(valuationDate),Some(valueToUse)), due to getValueAndDate - anAcquisition = false & isOwnedBeforePolicyYear2017 = true" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2019, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2019)
        val v2 = value1.copy(hasValueChanged = Some(true), anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(true), ownedBeforePolicyYearValue = Some(BigDecimal(1500000)))
        val cL2 = cL1.copy(value = Some(v2))
        val result1 = ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        result1.valuationDateToUse must be(Some(new LocalDate("2017-04-01")))
      }

      "return (Some(valuationDate),Some(valueToUse)), due to getValueAndDate - anAcquisition = false & isOwnedBefore2012 = false, isNewBuild = true" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2015)
        val v2 = value1.copy(hasValueChanged = Some(true), anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(false),
          isNewBuild = Some(true), newBuildValue = Some(BigDecimal(1500000)), newBuildDate = Some(new LocalDate("2015-06-06")),
          localAuthRegDate = Some(new LocalDate("2015-05-05")))
        val cL2 = cL1.copy(value = Some(v2))
        val result1 = ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        result1.valuationDateToUse must be(Some(new LocalDate("2015-05-05")))
      }

      "return (Some(valuationDate),Some(valueToUse)), due to getValueAndDate - anAcquisition = false & isOwnedBefore2012 = false, isNewBuild = false" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2016)
        val v2 = value1.copy(hasValueChanged = Some(true), anAcquisition = Some(false), isOwnedBeforePolicyYear = Some(false), isNewBuild = Some(false), notNewBuildValue = Some(BigDecimal(1500000)), notNewBuildDate = Some(new LocalDate("2015-06-06")))
        val cL2 = cL1.copy(value = Some(v2))
        val result1 = ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        result1.valuationDateToUse must be(Some(new LocalDate("2015-06-06")))
      }

      "return (Some(acquisitionValueToUse),Some(acquisitionDateToUse)), due to getAcquisitionValueAndDate - partAcqDispDate is defined" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2016)
        val v2 = value1.copy(hasValueChanged = Some(true), partAcqDispDate = Some(new LocalDate("2015-05-01")),
          anAcquisition = Some(true), isPropertyRevalued = Some(true), revaluedValue = Some(BigDecimal(1200000)))
        val cL2 = cL1.copy(value = Some(v2))
        val result1 = ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        result1.acquistionDateToUse must be(Some(new LocalDate("2015-05-01")))
        result1.acquistionValueToUse must be(Some(BigDecimal(1200000)))
      }

      "return (Some(acquisitionValueToUse),Some(acquisitionDateToUse)), due to getAcquisitionValueAndDate if no periods are defined" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2016)
        val v2 = value1.copy(hasValueChanged = Some(true), partAcqDispDate = Some(new LocalDate("2015-05-01")),
          anAcquisition = Some(true), isPropertyRevalued = Some(true), revaluedValue = Some(BigDecimal(1200000)))
        val cL2 = cL1.copy(value = Some(v2), period = None)
        val result1 = ChangeLiabilityUtils.changeLiabilityCalculated(cL2, None)
        result1.acquistionDateToUse must be(Some(new LocalDate("2015-05-01")))
        result1.acquistionValueToUse must be(Some(BigDecimal(1200000)))
      }
    }

    "getProfessionalValuation" must {
      "return some(true), if is valued by agent is selected as yes" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2016)
        val v2 = value1.copy(hasValueChanged = Some(true), isPropertyRevalued = Some(true)) // this will always be true as false is not allowed in frontend
        val cL2 = cL1.copy(value = Some(v2))
        val result1 = ChangeLiabilityUtils.getChangeLiabilityProfessionalValuation(cL2)
        result1 must be(Some(true))
      }

      "return form-bundle-return professional valuation value, hasValueChanged is not defined" in {
        val cL1 = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(2015, "123456789012")
        val value1 = ChangeLiabilityReturnBuilder.generateLiabilityValueDetails(periodKey = 2016)
        val v2 = value1.copy(hasValueChanged = None)
        val cL2 = cL1.copy(value = Some(v2))
        val result1 = ChangeLiabilityUtils.getChangeLiabilityProfessionalValuation(cL2)
        result1 must be(Some(true))
      }
    }

    "getInitialValueForSubmission" must {
      lazy val formBundleReturn = FormBundleReturn("2015", FormBundlePropertyDetails(titleNumber = Some("1234"),
        address = FormBundleAddress(addressLine1 = "line1", addressLine2 = "line2", addressLine3 = Some("line3"), addressLine4 = Some("line4"), None, countryCode = "GB"),
        additionalDetails = Some("additional details")),
        dateOfValuation = LocalDate.now(), professionalValuation = true, ninetyDayRuleApplies = false, dateOfAcquisition = None, valueAtAcquisition = None,
        localAuthorityCode = Some("1234"), dateOfSubmission = LocalDate.now(), taxAvoidanceScheme = None,
        liabilityAmount = BigDecimal(123.23), paymentReference = "payment-ref-123",
        lineItem =
          Seq(
            FormBundleProperty(20000000, new LocalDate("2016-02-01"), new LocalDate("2016-03-31"), "Liability", None),
            FormBundleProperty(5000000, new LocalDate("2015-04-01"), new LocalDate("2015-10-31"), "Liability", None),
            FormBundleProperty(10000000, new LocalDate("2015-11-01"), new LocalDate("2016-01-31"), "Liability", None)
          )
      )

      "return the earliest value from the line items as the initial value from the form bundle if the user hasn't change the value" in {
        lazy val propertyDetails = PropertyDetails(atedRefNo = "ated-ref-1",
          id = "1",
          periodKey = 2015,
          addressProperty = PropertyDetailsAddress("addr1", "addr2", None, None, None),
          value = Some(PropertyDetailsValue(hasValueChanged = Some(false))),
          formBundleReturn= Some(formBundleReturn)
        )

        val res = ChangeLiabilityUtils.changeLiabilityInitialValueForPeriod(propertyDetails)
        res.isDefined must be (true)
        res must be (Some(BigDecimal(5000000)))
      }

      "return the value from PropertyDetailsValue if the value has changed " in {
        lazy val propertyDetails = PropertyDetails(atedRefNo = "ated-ref-1",
          id = "1",
          periodKey = 2015,
          addressProperty = PropertyDetailsAddress("addr1", "addr2", None, None, None),
          value = Some(
            PropertyDetailsValue(hasValueChanged = Some(true), ownedBeforePolicyYearValue = Some(BigDecimal(123.45)), isOwnedBeforePolicyYear = Some(true))),
          formBundleReturn= Some(formBundleReturn)
        )

        val res = ChangeLiabilityUtils.changeLiabilityInitialValueForPeriod(propertyDetails)
        res.isDefined must be (true)
        res must be (Some(BigDecimal(123.45)))
      }

      "return None if we have no hasChamgeValue" in {
        lazy val propertyDetails = PropertyDetails(atedRefNo = "ated-ref-1",
          id = "1",
          periodKey = 2015,
          addressProperty = PropertyDetailsAddress("addr1", "addr2", None, None, None),
          value = Some(
            PropertyDetailsValue(hasValueChanged =None, ownedBeforePolicyYearValue = Some(BigDecimal(123.45)), isOwnedBeforePolicyYear = Some(true))),
          formBundleReturn= Some(formBundleReturn)
        )

        val res = ChangeLiabilityUtils.changeLiabilityInitialValueForPeriod(propertyDetails)
        res.isDefined must be (false)
      }
    }
  }

}
