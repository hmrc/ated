/*
 * Copyright 2020 HM Revenue & Customs
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

package connectors

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HttpClient, _}

import scala.concurrent.Future

class EmailConnectorSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockWSHttp: HttpClient = mock[HttpClient]

  trait Setup {
    class TestEmailConnector extends EmailConnector {
      val http: HttpClient = mockWSHttp
      override val serviceUrl: String = ""
      override val sendEmailUri: String = ""
    }

    val connector = new TestEmailConnector()
  }

  override def beforeEach() {
    reset(mockWSHttp)
  }

  "EmailConnector" must {

    "return a 202 accepted" when {

      "correct emailId Id is passed" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val emailString = "test@mail.com"
        val templateId = "relief_return_submit"
        val params = Map("testParam" -> "testParam")

        when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(202, "")))

        val response = connector.sendTemplatedEmail(emailString, templateId, params)
        await(response) must be(EmailSent)

      }

    }

    "return other status" when {

      "incorrect email Id are passed" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val invalidEmailString = "test@test1.com"
        val templateId = "relief_return_submit"
        val params = Map("testParam" -> "testParam")

        when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(404, "")))

        val response = connector.sendTemplatedEmail(invalidEmailString, templateId, params)
        await(response) must be(EmailNotSent)

      }

    }

  }
}
