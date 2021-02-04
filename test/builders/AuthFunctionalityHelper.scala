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

package builders

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}

import scala.concurrent.Future

trait AuthFunctionalityHelper {
  self: MockitoSugar =>

  val mockAuthConnector: AuthConnector
  val enrolment = Enrolment("HMRC-AGENT-AGENT", Seq(EnrolmentIdentifier("test", "test")), "Activated")

  def mockRetrievingAuthRef(): Unit = {
    when(mockAuthConnector.authorise[Enrolments](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(Enrolments(Set(enrolment))))
  }

  def mockRetrievingNoAuthRef(): Unit = {
    when(mockAuthConnector.authorise[Enrolments](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(Future.successful(Enrolments(Set())))
  }
}
