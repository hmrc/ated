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

package utils

import java.util.UUID

import builders.ReliefBuilder
import models.{Reliefs, TaxAvoidance}
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

import scala.collection.Seq
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId

class ReliefUtilsSpec extends PlaySpec with ReliefConstants {

  val atedRefNo = "atedref1234"
  val periodKey = 2015
  "convertToSubmitReliefsRequest" must {
    "Return None if we have no reliefs object" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
      val reliefsRequest = ReliefUtils.convertToSubmitReturnsRequest("ATED-123", None)

      reliefsRequest.isDefined must be(false)
    }

    "Return None if we have no reliefs selected" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
      val reliefsRequest = ReliefUtils.convertToSubmitReturnsRequest("ATED-123", Some(reliefs))

      reliefsRequest.isDefined must be(false)
    }

    "Return SubmitReliefsRequest if we have rental Business selected" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val startDate = new LocalDate("2015-10-21")
      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey,
        new Reliefs(periodKey = periodKey, rentalBusiness = true, rentalBusinessDate = Some(startDate))
      )
      val reliefsRequest = ReliefUtils.convertToSubmitReturnsRequest("ATED-123", Some(reliefs))

      reliefsRequest.isDefined must be(true)
      reliefsRequest.get.acknowledgementReference.isEmpty must be(false)
      reliefsRequest.get.reliefReturns.size must be(1)

      val relief = reliefsRequest.get.reliefReturns.get(0)

      relief.periodKey must be(reliefs.reliefs.periodKey.toString)
      relief.reliefStartDate must be(startDate)
      relief.reliefEndDate must be(reliefs.periodEndDate)
      relief.taxAvoidanceScheme must be(None)
      relief.reliefDescription must be(ReliefUtils.RentalBusinessDesc)

    }

    "Return SubmitReliefsRequest if we have Farm house selected" in {
      implicit val hc = new HeaderCarrier()

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, new Reliefs(periodKey = periodKey, farmHouses = true))
      val reliefsRequest = ReliefUtils.convertToSubmitReturnsRequest("ATED-123", Some(reliefs))

      reliefsRequest.isDefined must be(true)
      reliefsRequest.get.acknowledgementReference.isEmpty must be(false)
      reliefsRequest.get.reliefReturns.size must be(1)

      val relief = reliefsRequest.get.reliefReturns.get(0)

      relief.periodKey must be(reliefs.reliefs.periodKey.toString)
      relief.reliefStartDate must be(reliefs.periodStartDate)
      relief.reliefEndDate must be(reliefs.periodEndDate)

      relief.taxAvoidanceScheme must be(None)
      relief.reliefDescription must be(ReliefUtils.FarmHouseDesc)

    }

    "Return SubmitReliefsRequest if we have all reliefs selected" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = new Reliefs(periodKey = periodKey, rentalBusiness = true,
        openToPublic = true,
        propertyDeveloper = true,
        propertyTrading = true,
        lending = true,
        employeeOccupation = true,
        farmHouses = true,
        socialHousing = true,
        equityRelease = true)

      val taxAvoidance = new TaxAvoidance(rentalBusinessScheme = Some("Scheme123"),
        socialHousingScheme = Some("Scheme789"))

      val reliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, reliefs, taxAvoidance)
      val reliefsRequest = ReliefUtils.convertToSubmitReturnsRequest("ATED-123", Some(reliefsTaxAvoidance))

      reliefsRequest.isDefined must be(true)
      reliefsRequest.get.acknowledgementReference.isEmpty must be(false)
      reliefsRequest.get.reliefReturns.get.size must be(9)
      reliefsRequest.get.reliefReturns.get.head.reliefDescription must be(RentalBusinessDesc)
      reliefsRequest.get.reliefReturns.get.head.taxAvoidanceScheme.isDefined must be(true)
      reliefsRequest.get.reliefReturns.get.head.taxAvoidanceScheme must be(Some("Scheme123"))
      reliefsRequest.get.reliefReturns.get(1).taxAvoidanceScheme.isDefined must be(false)
      reliefsRequest.get.reliefReturns.get(2).taxAvoidanceScheme.isDefined must be(false)
      reliefsRequest.get.reliefReturns.get(7).reliefDescription must be(SocialHouseDesc)
      reliefsRequest.get.reliefReturns.get(7).taxAvoidanceScheme must be(Some("Scheme789"))

    }
  }

  "convertDraftReliefsToDraftDescription" must {
    "Return empty Seq, if we have no reliefs object" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
      val reliefsRequest = ReliefUtils.convertDraftReliefsToDraftDescription(reliefs)

      reliefsRequest must be(Seq())
    }

    "Return Rental Business, if rental business is true" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, Reliefs(periodKey, rentalBusiness = true))
      val reliefsRequest = ReliefUtils.convertDraftReliefsToDraftDescription(reliefs)

      reliefsRequest must be(Seq("Rental businesses"))
    }
    "Return Farmhouses, if farmHouses is true" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, Reliefs(periodKey, farmHouses = true))
      val reliefsRequest = ReliefUtils.convertDraftReliefsToDraftDescription(reliefs)

      reliefsRequest must be(Seq("Farmhouses"))
    }

    "Return Property developers, if propertyDeveloper is true" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, Reliefs(periodKey, propertyDeveloper = true))
      val reliefsRequest = ReliefUtils.convertDraftReliefsToDraftDescription(reliefs)

      reliefsRequest must be(Seq("Property developers"))
    }

    "Return Employee occupation, if employeeOccupation is true" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, Reliefs(periodKey, employeeOccupation = true))
      val reliefsRequest = ReliefUtils.convertDraftReliefsToDraftDescription(reliefs)

      reliefsRequest must be(Seq("Employee occupation"))
    }

    "Return Property trading, if propertyTrading is true" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, Reliefs(periodKey, propertyTrading = true))
      val reliefsRequest = ReliefUtils.convertDraftReliefsToDraftDescription(reliefs)

      reliefsRequest must be(Seq("Property trading"))
    }

    "Return Seq(Lending, Open to the public, Social housing), if socialHousing, openToPublic, lending are true" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

      val reliefs = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, Reliefs(periodKey, socialHousing = true, openToPublic = true, lending = true, equityRelease = true))
      val reliefsRequest = ReliefUtils.convertDraftReliefsToDraftDescription(reliefs)

      reliefsRequest must be(Seq("Lending", "Open to the public", "Social housing", "Equity Release"))
    }

  }
}
