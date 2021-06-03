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

import builders.PropertyDetailsBuilder
import models.{EditLiabilityReturnsResponseModel, PropertyDetails}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.ChangeLiabilityService
import uk.gov.hmrc.crypto.{ApplicationCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class ChangeLiabilityReturnControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockChangeLiabilityReturnService = mock[ChangeLiabilityService]
  val atedRefNo = "ated-123"
  val formBundle1 = "123456789012"
  val formBundle2 = "100000000000"
  val periodKey = 2015

  override def beforeEach = {
    reset(mockChangeLiabilityReturnService)
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val compositeSymmetricCrypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]
  implicit lazy val crypto: CryptoWithKeysFromConfig = compositeSymmetricCrypto.JsonCrypto
  implicit lazy val format: OFormat[PropertyDetails] = PropertyDetails.formats

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]
    implicit val ec: ExecutionContext = cc.executionContext
    class TestChangeLiabilityReturnController extends BackendController(cc) with ChangeLiabilityReturnController {
      override implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      override val changeLiabilityService: ChangeLiabilityService = mockChangeLiabilityReturnService
      override val crypto: ApplicationCrypto = compositeSymmetricCrypto
    }

    val controller = new TestChangeLiabilityReturnController()
  }

  "ChangeLiabilityReturnController" must {

    "convertSubmittedReturnToCachedDraft" must {
      "return ChangeLiabilityReturn model, if found in cache or ETMP" in new Setup {
        lazy val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        when(mockChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(changeLiabilityReturn)))
        val result = controller.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(changeLiabilityReturn))
      }

      "return ChangeLiabilityReturn model, if NOT-found in cache or ETMP" in new Setup {
        when(mockChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        val result = controller.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse( """{}"""))
      }
    }

    "convertPreviousSubmittedReturnToCachedDraft" must {
      "return ChangeLiabilityReturn model, if found in cache or ETMP" in new Setup {
        lazy val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        when(mockChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(changeLiabilityReturn)))
        val result = controller.convertPreviousSubmittedReturnToCachedDraft(atedRefNo, formBundle1, periodKey).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(changeLiabilityReturn))
      }

      "return ChangeLiabilityReturn model, if NOT-found in cache or ETMP" in new Setup {
        when(mockChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(ArgumentMatchers.eq(atedRefNo),
          ArgumentMatchers.eq(formBundle1), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))
        val result = controller.convertPreviousSubmittedReturnToCachedDraft(atedRefNo, formBundle1, periodKey).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse( """{}"""))
      }
    }

    "calculateDraftChangeLiability" must {

      "respond with OK and the Property Details if we have one" in new Setup {

        val testAccountRef = "ATED1223123"
        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))

        when(mockChangeLiabilityReturnService.calculateDraftChangeLiability(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest()
        val result = controller.calculateDraftChangeLiability(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result).as[JsValue] must be(Json.toJson(testPropertyDetails))
      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        when(mockChangeLiabilityReturnService.calculateDraftChangeLiability(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest()
        val result = controller.calculateDraftChangeLiability(testAccountRef, "1").apply(fakeRequest)

        status(result) must be(BAD_REQUEST)

      }
    }

    "submitChangeLiabilityReturn" must {
      "for successful submit, return OK as response status" in new Setup {
        val successResponse = EditLiabilityReturnsResponseModel(DateTime.now(), liabilityReturnResponse = Seq(), accountBalance = BigDecimal(0.00))
        when(mockChangeLiabilityReturnService.submitChangeLiability(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, Json.toJson(successResponse), Map.empty[String, Seq[String]])))
        val result = controller.submitChangeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(OK)
      }

      "for unsuccessful submit, return internal server error response" in new Setup {
        val errorResponse = Json.parse( """{"reason": "Some error"}""")
        when(mockChangeLiabilityReturnService.submitChangeLiability(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, Json.toJson(errorResponse), Map.empty[String, Seq[String]])))
        val result = controller.submitChangeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }
}
