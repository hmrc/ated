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

package controllers

import connectors.EtmpDetailsConnector
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class DetailsControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockEtmpConnector = mock[EtmpDetailsConnector]
  val callingUtr = "ATED-123"
  val ARNMatch = "AARN1234567"
  val ARNNotMatch = "AARN8901234"
  val identifierType = "arn"
  val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")
  val failureResponseJson = Json.parse( """{"reason":"Agent not found!"}""")
  val errorResponseJson = Json.parse( """{"reason":"Some Error."}""")

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

    class TestDetailsController extends BackendController(cc) with DetailsController {
      implicit val ec: ExecutionContext = cc.executionContext
      val etmpConnector: EtmpDetailsConnector = mockEtmpConnector
    }

    val controller = new TestDetailsController()
  }

  override def beforeEach = {
    reset(mockEtmpConnector)
  }

  "DetailsController" must {
    "getDetails" must {
      "respond with OK, for successful GET" in new Setup {
        when(mockEtmpConnector.getDetails(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result = controller.getDetails(callingUtr, ARNMatch, identifierType).apply(FakeRequest())
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in new Setup {
        when(mockEtmpConnector.getDetails(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, failureResponseJson, Map.empty[String, Seq[String]])))
        val result = controller.getDetails(callingUtr, ARNMatch, identifierType).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in new Setup {
        when(mockEtmpConnector.getDetails(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, errorResponseJson, Map.empty[String, Seq[String]])))
        val result = controller.getDetails(callingUtr, ARNMatch, identifierType).apply(FakeRequest())
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in new Setup {
        when(mockEtmpConnector.getDetails(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, errorResponseJson, Map.empty[String, Seq[String]])))
        val result = controller.getDetails(callingUtr, ARNMatch, identifierType).apply(FakeRequest())
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in new Setup {
        when(mockEtmpConnector.getDetails(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, errorResponseJson, Map.empty[String, Seq[String]])))
        val result = controller.getDetails(callingUtr, ARNMatch, identifierType).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }

}
