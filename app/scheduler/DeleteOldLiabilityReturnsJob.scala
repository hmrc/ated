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

package scheduler

import org.apache.pekko.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import scheduler.SchedulingActor.deleteLiabilityReturns

@Singleton
class DeleteOldLiabilityReturnsJob @Inject()(val config: Configuration,
																						 documentUpdateService: DeleteLiabilityReturnsService
																						) extends ScheduledJobs {
	val jobName = "delete-liability-returns-job"
	val actorSystem = ActorSystem(jobName)
	val scheduledMessage = deleteLiabilityReturns(documentUpdateService)
	schedule
}
