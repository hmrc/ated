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
import org.joda.time.LocalDate
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}

object EtmpReturnsResponseModelBuilder extends PlaySpec with OneServerPerSuite {


  def generateEtmpGetReturnsResponse(periodKey: String): EtmpGetReturnsResponse = {
    val safeId = "123Safe"
    val org = "OrganisationName"

    val etmpReturn = EtmpReturn("12345", new LocalDate(s"$periodKey-05-05"),
      new LocalDate(s"$periodKey-09-05"), new LocalDate(s"$periodKey-10-05"), 1000, "pay-123", true)

    val etmpPropertySummary = EtmpPropertySummary("abc", None, "line1", "line2", Seq(etmpReturn))

    val etmpLiabilityReturnSummary = EtmpLiabilityReturnSummary(Some(Seq(etmpPropertySummary)))

    val etmpReliefReturnsSummary = EtmpReliefReturnsSummary("12345", new LocalDate(s"$periodKey-05-05"), "Farmhouses",
      new LocalDate(s"$periodKey-09-05"), new LocalDate(s"$periodKey-10-05"), None, None)

    val etmpReturnData = EtmpReturnData(Some(Seq(etmpReliefReturnsSummary)), Some(Seq(etmpLiabilityReturnSummary)))

    val etmpPeriodSummary = EtmpPeriodSummary(periodKey, etmpReturnData)

    EtmpGetReturnsResponse(safeId, org, Seq(etmpPeriodSummary), 10000)
  }

  def generateEtmpGetReturnsResponseNoAtedBal(periodKey: String): EtmpGetReturnsResponse = {
    val safeId = "123Safe"
    val org = "OrganisationName"

    val etmpReturn = EtmpReturn("12345", new LocalDate(s"$periodKey-05-05"),
      new LocalDate(s"$periodKey-09-05"), new LocalDate(s"$periodKey-10-05"), 1000, "pay-123", true)

    val etmpPropertySummary = EtmpPropertySummary("abc", None, "line1", "line2", Seq(etmpReturn))

    val etmpLiabilityReturnSummary = EtmpLiabilityReturnSummary(Some(Seq(etmpPropertySummary)))

    val etmpReliefReturnsSummary = EtmpReliefReturnsSummary("12345", new LocalDate(s"$periodKey-05-05"), "Farmhouses",
      new LocalDate(s"$periodKey-09-05"), new LocalDate(s"$periodKey-10-05"), None, None)

    val etmpReturnData = EtmpReturnData(None, Some(Seq(etmpLiabilityReturnSummary)))

    val etmpPeriodSummary = EtmpPeriodSummary(periodKey, etmpReturnData)

    EtmpGetReturnsResponse(safeId, org, Seq(etmpPeriodSummary), 0)
  }

  def generateEmptyEtmpGetReturnsResponse(periodKey: String): EtmpGetReturnsResponse = {
    val safeId = "123Safe"
    val org = "OrganisationName"

    val etmpReturn = EtmpReturn("12345", new LocalDate(s"$periodKey-05-05"),
      new LocalDate(s"$periodKey-09-05"), new LocalDate(s"$periodKey-10-05"), 1000, "pay-123", true)

    val etmpPropertySummary = EtmpPropertySummary("abc", None, "line1", "line2", Seq(etmpReturn))

    val etmpLiabilityReturnSummary = EtmpLiabilityReturnSummary(propertySummary = None)

    val etmpReliefReturnsSummary = EtmpReliefReturnsSummary("12345", new LocalDate(s"$periodKey-05-05"), "Farmhouses",
      new LocalDate(s"$periodKey-09-05"), new LocalDate(s"$periodKey-10-05"), None, None)

    val etmpReturnData = EtmpReturnData(Some(Seq(etmpReliefReturnsSummary)), Some(Seq(etmpLiabilityReturnSummary)))

    val etmpPeriodSummary = EtmpPeriodSummary(periodKey, etmpReturnData)

    EtmpGetReturnsResponse(safeId, org, Seq(etmpPeriodSummary), 0)
  }

  def generateEtmpGetReturnsResponseNoDraftandLiabilty(periodKey: String): EtmpGetReturnsResponse = {
    val safeId = "123Safe"
    val org = "OrganisationName"

    val etmpReturn = EtmpReturn("12345", new LocalDate(s"$periodKey-05-05"),
      new LocalDate(s"$periodKey-09-05"), new LocalDate(s"$periodKey-10-05"), 1000, "pay-123", true)

    val etmpPropertySummary = EtmpPropertySummary("abc", None, "line1", "line2", Seq(etmpReturn))

    val etmpLiabilityReturnSummary = EtmpLiabilityReturnSummary(Some(Seq(etmpPropertySummary)))

    val etmpReliefReturnsSummary = EtmpReliefReturnsSummary("12345", new LocalDate(s"$periodKey-05-05"), "Farmhouses",
      new LocalDate(s"$periodKey-09-05"), new LocalDate(s"$periodKey-10-05"), None, None)

    val etmpReturnData = EtmpReturnData(None, None)

    val etmpPeriodSummary = EtmpPeriodSummary(periodKey, etmpReturnData)

    EtmpGetReturnsResponse(safeId, org, Seq(etmpPeriodSummary), 0)
  }

  def generateEtmpGetReturnsResponseWithNoPeriodData(periodKey: String): EtmpGetReturnsResponse = {
    val safeId = "123Safe"
    val org = "OrganisationName"

    EtmpGetReturnsResponse(safeId, org, Nil, 0)
  }


}
