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

import connectors.EtmpReturnsConnector
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._

import scala.concurrent.Future
import uk.gov.hmrc.http.HttpResponse

class FormBundleServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockEtmpConnector = mock[EtmpReturnsConnector]
  val atedRefNo = "ATED-123"
  val formBundle = "form-bundle-01"
  val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")

  override def beforeEach = {
    reset(mockEtmpConnector)
  }

  object TestFormBundleService extends FormBundleService {
    override val etmpReturnsConnector = mockEtmpConnector
  }

  "FormBundleService" must {
    "use the correct Etmpconnector" in {
      FormBundleService.etmpReturnsConnector must be(EtmpReturnsConnector)
    }


    "getFormBundleReturns" must {
      "return response from connector" in {
        when(mockEtmpConnector.getFormBundleReturns(Matchers.eq(atedRefNo), Matchers.eq(formBundle))).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successResponseJson))))
        val response = TestFormBundleService.getFormBundleReturns(atedRefNo, formBundle)
        await(response).status must be(OK)
      }
    }
  }

}
