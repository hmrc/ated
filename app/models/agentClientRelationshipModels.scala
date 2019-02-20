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

import org.joda.time.LocalDate
import play.api.libs.json.Json

case class IndividualRelationship(firstName: String, lastName: String)

object IndividualRelationship {
  implicit val formats = Json.format[IndividualRelationship]
}

case class OrganisationRelationship(organisationName: String)

object OrganisationRelationship {
  implicit val formats = Json.format[OrganisationRelationship]
}

case class RelationshipDetails(atedReferenceNumber: String,
                               agentReferenceNumber: String,
                               individual: Option[IndividualRelationship] = None,
                               organisation: Option[OrganisationRelationship] = None,
                               dateFrom: LocalDate, // YYYY-MM-DD
                               dateTo: LocalDate, // YYYY-MM-DD
                               contractAccountCategory: String)

object RelationshipDetails {
  implicit val formats = Json.format[RelationshipDetails]
}

case class AgentClientRelationshipResponseModel(relationship: Seq[RelationshipDetails])

object AgentClientRelationshipResponseModel {
  implicit val formats = Json.format[AgentClientRelationshipResponseModel]
}

case class AgentClientRelationshipRequestModel(agent: Boolean,
                                               activeOnly: Boolean,
                                               arn: Option[String] = None,
                                               atedRefNo: Option[String] = None,
                                               to:Option[String] = None,
                                               from: Option[String] = None)

object AgentClientRelationshipRequestModel {
  implicit val formats = Json.format[AgentClientRelationshipRequestModel]
}
