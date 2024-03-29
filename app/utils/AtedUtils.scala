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

import models.{ClientsAgent, RelationshipDetails}
import java.time.LocalDate
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import utils.AtedConstants._

object AtedUtils {

  val lowestBound = 2012

  def getSessionIdOrAgentCodeAsId(hc: HeaderCarrier, agentCode: String): String = {
    hc.sessionId.fold(SessionId(agentCode).value)(_.value)
  }

  def createPropertyKey: String = {
    java.util.UUID.randomUUID.toString.takeRight(10).toUpperCase()
  }

  def periodStartDate(periodKey: Int): LocalDate = LocalDate.of(periodKey, PeriodStartMonth.toInt, PeriodStartDay.toInt)

  def periodEndDate(periodKey: Int): LocalDate = periodStartDate(periodKey).plusYears(1).minusDays(1)

  def getClientsAgentFromEtmpRelationshipData(data: RelationshipDetails): ClientsAgent = {
    def getName = data.individual.fold(data.organisation.fold("")(a => a.organisationName))(a => a.firstName + " " + a.lastName)
    val clientsAgent = ClientsAgent(data.agentReferenceNumber, data.atedReferenceNumber, getName, isEtmpData = true)
    clientsAgent
  }

  def enforceFiveYearBoundary(valuation: LocalDate, taxYear: Int): LocalDate = {
    val lowerBoundary = calculateLowerTaxYearBounday(taxYear)
    if (!(valuation isAfter lowerBoundary)) lowerBoundary else valuation
  }

  private[utils] def calculateLowerTaxYearBounday(taxYear: Int): LocalDate = {
    val year = if (taxYear <= lowestBound) lowestBound else {
      lowestBound + (5 * ((taxYear - lowestBound - 1) / 5.0).floor.toInt)
    }

    LocalDate.of(year, 4, 1)
  }

  def extractName(authName: Option[Name]): String = authName.flatMap(_.name).getOrElse("No first name present")

  def extractLastName(authName: Option[Name]): String = authName.flatMap(_.lastName).getOrElse("No last name present")

}
