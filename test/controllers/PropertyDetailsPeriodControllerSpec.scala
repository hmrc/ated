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

import builders.{PropertyDetailsBuilder, TestAudit}
import models._
import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.PropertyDetailsPeriodService
import uk.gov.hmrc.play.audit.model.Audit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PropertyDetailsPeriodControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar {

  val mockPropertyDetailsService: PropertyDetailsPeriodService = mock[PropertyDetailsPeriodService]

  object TestPropertyDetailsController extends PropertyDetailsPeriodController {
    val propertyDetailsService = mockPropertyDetailsService
  }

  "PropertyDetailsPeriodController" must {

    "use the correct Service" in {
      PropertyDetailsPeriodController.propertyDetailsService must be(PropertyDetailsPeriodService)
    }

    "saveDraftFullTaxPeriod" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsDatesLiable = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        lazy val testPropertyDetailsPeriod = IsFullTaxPeriod(true, Some(testPropertyDetailsDatesLiable))
        when(mockPropertyDetailsService.cacheDraftFullTaxPeriod(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftFullTaxPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = IsFullTaxPeriod(true, None)
        when(mockPropertyDetailsService.cacheDraftFullTaxPeriod(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftFullTaxPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftInRelief" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsInRelief()
        when(mockPropertyDetailsService.cacheDraftInRelief(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsInRelief()
        when(mockPropertyDetailsService.cacheDraftInRelief(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }



    "saveDraftDatesLiable" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.cacheDraftDatesLiable(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.cacheDraftDatesLiable(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.saveDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "addDraftDatesLiable" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.addDraftDatesLiable(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.addDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.addDraftDatesLiable(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.addDraftDatesLiable(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "addDraftInRelief" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesInRelief(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.addDraftDatesInRelief(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.addDraftDatesInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = PropertyDetailsDatesInRelief(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
        when(mockPropertyDetailsService.addDraftDatesInRelief(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.addDraftDatesInRelief(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "deleteDraftPeriod" must {

      "respond with OK and a list of cached Property Details if this all works" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = new LocalDate("1970-01-01")
        when(mockPropertyDetailsService.deleteDraftPeriod(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.deleteDraftPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in {
        val testAccountRef = "ATED1223123"

        lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))
        lazy val testPropertyDetailsPeriod = new LocalDate("1970-01-01")
        when(mockPropertyDetailsService.deleteDraftPeriod(Matchers.eq(testAccountRef),
          Matchers.eq("1"), Matchers.eq(testPropertyDetailsPeriod))(org.mockito.Matchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(testPropertyDetailsPeriod))
        val result = TestPropertyDetailsController.deleteDraftPeriod(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }
  }
}
