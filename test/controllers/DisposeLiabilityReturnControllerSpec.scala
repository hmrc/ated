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

import builders.ChangeLiabilityReturnBuilder
import models._
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.DisposeLiabilityReturnService

import scala.concurrent.Future
import uk.gov.hmrc.http.HttpResponse

class DisposeLiabilityReturnControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockDisposeLiabilityReturnService = mock[DisposeLiabilityReturnService]
  val atedRefNo = "ated-123"
  val formBundle1 = "123456789012"
  val formBundle2 = "100000000000"
  val periodKey = 2015

  override def beforeEach = {
    reset(mockDisposeLiabilityReturnService)
  }

  object TestDisposeLiabilityReturnController extends DisposeLiabilityReturnController {
    override val disposeLiabilityReturnService = mockDisposeLiabilityReturnService
  }

  "DisposeLiabilityReturnController" must {
    "use correct service" in {
      DisposeLiabilityReturnController.disposeLiabilityReturnService must be(DisposeLiabilityReturnService)
    }

    "retrieveAndCacheDisposeLiabilityReturn" must {
      "return DisposeLiabilityReturn model, if found in cache or ETMP" in {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp)
        when(mockDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(Matchers.eq(atedRefNo), Matchers.eq(formBundle1))(Matchers.any())).thenReturn(Future.successful(Some(dispose1)))
        val result = TestDisposeLiabilityReturnController.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "return DisposeLiabilityReturn model, if NOT-found in cache or ETMP" in {
        when(mockDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(Matchers.eq(atedRefNo), Matchers.eq(formBundle1))(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestDisposeLiabilityReturnController.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "updateDisposalDate" must {
      "for successful save, return DisposeLiabilityReturn model with OK as response status" in {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val d1 = DisposeLiability(dateOfDisposal = None, periodKey)
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp, disposeLiability = Some(d1))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(d1))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeLiabilityReturnDate(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(d1))(Matchers.any())).thenReturn(Future.successful(Some(dispose1)))
        val result = TestDisposeLiabilityReturnController.updateDisposalDate(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val d1 = DisposeLiability(dateOfDisposal = None, periodKey)
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp, disposeLiability = Some(d1))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(d1))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeLiabilityReturnDate(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(d1))(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestDisposeLiabilityReturnController.updateDisposalDate(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "updateHasBankDetails" must {
      "for successful save, return DisposeLiabilityReturn model with OK as response status" in {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val bank1 = BankDetailsModel(true, Some(BankDetails(None, None, None, None)))
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp, bankDetails = Some(bank1))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(true))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(true))(Matchers.any())).thenReturn(Future.successful(Some(dispose1)))
        val result = TestDisposeLiabilityReturnController.updateHasBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(false))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(false))(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestDisposeLiabilityReturnController.updateHasBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "updateBankDetails" must {
      "for successful save, return DisposeLiabilityReturn model with OK as response status" in {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val bank1 = BankDetails(None, None, None, None)
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp, bankDetails = Some(BankDetailsModel(true, Some(bank1))))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(bank1))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeBankDetails(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(bank1))(Matchers.any())).thenReturn(Future.successful(Some(dispose1)))
        val result = TestDisposeLiabilityReturnController.updateBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val bank1 = BankDetails(None, None, None, None)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(bank1))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeBankDetails(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.eq(bank1))(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestDisposeLiabilityReturnController.updateBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "calculateDraftDispose" must {
      "for successful save, return DisposeLiabilityReturn model with OK as response status" in {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val fakeRequest = FakeRequest()
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp)
        when(mockDisposeLiabilityReturnService.calculateDraftDispose(Matchers.eq(atedRefNo), Matchers.eq(formBundle1))(Matchers.any())).thenReturn(Future.successful(Some(dispose1)))
        val result = TestDisposeLiabilityReturnController.calculateDraftDisposal(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val fakeRequest = FakeRequest()
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp)
        when(mockDisposeLiabilityReturnService.calculateDraftDispose(Matchers.eq(atedRefNo), Matchers.eq(formBundle1))(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestDisposeLiabilityReturnController.calculateDraftDisposal(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "submitDisposeLiabilityReturn" must {
      "for successful submit, return OK as response status" in {
        val successResponse = EditLiabilityReturnsResponseModel(DateTime.now(), liabilityReturnResponse = Seq(), accountBalance = BigDecimal(0.00))
        when(mockDisposeLiabilityReturnService.submitDisposeLiability(Matchers.eq(atedRefNo), Matchers.eq(formBundle1))(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson(successResponse)))))
        val result = TestDisposeLiabilityReturnController.submitDisposeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(OK)
      }

      "for unsuccessful submit, return internal server error response" in {
        val errorResponse = Json.parse("""{"reason": "Some error"}""")
        when(mockDisposeLiabilityReturnService.submitDisposeLiability(Matchers.eq(atedRefNo), Matchers.eq(formBundle1))(Matchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(Json.toJson(errorResponse)))))
        val result = TestDisposeLiabilityReturnController.submitDisposeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

  }

}
