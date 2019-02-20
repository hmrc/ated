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

import models.{ClientsAgent, IndividualRelationship, OrganisationRelationship, RelationshipDetails}
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId

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
        AtedUtils.periodStartDate(2015) must be(new LocalDate("2015-04-01"))
        AtedUtils.periodStartDate(2014) must be(new LocalDate("2014-04-01"))
      }
    }

    "periodEndDate" must {
      "return end date of period based on period key" in {
        AtedUtils.periodEndDate(2015) must be(new LocalDate("2016-03-31"))
        AtedUtils.periodEndDate(2014) must be(new LocalDate("2015-03-31"))
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

}
