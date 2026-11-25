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
import models.*
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.*
import play.api.test.{FakeHeaders, FakeRequest}
import services.PropertyDetailsValuesService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class PropertyDetailsValuesControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar {

  val mockPropertyDetailsService: PropertyDetailsValuesService = mock[PropertyDetailsValuesService]

  trait Setup {
    val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

    given ec: ExecutionContext = cc.executionContext

    val testAccountRef = "ATED1223123"
    lazy val testPropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("testPostCode1"))

    class TestPropertyDetailsController extends BackendController(cc) with PropertyDetailsValuesController {
      val propertyDetailsService = mockPropertyDetailsService

      given ec: ExecutionContext = cc.executionContext
    }

    val controller = new TestPropertyDetailsController()
  }

  "PropertyDetailsValuesController" must {
    "saveDraftHasValueChanged" must {
      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val updated = true
        when(mockPropertyDetailsService.cacheDraftHasValueChanged(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftHasValueChanged(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val updated = true
        when(mockPropertyDetailsService.cacheDraftHasValueChanged(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftHasValueChanged(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsAcquisition" must {
      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val updated = true
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsAcquisition(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val updated = true
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(updated))(using ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(updated))
        val result = controller.saveDraftPropertyDetailsAcquisition(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsIsPropertyRevalued" must {
      "respond with OK when saving isPropertyRevalued" in new Setup {
        val update = PropertyDetailsBuilder.getPropertyDetailsIsPropertyRevalued
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsIsPropertyRevalued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsIsPropertyRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with BAD_REQUEST when saving isPropertyRevalued fails" in new Setup {
        val update = PropertyDetailsBuilder.getPropertyDetailsIsPropertyRevalued
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsIsPropertyRevalued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsIsPropertyRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsFortyThousandValueDateOfChange" must {
      "respond with OK when saving revaluedDate" in new Setup {
        val update = PropertyDetailsBuilder.getPropertyDetailsDateOfRevaluation
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsDateOfRevaluation(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsDateOfRevaluation(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with BAD_REQUEST when saving revaluedDate fails" in new Setup {
        val update = PropertyDetailsBuilder.getPropertyDetailsDateOfRevaluation
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsDateOfRevaluation(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsDateOfRevaluation(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsNewValuation" must {
      "respond with OK when saving revaluedValue" in new Setup {
        val update = PropertyDetailsBuilder.getPropertyDetailsNewValuation
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewValuation(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewValuation(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with BAD_REQUEST when saving revaluedValue fails" in new Setup {
        val update = PropertyDetailsBuilder.getPropertyDetailsNewValuation
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewValuation(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewValuation(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsDateOfRevaluation" must {
      "respond with OK when saving dateOfChange" in new Setup {
        val update = PropertyDetailsBuilder.getPropertyDetailsFortyThousandValueDateOfChange
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsFortyThousandValueDateOfChange(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsFortyThousandValueDateOfChange(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with BAD_REQUEST when saving dateOfChange fails" in new Setup {
        val update = PropertyDetailsBuilder.getPropertyDetailsFortyThousandValueDateOfChange
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsFortyThousandValueDateOfChange(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsFortyThousandValueDateOfChange(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsIsPropertyRevalued, saveDraftPropertyDetailsFortyThousandValueDateOfChange, saveDraftPropertyDetailsNewValuation and saveDraftPropertyDetailsDateOfRevaluation" must {
      "save all components and retrieve the combined PropertyDetailsValue" in new Setup {
        val isPropertyRevalued = PropertyDetailsBuilder.getPropertyDetailsIsPropertyRevalued
        val revaluedValue = PropertyDetailsBuilder.getPropertyDetailsNewValuation
        val revaluedDate = PropertyDetailsBuilder.getPropertyDetailsDateOfRevaluation
        val dateOfChange = PropertyDetailsBuilder.getPropertyDetailsFortyThousandValueDateOfChange

        when(mockPropertyDetailsService.cacheDraftPropertyDetailsIsPropertyRevalued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(isPropertyRevalued))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewValuation(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(revaluedValue))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        when(mockPropertyDetailsService.cacheDraftPropertyDetailsDateOfRevaluation(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(revaluedDate))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        when(mockPropertyDetailsService.cacheDraftPropertyDetailsFortyThousandValueDateOfChange(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(dateOfChange))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(isPropertyRevalued))
        val result = controller.saveDraftPropertyDetailsIsPropertyRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

        val combinedResult = PropertyDetailsBuilder.getPropertyDetailsValueRevalued
        combinedResult mustBe Some(PropertyDetailsValue(
          isPropertyRevalued = isPropertyRevalued.isPropertyRevalued,
          revaluedValue = revaluedValue.revaluedValue,
          revaluedDate = revaluedDate.dateOfRevalue,
          partAcqDispDate = dateOfChange.dateOfChange
        ))
      }

      "respond with BAD_REQUEST when bulk saving fails" in new Setup {
        val isPropertyRevalued = PropertyDetailsBuilder.getPropertyDetailsIsPropertyRevalued
        val revaluedValue = PropertyDetailsBuilder.getPropertyDetailsNewValuation
        val revaluedDate = PropertyDetailsBuilder.getPropertyDetailsDateOfRevaluation
        val dateOfChange = PropertyDetailsBuilder.getPropertyDetailsFortyThousandValueDateOfChange

        when(mockPropertyDetailsService.cacheDraftPropertyDetailsIsPropertyRevalued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(isPropertyRevalued))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewValuation(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(revaluedValue))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        when(mockPropertyDetailsService.cacheDraftPropertyDetailsDateOfRevaluation(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(revaluedDate))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        when(mockPropertyDetailsService.cacheDraftPropertyDetailsFortyThousandValueDateOfChange(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(dateOfChange))(using ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(isPropertyRevalued))
        val result = controller.saveDraftPropertyDetailsIsPropertyRevalued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsOwnedBefore" must {
      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val update = PropertyDetailsOwnedBefore()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsOwnedBefore(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val update = new PropertyDetailsOwnedBefore()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsOwnedBefore(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsIsNewBuild" must {
      "respond with OK when saving the IsNewBuildFlag" in new Setup {
        val update = new PropertyDetailsIsNewBuild()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsIsNewBuild(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsIsNewBuild(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the IsNewBuildFlag" in new Setup {
        val update = new PropertyDetailsIsNewBuild()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsIsNewBuild(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsIsNewBuild(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsNewBuildDates" must {
      "respond with an Ok when successfully saving the new build dates" in new Setup {
        val update = new PropertyDetailsNewBuildDates()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuildDates(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewBuildDates(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the new build dates" in new Setup {
        val update = new PropertyDetailsNewBuildDates()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuildDates(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewBuildDates(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsNewBuildValue" must {
      "respond with OK when saving the New Build Value" in new Setup {
        val update = new PropertyDetailsNewBuildValue()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuildValue(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewBuildValue(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the New Build Value" in new Setup {
        val update = new PropertyDetailsNewBuildValue()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsNewBuildValue(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsNewBuildValue(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }

    "saveDraftPropertyDetailsValueAcquired" must {
      "respond with OK when saving the value on acquisition" in new Setup {
        val update = new PropertyDetailsValueOnAcquisition()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsValueAcquired(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsValueAcquired(testAccountRef, id = "1").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the Value acquired" in new Setup {
        val update = new PropertyDetailsValueOnAcquisition()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsValueAcquired(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsValueAcquired(testAccountRef, id = "1").apply(fakeRequest)

        status(result) must be(BAD_REQUEST)
      }
    }


    "saveDraftPropertyDetailsDatesAcquired" must {
      "respond with OK when saving the dates which the property was acquired" in new Setup {
        val update = new PropertyDetailsDateOfAcquisition()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsDatesAcquired(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsDatesAcquired(testAccountRef, id = "1").apply(fakeRequest)

        status(result) must be(OK)
      }

      "respond with a BAD REQUEST when failing to save the dates the property was acquired" in new Setup {
        val update = new PropertyDetailsDateOfAcquisition()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsDatesAcquired(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsDatesAcquired(testAccountRef, id = "1").apply(fakeRequest)

        status(result) must be(BAD_REQUEST)
      }
    }


    "saveDraftPropertyDetailsProfessionallyValued" must {
      "respond with OK and a list of cached Property Details if this all works" in new Setup {
        val update = new PropertyDetailsProfessionallyValued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(Some(testPropertyDetails)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsProfessionallyValued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(OK)

      }

      "respond with BAD_REQUEST and if this failed" in new Setup {
        val update = new PropertyDetailsProfessionallyValued()
        when(mockPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(ArgumentMatchers.eq(testAccountRef),
          ArgumentMatchers.eq("1"), ArgumentMatchers.eq(update))(using ArgumentMatchers.any())).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(update))
        val result = controller.saveDraftPropertyDetailsProfessionallyValued(testAccountRef, "1").apply(fakeRequest)
        status(result) must be(BAD_REQUEST)
      }
    }
  }
}
