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

package controllers

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.FormBundleService
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class FormBundleReturnsControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockFormBundleService: FormBundleService = mock[FormBundleService]
  val callingUtr = "ATED-123"
  val testFormBundleNum = "123456789012"
  val successResponseJson: JsValue = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")
  val failureResponseJson: JsValue = Json.parse( """{"reason":"Agent not found!"}""")
  val errorResponseJson: JsValue = Json.parse( """{"reason":"Some Error."}""")

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

    class TestFormBundleReturnsController extends BackendController(cc) with FormBundleReturnsController {
      implicit val ec: ExecutionContext = cc.executionContext
      val formBundleService: FormBundleService = mockFormBundleService
    }

    val controller = new TestFormBundleReturnsController()
  }


  "FormBundleReturnsController" must {
    "getFormBundleReturns" must {
      "respond with OK, for successful GET" in new Setup {
        when(mockFormBundleService.getFormBundleReturns(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = controller.getFormBundleReturns(callingUtr, testFormBundleNum).apply(FakeRequest())
        status(result) must be(OK)
      }
      "respond with NOT_FOUND, for unsuccessful GET" in new Setup {
        when(mockFormBundleService.getFormBundleReturns(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, failureResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = controller.getFormBundleReturns(callingUtr, testFormBundleNum).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
      "respond with BAD_REQUEST, if ETMP sends BadRequest status" in new Setup {
        when(mockFormBundleService.getFormBundleReturns(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = controller.getFormBundleReturns(callingUtr, testFormBundleNum).apply(FakeRequest())
        status(result) must be(BAD_REQUEST)
      }
      "respond with SERVICE_UNAVAILABLE, if ETMP is unavailable" in new Setup {
        when(mockFormBundleService.getFormBundleReturns(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = controller.getFormBundleReturns(callingUtr, testFormBundleNum).apply(FakeRequest())
        status(result) must be(SERVICE_UNAVAILABLE)
      }
      "respond with InternalServerError, if ETMP sends some server error response" in new Setup {
        when(mockFormBundleService.getFormBundleReturns(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, errorResponseJson, Map.empty[String, Seq[String]])))
        val result: Future[Result] = controller.getFormBundleReturns(callingUtr, testFormBundleNum).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }

}
