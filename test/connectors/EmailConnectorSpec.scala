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

package connectors

import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}

class EmailConnectorSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockWSHttp: HttpClientV2 = mock[HttpClientV2]

  trait Setup extends ConnectorTest{
    class TestEmailConnector extends EmailConnector {
      implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
      val http: HttpClientV2 = mockWSHttp
      override val serviceUrl: String = ""
      override val sendEmailUri: String = ""

    }

    val connector = new TestEmailConnector()
  }

  override def beforeEach(): Unit = {
    reset(mockWSHttp)
  }

  "EmailConnector" must {

    "return a 202 accepted" when {

      "correct emailId Id is passed" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()

        val emailString = "test@mail.com"
        val templateId = "relief_return_submit"
        val params = Map("testParam" -> "testParam")

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(202, "")))

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

        when(requestBuilderExecute[HttpResponse]).thenReturn(Future.successful(HttpResponse(404, "")))

        val response = connector.sendTemplatedEmail(invalidEmailString, templateId, params)
        await(response) must be(EmailNotSent)
      }
    }
  }
}
