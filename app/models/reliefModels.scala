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

package models

import java.time.{ZonedDateTime, ZoneId, LocalDate}
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class TaxAvoidance(
                         rentalBusinessScheme: Option[String] = None,
                         rentalBusinessSchemePromoter: Option[String] = None,
                         openToPublicScheme: Option[String] = None,
                         openToPublicSchemePromoter: Option[String] = None,
                         propertyDeveloperScheme: Option[String] = None,
                         propertyDeveloperSchemePromoter: Option[String] = None,
                         propertyTradingScheme: Option[String] = None,
                         propertyTradingSchemePromoter: Option[String] = None,
                         lendingScheme: Option[String] = None,
                         lendingSchemePromoter: Option[String] = None,
                         employeeOccupationScheme: Option[String] = None,
                         employeeOccupationSchemePromoter: Option[String] = None,
                         farmHousesScheme: Option[String] = None,
                         farmHousesSchemePromoter: Option[String] = None,
                         socialHousingScheme: Option[String] = None,
                         socialHousingSchemePromoter: Option[String] = None,
                         equityReleaseScheme: Option[String] = None,
                         equityReleaseSchemePromoter: Option[String] = None
                       )

object TaxAvoidance {
  implicit val formats = Json.format[TaxAvoidance]
}

case class Reliefs( periodKey: Int,
                    rentalBusiness: Boolean = false,
                    rentalBusinessDate: Option[LocalDate] = None,
                    openToPublic: Boolean = false,
                    openToPublicDate: Option[LocalDate] = None,
                    propertyDeveloper: Boolean = false,
                    propertyDeveloperDate: Option[LocalDate] = None,
                    propertyTrading: Boolean = false,
                    propertyTradingDate: Option[LocalDate] = None,
                    lending: Boolean = false,
                    lendingDate: Option[LocalDate] = None,
                    employeeOccupation: Boolean = false,
                    employeeOccupationDate: Option[LocalDate] = None,
                    farmHouses: Boolean = false,
                    farmHousesDate: Option[LocalDate] = None,
                    socialHousing: Boolean = false,
                    socialHousingDate: Option[LocalDate] = None,
                    equityRelease: Boolean = false,
                    equityReleaseDate: Option[LocalDate] = None,
                    isAvoidanceScheme: Option[Boolean] = None
                  )

object Reliefs {
  import play.api.libs.json.Reads._
  import play.api.libs.json.Writes._

  implicit val formats: OFormat[Reliefs] = {
    Json.format[Reliefs]
  }
  val mongoFormats: OFormat[Reliefs] = {
    Json.format[Reliefs]
  }
}

case class ReliefsTaxAvoidance(atedRefNo: String,
                               periodKey: Int,
                               reliefs: Reliefs,
                               taxAvoidance: TaxAvoidance,
                               periodStartDate: LocalDate,
                               periodEndDate: LocalDate,
                               timeStamp: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC")))

object ReliefsTaxAvoidance {

  import play.api.libs.json.Reads._
  import play.api.libs.json.Writes._

  val (reliefTaxAvoidanceReads, reliefTaxAvoidanceWrites) = {
    val reliefReads: Reads[ReliefsTaxAvoidance] = (
      (JsPath \ "atedRefNo").read[String] and
        (JsPath \ "periodKey").read[Int] and
        (JsPath \ "reliefs").read[Reliefs] and
        (JsPath \ "taxAvoidance").read[TaxAvoidance] and
        (JsPath \ "periodStartDate").read[LocalDate] and
        (JsPath \ "periodEndDate").read[LocalDate]
      )((atedRefNo, periodKey, reliefs, taxAvoidance, periodStartDate, periodEndDate) =>
      ReliefsTaxAvoidance(atedRefNo = atedRefNo, periodKey = periodKey, reliefs = reliefs, taxAvoidance = taxAvoidance,
        periodStartDate = periodStartDate, periodEndDate = periodEndDate))

    val reliefWrites: OWrites[ReliefsTaxAvoidance] = (
      (JsPath \ "atedRefNo").write[String] and
        (JsPath \ "periodKey").write[Int] and
        (JsPath \ "reliefs").write[Reliefs] and
        (JsPath \ "taxAvoidance").write[TaxAvoidance] and
        (JsPath \ "periodStartDate").write[LocalDate] and
        (JsPath \ "periodEndDate").write[LocalDate] and
        (JsPath \ "timeStamp").write[ZonedDateTime]
      )(reliefsTaxAvoidance => (reliefsTaxAvoidance.atedRefNo,reliefsTaxAvoidance.periodKey, reliefsTaxAvoidance.reliefs,
      reliefsTaxAvoidance.taxAvoidance, reliefsTaxAvoidance.periodStartDate, reliefsTaxAvoidance.periodEndDate, reliefsTaxAvoidance.timeStamp))

    (reliefReads, reliefWrites)
  }

  implicit val formats: OFormat[ReliefsTaxAvoidance] = OFormat(reliefTaxAvoidanceReads, reliefTaxAvoidanceWrites)

  val (mongoReads, mongoWrites) = {
    import mongo.MongoDateTimeFormats.Implicits._

    val mongoReliefReads: Reads[ReliefsTaxAvoidance] = (
      (JsPath \ "atedRefNo").read[String] and
        (JsPath \ "periodKey").read[Int] and
        (JsPath \ "reliefs").read[Reliefs](Reliefs.mongoFormats) and
        (JsPath \ "taxAvoidance").read[TaxAvoidance] and
        (JsPath \ "periodStartDate").read[LocalDate] and
        (JsPath \ "periodEndDate").read[LocalDate]
      )((atedRefNo, periodKey, reliefs, taxAvoidance, periodStartDate, periodEndDate) =>
      ReliefsTaxAvoidance(atedRefNo = atedRefNo, periodKey = periodKey, reliefs = reliefs, taxAvoidance = taxAvoidance,
        periodStartDate = periodStartDate, periodEndDate = periodEndDate))

    val mongoReliefWrites: OWrites[ReliefsTaxAvoidance] = (
      (JsPath \ "atedRefNo").write[String] and
        (JsPath \ "periodKey").write[Int] and
        (JsPath \ "reliefs").write[Reliefs](Reliefs.mongoFormats) and
        (JsPath \ "taxAvoidance").write[TaxAvoidance] and
        (JsPath \ "periodStartDate").write[LocalDate] and
        (JsPath \ "periodEndDate").write[LocalDate] and
        (JsPath \ "timeStamp").write[ZonedDateTime]
      )(reliefsTaxAvoidance => (reliefsTaxAvoidance.atedRefNo,reliefsTaxAvoidance.periodKey, reliefsTaxAvoidance.reliefs,
      reliefsTaxAvoidance.taxAvoidance, reliefsTaxAvoidance.periodStartDate, reliefsTaxAvoidance.periodEndDate, reliefsTaxAvoidance.timeStamp))

    (mongoReliefReads, mongoReliefWrites)
  }

  val mongoFormats: OFormat[ReliefsTaxAvoidance] = OFormat(mongoReads, mongoWrites)
}
