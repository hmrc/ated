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

package services

import connectors.mocks.MockAuthConnector
import connectors.{EmailConnector, EmailNotSent, EmailSent}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class NotificationServiceSpec extends PlaySpec with GuiceOneServerPerSuite with BeforeAndAfterEach with MockAuthConnector {

  val mockEmailConnector: EmailConnector = mock[EmailConnector]

  override def beforeEach(): Unit = {
    reset(mockEmailConnector)
  }

  trait Setup {
    class TestNotificationService extends NotificationService {
      implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
      override val emailConnector: EmailConnector = mockEmailConnector
    }

    val testNotificationService = new TestNotificationService()
  }

  "sendMail method" must {

    implicit val hc: HeaderCarrier = HeaderCarrier()

    "send email when all data is present" in new Setup {

      when(mockEmailConnector.sendTemplatedEmail(
        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
      ) thenReturn Future.successful(EmailSent)
      val subscriptionJson: JsValue = Json
        .parse(
          """{
            |"safeId":"1111111111",
            |"organisationName":"Test Org",
            |"address":[{"name1":"name1","name2":"name2",
            |"addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1",
            |"addressLine2":"address line 2",
            |"addressLine3":"address line 3",
            |"addressLine4":"address line 4",
            |"postalCode":"ZZ1 1ZZ","countryCode":"GB"},
            |"contactDetails":{"phoneNumber":"01234567890",
            |"mobileNumber":"0712345678",
            |"emailAddress":"test@mail.com"}}]}"""
            .stripMargin)

      val referencesMapCaptor = ArgumentCaptor.forClass(classOf[Map[String, String]])
      await(testNotificationService.sendMail(subscriptionJson, "any_template")) must be(EmailSent)
      verify(mockEmailConnector, times(1)).sendTemplatedEmail(
        ArgumentMatchers.any(),
        ArgumentMatchers.any(),
        referencesMapCaptor.capture)(ArgumentMatchers.any()
      )

      referencesMapCaptor.getValue.get("first_name") mustBe Some("")
      referencesMapCaptor.getValue.get("last_name") mustBe Some("customer")
    }

    "handle missing email address by not sending an email" in new Setup {

      val subscriptionJson: JsValue = Json.parse(
        """{
          |"safeId":"1111111111",
          |"organisationName":"Test Org",
          |"address":[{"name1":"name1","name2":"name2",
          |"addressDetails":{"addressType":"Correspondence",
          |"addressLine1":"address line 1",
          |"addressLine2":"address line 2",
          |"addressLine3":"address line 3",
          |"addressLine4":"address line 4",
          |"postalCode":"ZZ1 1ZZ","countryCode":"GB"},
          |"contactDetails":{"phoneNumber":"01234567890",
          |"mobileNumber":"0712345678"}}]}"""
          .stripMargin)

      await(testNotificationService.sendMail(subscriptionJson, "")) must be(EmailNotSent)
    }
  }
}
