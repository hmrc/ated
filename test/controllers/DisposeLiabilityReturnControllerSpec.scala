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

import builders.ChangeLiabilityReturnBuilder
import models._
import java.time.ZonedDateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.DisposeLiabilityReturnService
import uk.gov.hmrc.crypto.{ApplicationCrypto, Encrypter, Decrypter}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class DisposeLiabilityReturnControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockDisposeLiabilityReturnService = mock[DisposeLiabilityReturnService]
  val atedRefNo = "ated-123"
  val formBundle1 = "123456789012"
  val formBundle2 = "100000000000"
  val periodKey = 2015

  override def beforeEach() = {
    reset(mockDisposeLiabilityReturnService)
  }

  implicit lazy val compositeSymmetricCrypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]
  implicit lazy val crypto: Encrypter with Decrypter = compositeSymmetricCrypto.JsonCrypto
  implicit lazy val format: OFormat[DisposeLiabilityReturn] = DisposeLiabilityReturn.formats
  implicit lazy val liabilityFormat: OFormat[DisposeLiability] = DisposeLiability.formats

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]
    implicit val ec: ExecutionContext = cc.executionContext

    class TestDisposeLiabilityReturnController extends BackendController(cc) with DisposeLiabilityReturnController {
      implicit val ec: ExecutionContext = cc.executionContext
      override val disposeLiabilityReturnService: DisposeLiabilityReturnService = mockDisposeLiabilityReturnService
      override implicit val crypto: ApplicationCrypto = compositeSymmetricCrypto
    }

    val controller = new TestDisposeLiabilityReturnController()
  }

  "DisposeLiabilityReturnController" must {
    "retrieveAndCacheDisposeLiabilityReturn" must {
      "return DisposeLiabilityReturn model, if found in cache or ETMP" in new Setup {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp)
        when(mockDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(dispose1)))
        val result = controller.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "return DisposeLiabilityReturn model, if NOT-found in cache or ETMP" in new Setup {
        when(mockDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
        val result = controller.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "updateDisposalDate" must {
      "for successful save, return DisposeLiabilityReturn model with OK as response status" in new Setup {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val d1 = DisposeLiability(dateOfDisposal = None, periodKey)
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp, disposeLiability = Some(d1))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(d1))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeLiabilityReturnDate(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.eq(d1))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(dispose1)))
        val result = controller.updateDisposalDate(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in new Setup {
        val d1 = DisposeLiability(dateOfDisposal = None, periodKey)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(d1))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeLiabilityReturnDate(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.eq(d1))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        val result = controller.updateDisposalDate(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "updateHasBankDetails" must {
      "for successful save, return DisposeLiabilityReturn model with OK as response status" in new Setup {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val bank1 = BankDetailsModel(true, Some(BankDetails(None, None, None, None)))
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp, bankDetails = Some(bank1))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(true))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.eq(true))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(dispose1)))
        val result = controller.updateHasBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in new Setup {
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(false))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.eq(false))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        val result = controller.updateHasBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "updateBankDetails" must {
      "for successful save, return DisposeLiabilityReturn model with OK as response status" in new Setup {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val bank1 = BankDetails(None, None, None, None)
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp, bankDetails = Some(BankDetailsModel(true, Some(bank1))))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(bank1))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeBankDetails(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.eq(bank1))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(dispose1)))
        val result = controller.updateBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in new Setup {
        val bank1 = BankDetails(None, None, None, None)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(bank1))
        when(mockDisposeLiabilityReturnService.updateDraftDisposeBankDetails(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.eq(bank1))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        val result = controller.updateBankDetails(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "calculateDraftDispose" must {
      "for successful save, return DisposeLiabilityReturn model with OK as response status" in new Setup {
        lazy val formBundleResp = ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)
        val fakeRequest = FakeRequest()
        val dispose1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleResp)
        when(mockDisposeLiabilityReturnService.calculateDraftDispose(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(dispose1)))
        val result = controller.calculateDraftDisposal(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(dispose1))
      }

      "for unsuccessful save, return None with NOT_FOUND as response status" in new Setup {
        val fakeRequest = FakeRequest()
        when(mockDisposeLiabilityReturnService.calculateDraftDispose(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        val result = controller.calculateDraftDisposal(atedRefNo, formBundle1).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse("""{}"""))
      }
    }

    "submitDisposeLiabilityReturn" must {
      "for successful submit, return OK as response status" in new Setup {
        val successResponse = EditLiabilityReturnsResponseModel(ZonedDateTime.now(), liabilityReturnResponse = Seq(), accountBalance = BigDecimal(0.00))
        when(mockDisposeLiabilityReturnService.submitDisposeLiability(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1))
            (ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Json.toJson(successResponse), Map.empty[String, Seq[String]])))

        val result = controller.submitDisposeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(OK)
      }

      "for unsuccessful submit, return internal server error response" in new Setup {
        val errorResponse = Json.parse("""{"reason": "Some error"}""")
        when(mockDisposeLiabilityReturnService.submitDisposeLiability(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, Json.toJson(errorResponse), Map.empty[String, Seq[String]])))
        val result = controller.submitDisposeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }
}
