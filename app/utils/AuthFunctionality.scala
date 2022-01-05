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

package utils

import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthorisedFunctions, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait AuthFunctionality extends AuthorisedFunctions {

  private val enrolmentKey = "HMRC-AGENT-AGENT"

  def retrieveAgentRefNumberFor[A](body: Option[String] => Future[A])
                                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] = {
    authorised().retrieve(Retrievals.allEnrolments) {
      case enrolments @ Enrolments(_) =>
        val enrolment: Option[EnrolmentIdentifier] = enrolments
          .getEnrolment(enrolmentKey)
          .flatMap(_.getIdentifier("AgentRefNumber"))

        body(enrolment.map(_.value))
      }
    }

}
