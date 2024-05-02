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

package metrics

object MetricsEnum extends Enumeration {

  type MetricsEnum = Value
  val GgAdminAllocateAgent: metrics.MetricsEnum.Value = Value
  val EtmpGetDetails: metrics.MetricsEnum.Value = Value
  val EtmpSubmitPendingClient: metrics.MetricsEnum.Value = Value
  val EtmpSubmitReturns: metrics.MetricsEnum.Value = Value
  val EtmpSubmitEditedLiabilityReturns: metrics.MetricsEnum.Value = Value
  val EtmpGetSummaryReturns: metrics.MetricsEnum.Value = Value
  val EtmpGetSubscriptionData: metrics.MetricsEnum.Value = Value
  val EtmpUpdateSubscriptionData: metrics.MetricsEnum.Value = Value
  val EtmpUpdateRegistrationDetails: metrics.MetricsEnum.Value = Value
  val AtedAgentRequest: metrics.MetricsEnum.Value = Value
  val EtmpGetFormBundleReturns: metrics.MetricsEnum.Value = Value
  val RepositoryInsertRelief: metrics.MetricsEnum.Value = Value
  val RepositoryFetchRelief: metrics.MetricsEnum.Value = Value
  val RepositoryDeleteRelief: metrics.MetricsEnum.Value = Value
  val RepositoryDeleteReliefByYear: metrics.MetricsEnum.Value = Value
  val RepositoryInsertPropDetails: metrics.MetricsEnum.Value = Value
  val RepositoryFetchPropDetails: metrics.MetricsEnum.Value = Value
  val RepositoryDeletePropDetails: metrics.MetricsEnum.Value = Value
  val RepositoryDeletePropDetailsByFieldName: metrics.MetricsEnum.Value = Value
  val RepositoryInsertDispLiability: metrics.MetricsEnum.Value = Value
  val RepositoryFetchDispLiability: metrics.MetricsEnum.Value = Value
  val RepositoryDeleteDispLiability: metrics.MetricsEnum.Value = Value
}
