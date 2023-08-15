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

import connectors.EtmpReturnsConnector
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class FormBundleServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockEtmpConnector = mock[EtmpReturnsConnector]
  val atedRefNo = "ATED-123"
  val formBundle = "form-bundle-01"
  val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")

  override def beforeEach() = {
    reset(mockEtmpConnector)
  }

  trait Setup {
    class TestFormBundleService extends FormBundleService {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      override val etmpReturnsConnector = mockEtmpConnector
    }
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    val testFormBundleService = new TestFormBundleService()
  }

  "FormBundleService" must {
    "getFormBundleReturns" must {
      "return response from connector" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        when(mockEtmpConnector.getFormBundleReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val response = testFormBundleService.getFormBundleReturns(atedRefNo, formBundle)
        await(response).status must be(OK)
      }
    }
  }

}
