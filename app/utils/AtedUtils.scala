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

import models.{ClientsAgent, RelationshipDetails}
import org.joda.time.LocalDate
import utils.AtedConstants._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId

object AtedUtils {

  def getSessionIdOrAgentCodeAsId(hc: HeaderCarrier, agentCode: String): String = {
    hc.sessionId.fold(SessionId(agentCode).value)(_.value)
  }

  def createPropertyKey: String = {
    java.util.UUID.randomUUID.toString.takeRight(10).toUpperCase()
  }

  def periodStartDate(periodKey: Int): LocalDate = new LocalDate(s"$periodKey-$PeriodStartMonth-$PeriodStartDay")

  def periodEndDate(periodKey: Int): LocalDate = periodStartDate(periodKey).plusYears(1).minusDays(1)

  def getClientsAgentFromEtmpRelationshipData(data: RelationshipDetails): ClientsAgent = {
    def getName = data.individual.fold(data.organisation.fold("")(a => a.organisationName))(a => a.firstName + " " + a.lastName)
    val clientsAgent = ClientsAgent(data.agentReferenceNumber, data.atedReferenceNumber, getName, agentRejected = false, isEtmpData = true)
    clientsAgent
  }

}
