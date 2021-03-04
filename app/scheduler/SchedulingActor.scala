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

package scheduler

import akka.actor.{Actor, ActorLogging, Props}
import play.api.Logging
import scheduler.SchedulingActor._

class SchedulingActor extends Actor with ActorLogging with Logging {
  import context.dispatcher

  override def receive: Receive = {
    case message : ScheduledMessage[_] =>
      logger.info(s"Received ${message.getClass.getSimpleName}")
      message.service.invoke()
  }
}

object SchedulingActor {
  sealed trait ScheduledMessage[A] {
    val service: ScheduledService[Int]
  }

  case class deletePropertyDetailsDrafts(service: DeletePropertyDetailsService) extends ScheduledMessage[Int]
	case class deleteReliefDrafts(service: DeleteReliefsService) extends ScheduledMessage[Int]
	case class deleteLiabilityReturns(service: DeleteLiabilityReturnsService) extends ScheduledMessage[Int]

	def props: Props = Props[SchedulingActor]
}
