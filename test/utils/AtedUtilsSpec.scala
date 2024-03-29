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

package utils

import models.{ClientsAgent, IndividualRelationship, OrganisationRelationship, RelationshipDetails}
import java.time.LocalDate
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}

class AtedUtilsSpec extends PlaySpec {

  "AtedUtils" must {
    "getSessionIdOrAgentCodeAsId" must {
      "return session id string, if in header carrier" in {
        val hc = HeaderCarrier(sessionId = Some(SessionId("session-id")))
        val agentCode = "JARN1234567"
        AtedUtils.getSessionIdOrAgentCodeAsId(hc, agentCode) must be("session-id")
      }
      "return agentCode, if session id not found in request" in {
        val hc = HeaderCarrier()
        val agentCode = "JARN1234567"
        AtedUtils.getSessionIdOrAgentCodeAsId(hc, agentCode) must be("JARN1234567")
      }
    }

    "periodStartDate" must {
      "return start date of period based on period key" in {
        AtedUtils.periodStartDate(2015) must be(LocalDate.of(2015, 4, 1))
        AtedUtils.periodStartDate(2014) must be(LocalDate.of(2014, 4, 1))
      }
    }

    "periodEndDate" must {
      "return end date of period based on period key" in {
        AtedUtils.periodEndDate(2015) must be(LocalDate.of(2016, 3, 31))
        AtedUtils.periodEndDate(2014) must be(LocalDate.of(2015, 3, 31))
      }
    }

    "createDraftId" must {
      "create an id for submission with 10 alphanumeric characters" in {
        val id = AtedUtils.createPropertyKey
        id.length must be(10)
        id.matches("[A-Z0-9]{10}") must be(true)
      }
    }

    "getClientsAgentFromEtmpRelationshipData" must {
      "if individual is in relationship-detail, concatenate first name and last name with a space in between" in {
        val testRd1 = RelationshipDetails(
          atedReferenceNumber = "ated-ref-123",
          agentReferenceNumber = "arn-123",
          individual = Some(IndividualRelationship("first-name", "last-name")),
          organisation = None,
          dateFrom = LocalDate.now(),
          dateTo = LocalDate.now(),
          contractAccountCategory = "01"
        )
        AtedUtils.getClientsAgentFromEtmpRelationshipData(testRd1) must be(ClientsAgent(testRd1.agentReferenceNumber, testRd1.atedReferenceNumber, "first-name last-name", agentRejected = false, isEtmpData = true))
      }

      "if organisation is in relationship-detail, concatenate first name and last name with a space in between" in {
        val testRd1 = RelationshipDetails(
          atedReferenceNumber = "ated-ref-123",
          agentReferenceNumber = "arn-123",
          individual = None,
          organisation = Some(OrganisationRelationship("org-name")),
          dateFrom = LocalDate.now(),
          dateTo = LocalDate.now(),
          contractAccountCategory = "01"
        )
        AtedUtils.getClientsAgentFromEtmpRelationshipData(testRd1) must be(ClientsAgent(testRd1.agentReferenceNumber, testRd1.atedReferenceNumber, "org-name", agentRejected = false, isEtmpData = true))
      }

      "if neither(individual/organisation) is in relationship-detail, just return an empty string as name - this won't happen in reality unless ETMP return such data" in {
        val testRd1 = RelationshipDetails(
          atedReferenceNumber = "ated-ref-123",
          agentReferenceNumber = "arn-123",
          individual = None,
          organisation = None,
          dateFrom = LocalDate.now(),
          dateTo = LocalDate.now(),
          contractAccountCategory = "01"
        )
        AtedUtils.getClientsAgentFromEtmpRelationshipData(testRd1) must be(ClientsAgent(testRd1.agentReferenceNumber, testRd1.atedReferenceNumber, "", agentRejected = false, isEtmpData = true))
      }
    }
  }

  "calculateLowerTaxYearBounday" should {
    def year(i: Int) = LocalDate.parse(s"$i-04-01")

    "return 2012" when {
      "year is before 2012" in {
        AtedUtils.calculateLowerTaxYearBounday(2011) mustBe year(2012)
      }
      "year is 2012" in {
        AtedUtils.calculateLowerTaxYearBounday(2012) mustBe year(2012)
      }
      "year is before 2017" in {
        AtedUtils.calculateLowerTaxYearBounday(2017) mustBe year(2012)
      }
    }

    "return 2017" when {
      "year is 2017" in {
        AtedUtils.calculateLowerTaxYearBounday(2018) mustBe year(2017)
      }
      "year is before 2022" in {
        AtedUtils.calculateLowerTaxYearBounday(2021) mustBe year(2017)
      }
    }

    "return 2022" when {
      "year is 2022" in {
        AtedUtils.calculateLowerTaxYearBounday(2023) mustBe year(2022)
      }
      "year is before 2027" in {
        AtedUtils.calculateLowerTaxYearBounday(2026) mustBe year(2022)
      }
    }
  }

  "enforceFiveYearBoundary" should {
    def date(s: String) = LocalDate.parse(s)

    "return 2012-4-1 as valuation year" in {
      Seq(
        date("2012-04-01") -> 2012,
        date("2011-03-02") -> 2012,
        date("2011-03-02") -> 2017
      ) foreach { case (valued, period) =>
        AtedUtils.enforceFiveYearBoundary(valued, period) mustBe date("2012-04-01")
      }
    }

    "return 2017-4-1 as valuation year" in {
      Seq(
        date("2016-03-31") -> 2018,
        date("2016-03-31") -> 2022,
        date("2017-04-01") -> 2017
      ) foreach { case (valued, period) =>
        AtedUtils.enforceFiveYearBoundary(valued, period) mustBe date("2017-04-01")
      }
    }

    "return 2022-4-1 as valuation year" in {
      Seq(
        date("2021-03-31") -> 2023,
        date("2021-03-31") -> 2027,
        date("2022-04-01") -> 2022
      ) foreach { case (valued, period) =>
        AtedUtils.enforceFiveYearBoundary(valued, period) mustBe date("2022-04-01")
      }
    }

    "return the provided date (if within tax boundary) as valuation year" in {
      Seq(
        date("2014-07-10") -> 2015,
        date("2019-01-13") -> 2019,
        date("2023-01-01") -> 2017
      ) foreach { case (valued, period) =>
        AtedUtils.enforceFiveYearBoundary(valued, period) mustBe valued
      }
    }
  }

  "extractName" must {
    val nameMissingFirstName = Some(Name(None,Some("bloggs")))
    val nameNone = None
    val name = Some(Name(Some("gary"),Some("bloggs")))
    "return the first name when present" in {
      AtedUtils.extractName(name) must be("gary")
    }

    "return a string saying there is no first name" in  {
      AtedUtils.extractName(nameMissingFirstName) must be("No first name present")
    }

    "return a string saying there is no first name when None is returned from auth" in {
      AtedUtils.extractName(nameNone) must be("No first name present")
    }
  }

  "extractLastName" must {
    val nameMissingLastName = Some(Name(Some("gary"),None))
    val nameNone = None
    val name = Some(Name(Some("gary"),Some("bloggs")))
    "return the second name when present" in  {
      AtedUtils.extractLastName(name) must be("bloggs")
    }

    "return a string saying there is no last name" in {
      AtedUtils.extractLastName(nameMissingLastName) must be("No last name present")
    }

    "return a string saying there is no second name when None is returned from auth" in {
      AtedUtils.extractLastName(nameNone) must be("No last name present")
    }
  }
}
