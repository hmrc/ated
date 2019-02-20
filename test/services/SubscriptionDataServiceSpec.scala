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

package services

import connectors.{AuthConnector, EtmpDetailsConnector}
import models._
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._

import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

class SubscriptionDataServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockEtmpConnector = mock[EtmpDetailsConnector]
  val mockAuthConnector = mock[AuthConnector]

  object TestSubscriptionDataService extends SubscriptionDataService {
    override val etmpConnector = mockEtmpConnector
    val authConnector = mockAuthConnector
  }

  val accountRef = "ATED-123123"
  val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

  override def beforeEach = {
    reset(mockEtmpConnector)
    reset(mockAuthConnector)
  }

  "SubscriptionDataService" must {

    "use the correct Etmpconnector" in {
      SubscriptionDataService.etmpConnector must be(EtmpDetailsConnector)
    }

    "retrieve Subscription Data" in {
      when(mockEtmpConnector.getSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
      val result = TestSubscriptionDataService.retrieveSubscriptionData(accountRef)
      val response = await(result)
      response.status must be(OK)
      response.json must be(successResponse)
    }

    "save account details" must {
      val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

      "work if we have valid data" in {
        val addressDetails = AddressDetails("Correspondence", "line1", "line2", None, None, Some("postCode"), "GB")
        val updatedData = UpdateSubscriptionDataRequest(true, ChangeIndicators(), List(Address(addressDetails = addressDetails)))
        implicit val hc = HeaderCarrier()

        when(mockEtmpConnector.updateSubscriptionData(Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
        val result = TestSubscriptionDataService.updateSubscriptionData(accountRef, updatedData)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }
    }

    "save registration details" must {
      val successResponse = Json.parse( """{"processingDate": "2001-12-17T09:30:47Z"}""")

      "work if we have valid data" in {
        val registeredDetails = RegisteredAddressDetails(addressLine1 = "", addressLine2 = "", countryCode = "GB")
        val updatedData = new UpdateRegistrationDetailsRequest(None, false, None, Some(Organisation("testName")), registeredDetails, ContactDetails(), false, false)

        when(mockEtmpConnector.updateRegistrationDetails(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponse))))
        val result = TestSubscriptionDataService.updateRegistrationDetails(accountRef, "safeId", updatedData)
        val response = await(result)
        response.status must be(OK)
        response.json must be(successResponse)
      }
    }
  }
}
