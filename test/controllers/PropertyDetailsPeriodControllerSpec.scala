/*
 * Copyright 2020 HM Revenue & Customs
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
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.PropertyDetailsPeriodService
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.Future


class PropertyDetailsPeriodControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar {

  val mockPropertyDetailsService: PropertyDetailsPeriodService = mock[PropertyDetailsPeriodService]

  implicit lazy val compositeSymmetricCrypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]
  implicit lazy val crypto: CryptoWithKeysFromConfig = compositeSymmetricCrypto.JsonCrypto
  implicit lazy val format: OFormat[PropertyDetails] = PropertyDetails.formats

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

    class TestPropertyDetailsController extends BackendController(cc) with PropertyDetailsPeriodController {
      val propertyDetailsService = mockPropertyDetailsService
      override implicit val crypto: ApplicationCrypto = compositeSymmetricCrypto
    }

    val controller = new TestPropertyDetailsController()
  }

  "PropertyDetailsPeriodController" must {

    "saveDraftFullTaxPeriod" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsDatesLiable = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        lazy val testPropertyDetailsPeriod = IsFullTaxPeriod(true, Some(testPropertyDetailsDatesLiable))
        when(mockPropertyDetailsService.cacheDraftFullTaxPeriod(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftFullTaxPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = IsFullTaxPeriod(true, None)
        when(mockPropertyDetailsService.cacheDraftFullTaxPeriod(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftFullTaxPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftInRelief" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsInRelief()
        when(mockPropertyDetailsService.cacheDraftInRelief(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsInRelief()
        when(mockPropertyDetailsService.cacheDraftInRelief(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }



    "saveDraftDatesLiable" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.cacheDraftDatesLiable(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.cacheDraftDatesLiable(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.saveDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "addDraftDatesLiable" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.addDraftDatesLiable(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.addDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.addDraftDatesLiable(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.addDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "addDraftInRelief" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesInRelief(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.addDraftDatesInRelief(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.addDraftDatesInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesInRelief(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.addDraftDatesInRelief(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.addDraftDatesInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "deleteDraftPeriod" must {

      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = new LocalDate("1970-01-01")
        when(mockPropertyDetailsService.deleteDraftPeriod(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.deleteDraftPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = new LocalDate("1970-01-01")
        when(mockPropertyDetailsService.deleteDraftPeriod(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(testPropertyDetailsPeriod))(ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = controller.deleteDraftPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }
  }
}
