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

package controllers

import models.SummaryReturnsModel
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.ReturnSummaryService

import scala.concurrent.Future

class ReturnsSummaryControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockReturnSummaryService = mock[ReturnSummaryService]
  val atedRefNo = "ATED-123"

  object TestReturnsSummaryController extends ReturnsSummaryController {
    val returnSummaryService: ReturnSummaryService = mockReturnSummaryService
  }

  override def beforeEach = {
    reset(mockReturnSummaryService)
  }


  "ReturnsSummaryController" must {
    "use correct ETMP connector" in {
      ReturnsSummaryController.returnSummaryService must be(ReturnSummaryService)
    }

    "getFullSummaryReturn" must {
      "return SummaryReturnsModel model, if found in cache or ETMP" in {
        val summaryReturnsModel = SummaryReturnsModel(None, Nil)
        when(mockReturnSummaryService.getFullSummaryReturns(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(summaryReturnsModel))
        val result = TestReturnsSummaryController.getFullSummaryReturn(atedRefNo).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(summaryReturnsModel))

      }

      "getPartialSummaryReturn" must {
        "return SummaryReturnsModel model, if found in cache or ETMP" in {
          val summaryReturnsModel = SummaryReturnsModel(None, Nil)
          when(mockReturnSummaryService.getPartialSummaryReturn(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(summaryReturnsModel))
          val result = TestReturnsSummaryController.getPartialSummaryReturn(atedRefNo).apply(FakeRequest())
          status(result) must be(OK)
          contentAsJson(result) must be(Json.toJson(summaryReturnsModel))

        }
      }
    }
  }

}
