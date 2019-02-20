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

package connectors

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import uk.gov.hmrc.http._

import scala.concurrent.Future

class EmailConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  trait MockedVerbs extends CoreGet with CorePost with CorePut
  val mockWSHttp: CoreGet with CorePost with CorePut = mock[MockedVerbs]

  object TestEmailConnector extends EmailConnector {
    val http: CoreGet with CorePost with CorePut = mockWSHttp
  }

  override def beforeEach() {
    reset(mockWSHttp)
  }

  "EmailConnector" must {

    "have a service url" in {
      EmailConnector.serviceUrl == "email"
    }

    "return a 202 accepted" when {

      "correct emailId Id is passed" in {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val emailString = "test@mail.com"
        val templateId = "relief_return_submit"
        val params = Map("testParam" -> "testParam")

        when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(),
          Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(202, responseJson = None)))

        val response = TestEmailConnector.sendTemplatedEmail(emailString, templateId, params)
        await(response) must be(EmailSent)

      }

    }

    "return other status" when {

      "incorrect email Id are passed" in {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val invalidEmailString = "test@test1.com"
        val templateId = "relief_return_submit"
        val params = Map("testParam" -> "testParam")

        when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(),
          Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(404, responseJson = None)))

        val response = TestEmailConnector.sendTemplatedEmail(invalidEmailString, templateId, params)
        await(response) must be(EmailNotSent)

      }

    }

  }
}
