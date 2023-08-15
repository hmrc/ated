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

import models.SummaryReturnsModel
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
import services.ReturnSummaryService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class ReturnsSummaryControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockReturnSummaryService = mock[ReturnSummaryService]
  val atedRefNo = "ATED-123"

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]
    implicit val ec: ExecutionContext = cc.executionContext
    class TestReturnsSummaryController extends BackendController(cc) with ReturnsSummaryController {
      implicit val ec: ExecutionContext = cc.executionContext
      val returnSummaryService: ReturnSummaryService = mockReturnSummaryService
    }

    val controller = new TestReturnsSummaryController()
  }

  override def beforeEach() = {
    reset(mockReturnSummaryService)
  }

  "ReturnsSummaryController" must {

    "getFullSummaryReturn" must {
      "return SummaryReturnsModel model, if found in cache or ETMP" in new Setup {
        val summaryReturnsModel = SummaryReturnsModel(None, Nil)
        when(mockReturnSummaryService.getFullSummaryReturns(ArgumentMatchers.eq(atedRefNo))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(summaryReturnsModel))
        val result = controller.getFullSummaryReturn(atedRefNo).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(summaryReturnsModel))

      }

      "getPartialSummaryReturn" must {
        "return SummaryReturnsModel model, if found in cache or ETMP" in new Setup {
          val summaryReturnsModel = SummaryReturnsModel(None, Nil)
          when(mockReturnSummaryService.getPartialSummaryReturn(ArgumentMatchers.eq(atedRefNo))(ArgumentMatchers.any()))
            .thenReturn(Future.successful(summaryReturnsModel))
          val result = controller.getPartialSummaryReturn(atedRefNo).apply(FakeRequest())
          status(result) must be(OK)
          contentAsJson(result) must be(Json.toJson(summaryReturnsModel))

        }
      }
    }
  }

}
