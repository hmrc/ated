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

import java.time.LocalDate
import play.api.libs.json.{Json, OFormat}
import play.api.libs.json.Writes._
import play.api.libs.json.Reads._

case class IndividualRelationship(firstName: String, lastName: String)

object IndividualRelationship {
  implicit val formats: OFormat[IndividualRelationship] = Json.format[IndividualRelationship]
}

case class OrganisationRelationship(organisationName: String)

object OrganisationRelationship {
  implicit val formats: OFormat[OrganisationRelationship] = Json.format[OrganisationRelationship]
}

case class RelationshipDetails(atedReferenceNumber: String,
                               agentReferenceNumber: String,
                               individual: Option[IndividualRelationship] = None,
                               organisation: Option[OrganisationRelationship] = None,
                               dateFrom: LocalDate, // YYYY-MM-DD
                               dateTo: LocalDate, // YYYY-MM-DD
                               contractAccountCategory: String)

object RelationshipDetails {
  implicit val formats: OFormat[RelationshipDetails] = Json.format[RelationshipDetails]
}

case class AgentClientRelationshipResponseModel(relationship: Seq[RelationshipDetails])

object AgentClientRelationshipResponseModel {
  implicit val formats: OFormat[AgentClientRelationshipResponseModel] = Json.format[AgentClientRelationshipResponseModel]
}

case class AgentClientRelationshipRequestModel(agent: Boolean,
                                               activeOnly: Boolean,
                                               arn: Option[String] = None,
                                               atedRefNo: Option[String] = None,
                                               to:Option[String] = None,
                                               from: Option[String] = None)

object AgentClientRelationshipRequestModel {
  implicit val formats: OFormat[AgentClientRelationshipRequestModel] = Json.format[AgentClientRelationshipRequestModel]
}
