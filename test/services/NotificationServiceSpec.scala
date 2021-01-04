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

package services

import connectors.mocks.MockAuthConnector
import connectors.{EmailConnector, EmailNotSent, EmailSent}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class NotificationServiceSpec extends PlaySpec with GuiceOneServerPerSuite with BeforeAndAfterEach with MockAuthConnector {

  val mockEmailConnector = mock[EmailConnector]

  override def beforeEach = {
    reset(mockEmailConnector)
  }

  val name = Some(Name(Some("gary"),Some("bloggs")))
  val noName:Option[Name] = None

  trait Setup {
    class TestNotificationService extends NotificationService with AuthorisedFunctions {
      override val emailConnector = mockEmailConnector
      override val authConnector = mockAuthConnector
    }

    val testNotificationService = new TestNotificationService()
  }

  "sendMail" must {

    implicit val hc = HeaderCarrier()

    "send email when email address present and name is present" in new Setup {
      when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())) thenReturn Future.successful(EmailSent)
      mockAuthorise(retrievals = Retrievals.name)(Future.successful(name))

      val json = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678","emailAddress":"test@mail.com"}}]}""")
      await(testNotificationService.sendMail(json, "")) must be(EmailSent)
      verify(mockEmailConnector, times(1)).sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
    }

    "send email when email address present and name is not present" in new Setup {
      when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())) thenReturn Future.successful(EmailSent)
      mockAuthorise(retrievals = Retrievals.name)(Future.successful(noName))

      val json = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678","emailAddress":"test@mail.com"}}]}""")
      await(testNotificationService.sendMail(json, "")) must be(EmailSent)
    }

    "handle missing email address" in new Setup {
      val json = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678"}}]}""")
      await(testNotificationService.sendMail(json, "")) must be(EmailNotSent)
    }
  }
}
