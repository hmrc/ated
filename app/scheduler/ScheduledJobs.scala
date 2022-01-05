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

package scheduler

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.quartz.CronExpression
import play.api.{Configuration, Logging}
import scheduler.SchedulingActor.ScheduledMessage

import scala.util.Try

trait ScheduledJobs extends Logging {
  val scheduledMessage: ScheduledMessage[_]
  val config: Configuration
  val actorSystem: ActorSystem
  val jobName: String

  lazy val scheduler: QuartzSchedulerExtension = QuartzSchedulerExtension(actorSystem)

  lazy val schedulingActorRef: ActorRef = actorSystem.actorOf(SchedulingActor.props)

  def enabled: Boolean = Try(config.get[Boolean](s"schedules.$jobName.enabled")).getOrElse(false)

  lazy val description: Option[String] = Try(config.get[String](s"schedules.$jobName.description")).toOption

  lazy val expression: String = Try(config.get[String](s"schedules.$jobName.expression")) map (_.replaceAll("_", " ")) getOrElse ""

  lazy val expressionValid: Boolean = CronExpression.isValidExpression(expression)

  lazy val schedule: Boolean = {
    (enabled, expressionValid) match {
      case (true, true) =>
        scheduler.createSchedule(jobName, description, expression)
        scheduler.schedule(jobName, schedulingActorRef, scheduledMessage)
        logger.info(s"Scheduler for $jobName has been started")
        true
      case (true, false) =>
        logger.info(s"Scheduler for $jobName is disabled as there is no valid quartz expression: $expression")
        false
      case (false, _) =>
        logger.info(s"Scheduler for $jobName is disabled by configuration")
        false
    }
  }
}
