/*
 * Copyright 2021 HM Revenue & Customs
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
import scheduler._
import services._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import play.api.inject.{bind => playBind}
import uk.gov.hmrc.http.HttpClient

class ServiceBindings extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      playBind(classOf[DeletePropertyDetailsService]).to[DefaultDeletePropertyDetailsService].eagerly(),
      playBind(classOf[DeleteReliefsService]).to[DefaultDeleteReliefsService].eagerly(),
      playBind(classOf[DeleteLiabilityReturnsService]).to[DefaultDeleteLiabilityReturnsService].eagerly(),
      playBind(classOf[LockRepositoryProvider]).to[DefaultLockRepositoryProvider].eagerly(),
      playBind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector]),
      playBind(classOf[EmailConnector]).to(classOf[EmailConnectorImpl]),
      playBind(classOf[EtmpDetailsConnector]).to(classOf[EtmpDetailsConnectorImpl]),
      playBind(classOf[EtmpReturnsConnector]).to(classOf[EtmpReturnsConnectorImpl]),
      playBind(classOf[ServiceMetrics]).to(classOf[ServiceMetricsImpl]),
      playBind(classOf[HttpClient]).to(classOf[DefaultHttpClient]),
      playBind(classOf[ChangeLiabilityService]).to(classOf[ChangeLiabilityServiceImpl]),
      playBind(classOf[DisposeLiabilityReturnService]).to(classOf[DisposeLiabilityReturnServiceImpl]),
      playBind(classOf[FormBundleService]).to(classOf[FormBundleServiceImpl]),
      playBind(classOf[PropertyDetailsPeriodService]).to(classOf[PropertyDetailsPeriodServiceImpl]),
      playBind(classOf[PropertyDetailsService]).to(classOf[PropertyDetailsServiceImpl]),
      playBind(classOf[PropertyDetailsValuesService]).to(classOf[PropertyDetailsValuesServiceImpl]),
      playBind(classOf[ReliefsService]).to(classOf[ReliefsServiceImpl]),
      playBind(classOf[ReturnSummaryService]).to(classOf[ReturnSummaryServiceImpl]),
      playBind(classOf[SubscriptionDataService]).to(classOf[SubscriptionDataServiceImpl]),
      playBind(classOf[DisposeLiabilityReturnMongoWrapper]).to(classOf[DisposeLiabilityReturnMongoWrapperImpl]),
      playBind(classOf[PropertyDetailsMongoWrapper]).to(classOf[PropertyDetailsMongoWrapperImpl]),
      playBind(classOf[ReliefsMongoWrapper]).to(classOf[ReliefsMongoWrapperImpl]),
      playBind(classOf[DeleteOldReliefsJob]).toSelf.eagerly(),
      playBind(classOf[DeleteOldLiabilityReturnsJob]).toSelf.eagerly(),
      playBind(classOf[DeleteOldPropertyDetailsJob]).toSelf.eagerly()
    )
}
