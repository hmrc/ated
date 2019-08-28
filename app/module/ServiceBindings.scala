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

package module

import connectors._
import metrics.{ServiceMetrics, ServiceMetricsImpl}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import repository._
import services._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}

class ServiceBindings extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector]),
      bind(classOf[EmailConnector]).to(classOf[EmailConnectorImpl]),
      bind(classOf[EtmpDetailsConnector]).to(classOf[EtmpDetailsConnectorImpl]),
      bind(classOf[EtmpReturnsConnector]).to(classOf[EtmpReturnsConnectorImpl]),
      bind(classOf[ServiceMetrics]).to(classOf[ServiceMetricsImpl]),
      bind(classOf[HttpClient]).to(classOf[DefaultHttpClient]),
      bind(classOf[ChangeLiabilityService]).to(classOf[ChangeLiabilityServiceImpl]),
      bind(classOf[DisposeLiabilityReturnService]).to(classOf[DisposeLiabilityReturnServiceImpl]),
      bind(classOf[FormBundleService]).to(classOf[FormBundleServiceImpl]),
      bind(classOf[PropertyDetailsPeriodService]).to(classOf[PropertyDetailsPeriodServiceImpl]),
      bind(classOf[PropertyDetailsService]).to(classOf[PropertyDetailsServiceImpl]),
      bind(classOf[PropertyDetailsValuesService]).to(classOf[PropertyDetailsValuesServiceImpl]),
      bind(classOf[ReliefsService]).to(classOf[ReliefsServiceImpl]),
      bind(classOf[ReturnSummaryService]).to(classOf[ReturnSummaryServiceImpl]),
      bind(classOf[SubscriptionDataService]).to(classOf[SubscriptionDataServiceImpl]),
      bind(classOf[DisposeLiabilityReturnMongoWrapper]).to(classOf[DisposeLiabilityReturnMongoWrapperImpl]),
      bind(classOf[PropertyDetailsMongoWrapper]).to(classOf[PropertyDetailsMongoWrapperImpl]),
      bind(classOf[ReliefsMongoWrapper]).to(classOf[ReliefsMongoWrapperImpl])
    )
}
