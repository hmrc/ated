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

import builders.{ReliefBuilder, TestAudit}
import connectors.AuthConnector
import models.{Reliefs, ReliefsTaxAvoidance, TaxAvoidance}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.ReliefsService
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReliefsControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockReliefsService: ReliefsService = mock[ReliefsService]
  val periodKey = 2015
  val mockAuthConnector = mock[AuthConnector]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

    class TestReliefsController extends BackendController(cc) with ReliefsController {
      val reliefsService = mockReliefsService
      val isAgent = false
      val audit: Audit = new TestAudit(mockAuditConnector)
      val appName: String = "Test"
      override val authConnector = mockAuthConnector
    }

    val testReliefsController = new TestReliefsController()
  }

  override def beforeEach = {
    reset(mockReliefsService)
    reset(mockAuthConnector)
  }

  "ReliefsController" must {

    "retrieveDraftReliefs" must {
      "respond with OK and the Reliefs if we have one" in new Setup {
        val testAccountRef = "ATED1223123"

        val testReliefs = ReliefBuilder.reliefTaxAvoidance(testAccountRef, periodKey, Reliefs(periodKey = periodKey))

        when(mockReliefsService.retrieveDraftReliefs(any())(any())).thenReturn(Future(Seq(testReliefs)))

        val fakeRequest = FakeRequest()
        val result = testReliefsController.retrieveDraftReliefs(testAccountRef, periodKey).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result).as[JsValue] must be(Json.toJson(testReliefs))
      }

      "respond with NotFound and No Reliefs if we have None" in new Setup {
        val testAccountRef = "ATED1223123"

        when(mockReliefsService.retrieveDraftReliefs(any())(any())).thenReturn(Future(Seq()))

        val fakeRequest = FakeRequest()
        val result = testReliefsController.retrieveDraftReliefs(testAccountRef, periodKey).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
        contentAsJson(result).toString() must be("{}")
      }
    }

    "saveDraftReliefs" must {


      "respond with OK and a list of cached reliefs" in new Setup {
        val testAccountRef = "ATED1223123"
        val taxAvoidance = TaxAvoidance(
          rentalBusinessScheme = Some("Scheme1"),
          rentalBusinessSchemePromoter = Some("Promoter1"),
          openToPublicScheme = Some("Scheme2"),
          openToPublicSchemePromoter = Some("Scheme2"),
          propertyDeveloperScheme = Some("Scheme3"),
          propertyDeveloperSchemePromoter = Some("Scheme3"),
          propertyTradingScheme = Some("Scheme4"),
          propertyTradingSchemePromoter = Some("Promoter4"),
          lendingScheme = Some("Scheme5"),
          lendingSchemePromoter = Some("Promoter5"),
          employeeOccupationScheme = Some("Scheme6"),
          employeeOccupationSchemePromoter = Some("Promoter6"),
          farmHousesScheme = Some("Scheme7"),
          farmHousesSchemePromoter = Some("Promoter7"),
          socialHousingScheme = Some("Scheme8"),
          socialHousingSchemePromoter = Some("Promoter8"),
          equityReleaseScheme = Some("Scheme9"),
          equityReleaseSchemePromoter = Some("Promoter9")
        )
        val testReliefs = ReliefBuilder.reliefTaxAvoidance(testAccountRef, periodKey, Reliefs(periodKey = periodKey), taxAvoidance)

        when(mockReliefsService.saveDraftReliefs(any(), any())(any())).thenReturn(Future(Seq(testReliefs)))
        when(mockAuthConnector.agentReferenceNo(any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testReliefs))
        val result = testReliefsController.saveDraftReliefs(testAccountRef).apply(fakeRequest)
        status(result) must be(OK)
        contentAsJson(result).as[JsValue] must be(Json.toJson(Seq(testReliefs)))

      }

    }

    "submitReliefs" must {
      val testAccountRef = "ATED1223123"

      "respond with OK and delete cached reliefs" in new Setup {

        val fakeRequest = FakeRequest()
        val submitSuccess = Json.parse( """{"status" : "OK", "processingDate" :  "2014-12-17T09:30:47Z", "formBundleNumber" : "123456789012"}""")
        when(mockReliefsService.submitAndDeleteDraftReliefs(any(), any())(any())).thenReturn(Future.successful(HttpResponse(OK, Some(submitSuccess))))
        val result = testReliefsController.submitDraftReliefs(testAccountRef, periodKey).apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with OK and an invalid response cached reliefs" in new Setup {

        val fakeRequest = FakeRequest()
        val submitSuccess = Json.parse( """{"status" : "OK", "processingDate" :  "2014-12-17T09:30:47Z", "formBundleNo" : "123456789012"}""")
        when(mockReliefsService.submitAndDeleteDraftReliefs(any(), any())(any())).thenReturn(Future.successful(HttpResponse(OK, Some(submitSuccess))))
        val result = testReliefsController.submitDraftReliefs(testAccountRef, periodKey).apply(fakeRequest)
        status(result) must be(OK)
      }
      "handle a bad request" in new Setup {
        val fakeRequest = FakeRequest()
        val serviceUnavailable = Json.parse( """{"reason" : "Service unavailable"}""")
        when(mockReliefsService.submitAndDeleteDraftReliefs(any(), any())(any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(serviceUnavailable))))
        val result = testReliefsController.submitDraftReliefs(testAccountRef, periodKey).apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }

      "handle a not found" in new Setup {
        val fakeRequest = FakeRequest()
        val serviceUnavailable = Json.parse( """{"reason" : "Service unavailable"}""")
        when(mockReliefsService.submitAndDeleteDraftReliefs(any(), any())(any())).thenReturn(Future.successful(HttpResponse(NOT_FOUND, Some(serviceUnavailable))))
        val result = testReliefsController.submitDraftReliefs(testAccountRef, periodKey).apply(fakeRequest)
        status(result) must be(NOT_FOUND)
      }

      "handle a service unavailable" in new Setup {
        val fakeRequest = FakeRequest()
        val serviceUnavailable = Json.parse( """{"reason" : "Service unavailable"}""")
        when(mockReliefsService.submitAndDeleteDraftReliefs(any(), any())(any())).thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, Some(serviceUnavailable))))
        val result = testReliefsController.submitDraftReliefs(testAccountRef, periodKey).apply(fakeRequest)
        status(result) must be(SERVICE_UNAVAILABLE)
      }

      "handle an internal server errror" in new Setup {
        val fakeRequest = FakeRequest()
        val serviceUnavailable = Json.parse( """{"reason" : "Service unavailable"}""")
        when(mockReliefsService.submitAndDeleteDraftReliefs(any(), any())(any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, Some(serviceUnavailable))))
        val result = testReliefsController.submitDraftReliefs(testAccountRef, periodKey).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }

    }

    "delete draft reliefs" when {
      val testAccountRef = "ATED1223123"

      "a call is made to the delete api" in new Setup {
        val fakeRequest = FakeRequest()
        when(mockReliefsService.deleteAllDraftReliefs(any())(any())) thenReturn Future.successful(Seq.empty)

        val result = testReliefsController.deleteDraftReliefs(testAccountRef).apply(fakeRequest)
        status(result) must be(OK)
      }

    }

    "deleteDraftReliefsByYear" must {
      "respond with OK when list is empty" in new Setup {
        val testAccountRef = "ATED1223123"
        when(mockReliefsService.deleteAllDraftReliefByYear(ArgumentMatchers.eq(testAccountRef), ArgumentMatchers.eq(2017))(any())).thenReturn(Future(Seq[ReliefsTaxAvoidance]()))
        val result = testReliefsController.deleteDraftReliefsByYear(testAccountRef, 2017).apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(OK)
      }

      "respond with INTERNAL_SERVER_ERROR when list is not empty" in new Setup {
        val testAccountRef = "ATED1223123"
        val taxAvoidance = TaxAvoidance(
          rentalBusinessScheme = Some("Scheme1"),
          rentalBusinessSchemePromoter = Some("Promoter1"),
          openToPublicScheme = Some("Scheme2"),
          openToPublicSchemePromoter = Some("Scheme2"),
          propertyDeveloperScheme = Some("Scheme3"),
          propertyDeveloperSchemePromoter = Some("Scheme3"),
          propertyTradingScheme = Some("Scheme4"),
          propertyTradingSchemePromoter = Some("Promoter4"),
          lendingScheme = Some("Scheme5"),
          lendingSchemePromoter = Some("Promoter5"),
          employeeOccupationScheme = Some("Scheme6"),
          employeeOccupationSchemePromoter = Some("Promoter6"),
          farmHousesScheme = Some("Scheme7"),
          farmHousesSchemePromoter = Some("Promoter7"),
          socialHousingScheme = Some("Scheme8"),
          socialHousingSchemePromoter = Some("Promoter8"),
          equityReleaseScheme = Some("Scheme9"),
          equityReleaseSchemePromoter = Some("Promoter9")
        )
        val testReliefs = ReliefBuilder.reliefTaxAvoidance(testAccountRef, periodKey, Reliefs(periodKey = periodKey), taxAvoidance)
        when(mockReliefsService.deleteAllDraftReliefByYear(ArgumentMatchers.eq(testAccountRef), ArgumentMatchers.eq(2017))(any())).thenReturn(Future(Seq[ReliefsTaxAvoidance](testReliefs)))
        val result = testReliefsController.deleteDraftReliefsByYear(testAccountRef, 2017).apply(FakeRequest().withJsonBody(Json.parse( """{}""")))
        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }

    "not delete draft reliefs" when {
      val testAccountRef = "ATED1223123"

      "an exception is throw during deletion" in new Setup {
        val taxAvoidance = TaxAvoidance(
          rentalBusinessScheme = Some("Scheme1"),
          openToPublicScheme = Some("Scheme2"),
          propertyDeveloperScheme = Some("Scheme3"),
          propertyTradingScheme = Some("Scheme4"),
          lendingScheme = Some("Scheme5"),
          employeeOccupationScheme = Some("Scheme6"),
          farmHousesScheme = Some("Scheme7"),
          socialHousingScheme = Some("Scheme8"),
          equityReleaseScheme = Some("Scheme9")
        )
        val testReliefs = ReliefBuilder.reliefTaxAvoidance(testAccountRef, periodKey, Reliefs(periodKey = periodKey), taxAvoidance)

        val fakeRequest = FakeRequest()
        when(mockReliefsService.deleteAllDraftReliefs(any())(any())) thenReturn Future.successful(Seq(testReliefs))
        val result = testReliefsController.deleteDraftReliefs(testAccountRef).apply(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
      }

    }

  }
}
