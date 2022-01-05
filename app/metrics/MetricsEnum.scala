/*
 * Copyright 2022 HM Revenue & Customs
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

package metrics

object MetricsEnum extends Enumeration {

  type MetricsEnum = Value
  val GgAdminAllocateAgent = Value
  val EtmpGetDetails = Value
  val EtmpSubmitPendingClient = Value
  val EtmpSubmitReturns = Value
  val EtmpSubmitEditedLiabilityReturns = Value
  val EtmpGetSummaryReturns = Value
  val EtmpGetSubscriptionData = Value
  val EtmpUpdateSubscriptionData = Value
  val EtmpUpdateRegistrationDetails = Value
  val AtedAgentRequest = Value
  val EtmpGetFormBundleReturns = Value
  val EtmpGetAgentClientRelationshipDetails = Value
  val RepositoryInsertRelief = Value
  val RepositoryFetchRelief = Value
  val RepositoryDeleteRelief = Value
  val RepositoryDeleteReliefByYear = Value
  val RepositoryInsertPropDetails = Value
  val RepositoryFetchPropDetails  = Value
  val RepositoryDeletePropDetails  = Value
  val RepositoryDeletePropDetailsByFieldName = Value
  val RepositoryInsertDispLiability = Value
  val RepositoryFetchDispLiability = Value
  val RepositoryDeleteDispLiability = Value
}
