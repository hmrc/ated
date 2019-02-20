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

package services

import connectors.{EmailConnector, EmailNotSent, EmailSent}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class NotificationServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockEmailConnector = mock[EmailConnector]

  override def beforeEach = {
    reset(mockEmailConnector)
  }

  object TestNotificationService extends NotificationService {
    override val emailConnector = mockEmailConnector
  }

  "sendMail" must {

    implicit val hc = HeaderCarrier()

    "send email when email address present" in {
      when(mockEmailConnector.sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)
      val json = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678","emailAddress":"test@mail.com"}}]}""")
      await(TestNotificationService.sendMail(json, "")) must be(EmailSent)
    }

    "handle missing email address" in {
      val json = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678"}}]}""")
      await(TestNotificationService.sendMail(json, "")) must be(EmailNotSent)
    }
  }
}
