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

import builders.{TestAudit, PropertyDetailsBuilder, ChangeLiabilityReturnBuilder}
import models.{PropertyDetailsTitle, EditLiabilityReturnsResponseModel}
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.{ChangeLiabilityService, PropertyDetailsService}
import uk.gov.hmrc.play.audit.model.Audit

import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

class ChangeLiabilityReturnControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockChangeLiabilityReturnService = mock[ChangeLiabilityService]
  val atedRefNo = "ated-123"
  val formBundle1 = "123456789012"
  val formBundle2 = "100000000000"
  val periodKey = 2015

  override def beforeEach = {
    reset(mockChangeLiabilityReturnService)
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  object TestChangeLiabilityReturnController extends ChangeLiabilityReturnController {
    override val changeLiabilityService = mockChangeLiabilityReturnService
  }

  "ChangeLiabilityReturnController" must {

    "use the correct service" in {
      ChangeLiabilityReturnController.changeLiabilityService must be(ChangeLiabilityService)
      PropertyDetailsService
    }
    "convertSubmittedReturnToCachedDraft" must {
      "return ChangeLiabilityReturn model, if found in cache or ETMP" in {
        lazy val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        when(mockChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(Some(changeLiabilityReturn)))
        val result = TestChangeLiabilityReturnController.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(changeLiabilityReturn))
      }

      "return ChangeLiabilityReturn model, if NOT-found in cache or ETMP" in {
        lazy val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        when(mockChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestChangeLiabilityReturnController.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse( """{}"""))
      }
    }

    "convertPreviousSubmittedReturnToCachedDraft" must {
      "return ChangeLiabilityReturn model, if found in cache or ETMP" in {
        lazy val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        when(mockChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(Some(changeLiabilityReturn)))
        val result = TestChangeLiabilityReturnController.convertPreviousSubmittedReturnToCachedDraft(atedRefNo, formBundle1, periodKey).apply(FakeRequest())
        status(result) must be(OK)
        contentAsJson(result) must be(Json.toJson(changeLiabilityReturn))
      }

      "return ChangeLiabilityReturn model, if NOT-found in cache or ETMP" in {
        lazy val changeLiabilityReturn = PropertyDetailsBuilder.getFullPropertyDetails(formBundle1)
        when(mockChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(Matchers.eq(atedRefNo), Matchers.eq(formBundle1), Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestChangeLiabilityReturnController.convertPreviousSubmittedReturnToCachedDraft(atedRefNo, formBundle1, periodKey).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
        contentAsJson(result) must be(Json.parse( """{}"""))
      }
    }



    "calculateDraftChangeLiability" must {

      "respond with OK and the Property Details if we have one" in {
        import scala.concurrent.ExecutionContext.Implicits.global
        val testAccountRef = "ATED1223123"
        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))

        when(mockChangeLiabilityReturnService.calculateDraftChangeLiability(Matchers.eq(testAccountRef), Matchers.eq("1"))(Matchers.any())).thenReturn(Future(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest()
        val result = TestChangeLiabilityReturnController.calculateDraftChangeLiability(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result).as[JsValue] must be(Json.toJson(testPropertyDetails))
      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        when(mockChangeLiabilityReturnService.calculateDraftChangeLiability(Matchers.eq(testAccountRef),
          Matchers.eq("1"))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest()
        val result = TestChangeLiabilityReturnController.calculateDraftChangeLiability(testAccountRef, "1").apply(fakeRequest)

        status(result) must be(BAD_REQUEST)

      }
    }
    "submitChangeLiabilityReturn" must {
      "for successful submit, return OK as response status" in {
        val successResponse = EditLiabilityReturnsResponseModel(DateTime.now(), liabilityReturnResponse = Seq(), accountBalance = BigDecimal(0.00))
        when(mockChangeLiabilityReturnService.submitChangeLiability(Matchers.eq(atedRefNo), Matchers.eq(formBundle1))(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson(successResponse)))))
        val result = TestChangeLiabilityReturnController.submitChangeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(OK)
      }

      "for unsuccessful submit, return internal server error response" in {
        val errorResponse = Json.parse( """{"reason": "Some error"}""")
        when(mockChangeLiabilityReturnService.submitChangeLiability(Matchers.eq(atedRefNo), Matchers.eq(formBundle1))(Matchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(Json.toJson(errorResponse)))))
        val result = TestChangeLiabilityReturnController.submitChangeLiabilityReturn(atedRefNo, formBundle1).apply(FakeRequest())
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

  }

}
