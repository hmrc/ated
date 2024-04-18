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

import builders.PropertyDetailsBuilder
import models._

import java.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Writes._
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.PropertyDetailsPeriodService
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}


class PropertyDetailsPeriodControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar {

  val mockPropertyDetailsService: PropertyDetailsPeriodService = mock[PropertyDetailsPeriodService]

  implicit lazy val compositeSymmetricCrypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]
  implicit lazy val crypto: Encrypter with Decrypter = compositeSymmetricCrypto.JsonCrypto
  implicit lazy val format: OFormat[PropertyDetails] = PropertyDetails.formats

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]
    implicit val ec: ExecutionContext = cc.executionContext
    class TestPropertyDetailsController extends BackendController(cc) with PropertyDetailsPeriodController {
      val propertyDetailsService: PropertyDetailsPeriodService = mockPropertyDetailsService
      override implicit val crypto: ApplicationCrypto = compositeSymmetricCrypto
      implicit val ec: ExecutionContext = cc.executionContext
    }

    val controller = new TestPropertyDetailsController()
  }

  "PropertyDetailsPeriodController" must {

    "saveDraftFullTaxPeriod" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsDatesLiable: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
        lazy val testPropertyDetailsPeriod: IsFullTaxPeriod = IsFullTaxPeriod(true, Some(testPropertyDetailsDatesLiable))
        when(mockPropertyDetailsService.cacheDraftFullTaxPeriod(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.saveDraftFullTaxPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetailsPeriod: IsFullTaxPeriod = IsFullTaxPeriod(true, None)
        when(mockPropertyDetailsService.cacheDraftFullTaxPeriod(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.saveDraftFullTaxPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftInRelief" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod: PropertyDetailsInRelief = PropertyDetailsInRelief()
        when(mockPropertyDetailsService.cacheDraftInRelief(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.saveDraftInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetailsPeriod: PropertyDetailsInRelief = PropertyDetailsInRelief()
        when(mockPropertyDetailsService.cacheDraftInRelief(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.saveDraftInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }



    "saveDraftDatesLiable" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
        when(mockPropertyDetailsService
          .cacheDraftDatesLiable(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetailsPeriod: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
        when(mockPropertyDetailsService.cacheDraftDatesLiable(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.saveDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "addDraftDatesLiable" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
        when(mockPropertyDetailsService.addDraftDatesLiable(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.addDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetailsPeriod: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
        when(mockPropertyDetailsService.addDraftDatesLiable(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.addDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "addDraftInRelief" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod: PropertyDetailsDatesInRelief = PropertyDetailsDatesInRelief(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
        when(mockPropertyDetailsService.addDraftDatesInRelief(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.addDraftDatesInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetailsPeriod: PropertyDetailsDatesInRelief = PropertyDetailsDatesInRelief(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
        when(mockPropertyDetailsService.addDraftDatesInRelief(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.addDraftDatesInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "deleteDraftPeriod" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod: LocalDate = LocalDate.of(1970, 1, 1)
        when(mockPropertyDetailsService.deleteDraftPeriod(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.deleteDraftPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetailsPeriod: LocalDate = LocalDate.of(1970, 1, 1)
        when(mockPropertyDetailsService.deleteDraftPeriod(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result: Future[Result] = controller.deleteDraftPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }
  }
}
