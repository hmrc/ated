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

package metrics

import com.codahale.metrics.Timer
import com.codahale.metrics.Timer.Context
import com.kenshoo.play.metrics.MetricsRegistry
import metrics.MetricsEnum.{MetricsEnum, Value}
import uk.gov.hmrc.play.graphite.MicroserviceMetrics

trait Metrics {

  def startTimer(api: MetricsEnum): Timer.Context

  def incrementSuccessCounter(api: MetricsEnum): Unit

  def incrementFailedCounter(api: MetricsEnum): Unit

}

object Metrics extends Metrics with MicroserviceMetrics {
  val registry = metrics.defaultRegistry
  val timers = Map(
    MetricsEnum.GgAdminAllocateAgent -> registry.timer("gga-allocate-agent-response-timer"),
    MetricsEnum.EtmpSubmitReturns -> registry.timer("etmp-submit-returns-response-timer"),
    MetricsEnum.EtmpSubmitEditedLiabilityReturns -> registry.timer("etmp-submit-edited-liability-returns-response-timer"),
    MetricsEnum.EtmpSubmitPendingClient -> registry.timer("etmp-submit-client-response-timer"),
    MetricsEnum.EtmpGetSummaryReturns -> registry.timer("etmp-get-summary-returns-response-timer"),
    MetricsEnum.EtmpGetSubscriptionData -> registry.timer("etmp-get-subscription-data-response-timer"),
    MetricsEnum.EtmpUpdateSubscriptionData -> registry.timer("etmp-update-subscription-data-response-timer"),
    MetricsEnum.EtmpUpdateRegistrationDetails -> registry.timer("etmp-update-registration-details-response-timer"),
    MetricsEnum.EtmpGetDetails -> registry.timer("etmp-get-details-response-timer"),
    MetricsEnum.EtmpGetFormBundleReturns -> registry.timer("etmp-get-form-bundle-returns-response-timer"),
    MetricsEnum.RepositoryInsertRelief -> registry.timer("repository-insert-relief-timer"),
    MetricsEnum.RepositoryFetchRelief -> registry.timer("repository-fetch-relief-timer"),
    MetricsEnum.RepositoryDeleteRelief -> registry.timer("repository-delete-relief-timer"),
    MetricsEnum.RepositoryDeleteReliefByYear -> registry.timer("repository-delete-relief-year-timer"),
    MetricsEnum.RepositoryInsertPropDetails -> registry.timer("repository-insert-property-details-timer"),
    MetricsEnum.RepositoryFetchPropDetails -> registry.timer("repository-fetch-property-details-timer"),
    MetricsEnum.RepositoryDeletePropDetails -> registry.timer("repository-delete-property-details-timer"),
    MetricsEnum.RepositoryDeletePropDetailsByFieldName -> registry.timer("repository-delete-property-details-field-name-timer"),
    MetricsEnum.RepositoryInsertDispLiability -> registry.timer("repository-insert-disp-liability-timer"),
    MetricsEnum.RepositoryFetchDispLiability -> registry.timer("repository-fetch-disp-liability-timer"),
    MetricsEnum.RepositoryDeleteDispLiability -> registry.timer("repository-delete-disp-liability-timer")
  )

  val successCounters = Map(
    MetricsEnum.GgAdminAllocateAgent -> registry.counter("gga-allocate-agent-success-counter"),
    MetricsEnum.EtmpSubmitReturns -> registry.counter("etmp-submit-returns-success-counter"),
    MetricsEnum.EtmpSubmitEditedLiabilityReturns -> registry.counter("etmp-submit-edited-liability-returns-success-counter"),
    MetricsEnum.EtmpSubmitPendingClient -> registry.counter("etmp-submit-client-success-counter"),
    MetricsEnum.EtmpGetSummaryReturns -> registry.counter("etmp-get-summary-returns-success-counter"),
    MetricsEnum.EtmpGetSubscriptionData -> registry.counter("etmp-get-subscription-data-success-counter"),
    MetricsEnum.EtmpUpdateSubscriptionData -> registry.counter("etmp-update-subscription-data-success-counter"),
    MetricsEnum.EtmpUpdateRegistrationDetails -> registry.counter("etmp-update-registration-details-success-counter"),
    MetricsEnum.EtmpGetDetails -> registry.counter("etmp-get-details-success-counter"),
    MetricsEnum.AtedAgentRequest -> registry.counter("ated-agent-request-success-counter"),
    MetricsEnum.EtmpGetFormBundleReturns -> registry.counter("etmp-get-form-bundle-returns-success-counter")
  )

  val failedCounters = Map(
    MetricsEnum.GgAdminAllocateAgent -> registry.counter("gga-allocate-agent-failed-counter"),
    MetricsEnum.EtmpSubmitReturns -> registry.counter("etmp-submit-returns-failed-counter"),
    MetricsEnum.EtmpSubmitEditedLiabilityReturns -> registry.counter("etmp-submit-edited-liability-returns-failed-counter"),
    MetricsEnum.EtmpSubmitPendingClient -> registry.counter("etmp-submit-client-failed-counter"),
    MetricsEnum.EtmpGetSummaryReturns -> registry.counter("etmp-get-summary-returns-failed-counter"),
    MetricsEnum.EtmpGetSubscriptionData -> registry.counter("etmp-get-subscription-data-failed-counter"),
    MetricsEnum.EtmpUpdateSubscriptionData -> registry.counter("etmp-update-subscription-data-failed-counter"),
    MetricsEnum.EtmpUpdateRegistrationDetails -> registry.counter("etmp-update-registration-details-failed-counter"),
    MetricsEnum.EtmpGetDetails -> registry.counter("etmp-get-details-failed-counter"),
    MetricsEnum.EtmpGetFormBundleReturns -> registry.counter("etmp-get-form-bundle-returns-failed-counter")
  )

  override def startTimer(api: MetricsEnum): Context = timers(api).time()

  override def incrementSuccessCounter(api: MetricsEnum): Unit = successCounters(api).inc()

  override def incrementFailedCounter(api: MetricsEnum): Unit = failedCounters(api).inc()

}
