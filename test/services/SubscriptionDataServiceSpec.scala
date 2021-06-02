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

package services

import builders.AuthFunctionalityHelper
import connectors.EtmpDetailsConnector
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class SubscriptionDataServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with AuthFunctionalityHelper {

  val mockEtmpConnector = mock[EtmpDetailsConnector]
  val mockAuthConnector = mock[AuthConnector]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait Setup {
    class TestSubscriptionDataService extends SubscriptionDataService {
      override val etmpConnector = mockEtmpConnector
      val authConnector = mockAuthConnector
    }

    val testSubscriptionDataService = new TestSubscriptionDataService()
  }

  val accountRef = "ATED-123123"
  val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

  override def beforeEach = {
    reset(mockEtmpConnector)
    reset(mockAuthConnector)
  }

  "SubscriptionDataService" must {

    "retrieve Subscription Data" in new Setup {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      when(mockEtmpConnector.getSubscriptionData(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
      val result = testSubscriptionDataService.retrieveSubscriptionData(accountRef)
      val response = await(result)
      response.status must be(OK)
      response.json must be(successResponse)
    }

    "save account details" must {
      val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

      "work if we have valid data" in new Setup {
        val addressDetails = AddressDetails("Correspondence", "line1", "line2", None, None, Some("postCode"), "GB")
        val updatedData = UpdateSubscriptionDataRequest(true, ChangeIndicators(), List(Address(addressDetails = addressDetails)))
        implicit val hc = HeaderCarrier()

        when(mockEtmpConnector.updateSubscriptionData(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
        mockRetrievingNoAuthRef
        val result = testSubscriptionDataService.updateSubscriptionData(accountRef, updatedData)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }
    }

    "save registration details" must {
      val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

      "work if we have valid data" in new Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()
        val registeredDetails = RegisteredAddressDetails(addressLine1 = "", addressLine2 = "", countryCode = "GB")
        val updatedData = new UpdateRegistrationDetailsRequest(None, false, None, Some(Organisation("testName")), registeredDetails, ContactDetails(), false, false)

        when(mockEtmpConnector.updateRegistrationDetails(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
        val result = testSubscriptionDataService.updateRegistrationDetails(accountRef, "safeId", updatedData)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }
    }
  }
}
