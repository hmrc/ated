/*
 * Copyright 2022 HM Revenue & Customs
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

import builders.{AuthFunctionalityHelper, ReliefBuilder}
import connectors.{EmailConnector, EmailSent, EtmpReturnsConnector}
import models.{Reliefs, TaxAvoidance}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import repository.{ReliefCached, ReliefDeleted, ReliefsMongoRepository}
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class ReliefsServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with AuthFunctionalityHelper {

  val mockReliefsCache = mock[ReliefsMongoRepository]
  val mockEtmpConnector = mock[EtmpReturnsConnector]
  val mockAuthConnector = mock[AuthConnector]
  val mockSubscriptionDataService = mock[SubscriptionDataService]
  val mockEmailConnector = mock[EmailConnector]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait Setup {
    class TestReliefsService extends ReliefsService {
      override val reliefsCache = mockReliefsCache
      override val etmpConnector = mockEtmpConnector
      override val authConnector = mockAuthConnector
      override val subscriptionDataService = mockSubscriptionDataService
      override val emailConnector = mockEmailConnector
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    }

    val testReliefsService = new TestReliefsService()
  }

  val accountRef = "ATED-123123"
  val periodKey = 2015
  val successResponseJson = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678","emailAddress":"test@mail.com"}}]}""")

  override def beforeEach = {
    reset(mockReliefsCache)
    reset(mockEtmpConnector)
    reset(mockAuthConnector)
    reset(mockEmailConnector)
    reset(mockSubscriptionDataService)
  }

  "ReliefsService" must {

    "saveDraftReliefs when we are passed some Reliefs" in new Setup {
      val testReliefs = ReliefBuilder.reliefTaxAvoidance(accountRef, periodKey)

      when(mockReliefsCache.cacheRelief(ArgumentMatchers.any())).thenReturn(Future.successful(ReliefCached))
      when(mockReliefsCache.fetchReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(testReliefs)))
      val result = testReliefsService.saveDraftReliefs(accountRef, testReliefs)

      await(result) must be(Seq(testReliefs))
    }

    "fetch the cached Reliefs when we have some" in new Setup {
      val testReliefs = ReliefBuilder.reliefTaxAvoidance(accountRef, periodKey)

      when(mockReliefsCache.fetchReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(testReliefs)))
      val result = testReliefsService.retrieveDraftReliefs(accountRef)

      await(result) must be(Seq(testReliefs))
    }

    "fetch an empty list when we have no cached Reliefs" in new Setup {

      when(mockReliefsCache.fetchReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(Seq()))
      val result = testReliefsService.retrieveDraftReliefs(accountRef)

      await(result) must be(Seq())
    }

    "submit cached Reliefs" must {

      "work even if we have no reliefs found" in new Setup {
        implicit val hc = new HeaderCarrier()

        when(mockReliefsCache.fetchReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(Seq()))
        when(mockEtmpConnector.submitReturns(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, "")))
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.successful(Some("Name")))

        when(mockReliefsCache.deleteReliefs(ArgumentMatchers.anyString())).thenReturn(Future.successful(ReliefDeleted))
        mockRetrievingNoAuthRef

        when(mockSubscriptionDataService.retrieveSubscriptionData(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))

        val result = testReliefsService.submitAndDeleteDraftReliefs("accountRef", periodKey)
        await(result).status must be(NOT_FOUND)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
      }

      "submit cached Reliefs and delete them if this submit works" in new Setup {
        implicit val hc = HeaderCarrier()

        type Retrieval = Option[Name]
        val testEnrolments: Set[Enrolment] = Set(Enrolment("HMRC-ATED-ORG", Seq(EnrolmentIdentifier("AgentRefNumber", "XN1200000100001")), "activated"))
        val name = Name(Some("gary"),Some("bloggs"))
        val enrolmentsWithName: Retrieval = Some(name)

        val reliefs = new Reliefs(periodKey = periodKey, rentalBusiness = true,
          openToPublic = true,
          propertyDeveloper = true,
          propertyTrading = true,
          lending = true,
          employeeOccupation = true,
          farmHouses = true,
          socialHousing = true)

        val taxAvoidance = new TaxAvoidance(rentalBusinessScheme = Some("Scheme123"),
            socialHousingScheme = Some("Scheme789"))

        val reliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(accountRef, periodKey, reliefs, taxAvoidance)
        when(mockReliefsCache.fetchReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(reliefsTaxAvoidance)))
        when(mockReliefsCache.cacheRelief(ArgumentMatchers.any())).thenReturn(Future.successful(ReliefCached))
        when(mockAuthConnector.authorise[Any](any(), any())(any(), any())).thenReturn(Future.successful(Enrolments(testEnrolments)), Future.successful(enrolmentsWithName))
        when(mockSubscriptionDataService.retrieveSubscriptionData(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())) thenReturn Future.successful(EmailSent)
        when(mockReliefsCache.deleteDraftReliefByYear(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(ReliefDeleted))

        val submitSuccess = Json.parse( """{"status" : "OK", "processingDate" :  "2014-12-17T09:30:47Z", "formBundleNumber" : "123456789012"}""")
        when(mockEtmpConnector.submitReturns(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, submitSuccess, Map.empty[String, Seq[String]])))
        val result = testReliefsService.submitAndDeleteDraftReliefs("accountRef", periodKey)
        await(result).status must be(OK)
        verify(mockEmailConnector, times(1)).sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
      }
    }

    "delete reliefs for a particular user" in new Setup {
      val reliefs = new Reliefs(periodKey = periodKey, rentalBusiness = true,
        openToPublic = true,
        propertyDeveloper = true,
        propertyTrading = true,
        lending = true,
        employeeOccupation = true,
        farmHouses = true,
        socialHousing = true)

      val taxAvoidance = new TaxAvoidance(rentalBusinessScheme = Some("Scheme123"),
        socialHousingScheme = Some("Scheme789"))

      val reliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(accountRef, periodKey, reliefs, taxAvoidance)
      when(mockReliefsCache.fetchReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(reliefsTaxAvoidance)))
      when(mockReliefsCache.cacheRelief(ArgumentMatchers.any())).thenReturn(Future.successful(ReliefCached))
      mockRetrievingNoAuthRef

      val fetchResult = testReliefsService.retrieveDraftReliefs("accountRef")

      await(fetchResult) must be(Seq(reliefsTaxAvoidance))

      when(mockReliefsCache.deleteReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(ReliefDeleted))
      when(mockReliefsCache.fetchReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(Seq()))
      val result = testReliefsService.deleteAllDraftReliefs("accountRef")

      await(result) must be(Seq())

    }

    "delete all relief drafts for an user for that year" in new Setup {
      val reliefs = new Reliefs(periodKey = periodKey, rentalBusiness = true,
        openToPublic = true,
        propertyDeveloper = true,
        propertyTrading = true,
        lending = true,
        employeeOccupation = true,
        farmHouses = true,
        socialHousing = true)

      val taxAvoidance = new TaxAvoidance(rentalBusinessScheme = Some("Scheme123"),
        socialHousingScheme = Some("Scheme789"))

      val reliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(accountRef, periodKey, reliefs, taxAvoidance)
      when(mockReliefsCache.fetchReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(reliefsTaxAvoidance)))
      when(mockReliefsCache.cacheRelief(ArgumentMatchers.any())).thenReturn(Future.successful(ReliefCached))
      mockRetrievingNoAuthRef

      val fetchResult = testReliefsService.retrieveDraftReliefs("accountRef")

      await(fetchResult) must be(Seq(reliefsTaxAvoidance))

      when(mockReliefsCache.deleteDraftReliefByYear(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(ReliefDeleted))
      when(mockReliefsCache.fetchReliefs(ArgumentMatchers.any())).thenReturn(Future.successful(Seq()))
      val result = testReliefsService.deleteAllDraftReliefByYear("accountRef", 2017)

      await(result) must be(Seq())
    }

  }
}
