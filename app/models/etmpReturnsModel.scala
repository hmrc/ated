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

package models

import org.joda.time.LocalDate
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads}

case class EtmpAddress(addressLine1: String,
                       addressLine2: String,
                       addressLine3: Option[String] = None,
                       addressLine4: Option[String] = None,
                       countryCode: String,
                       postalCode: Option[String] = None)

object EtmpAddress {
  implicit val formats = Json.format[EtmpAddress]
}


case class EtmpReliefReturns(reliefDescription: String,
                             reliefStartDate: LocalDate,
                             reliefEndDate: LocalDate,
                             periodKey: String,
                             taxAvoidanceScheme: Option[String] = None,
                             taxAvoidancePromoterReference: Option[String] = None)

object EtmpReliefReturns {
  implicit val formats = Json.format[EtmpReliefReturns]
}


case class EtmpPropertyDetails(titleNumber: Option[String] = None,
                               address: EtmpAddress,
                               additionalDetails: Option[String] = None)

object EtmpPropertyDetails {
  implicit val formats = Json.format[EtmpPropertyDetails]
}

case class EtmpLineItems(propertyValue: BigDecimal,
                         dateFrom: LocalDate,
                         dateTo: LocalDate,
                         `type`: String,
                         reliefDescription: Option[String] = None)

object EtmpLineItems {
  implicit val formats = Json.format[EtmpLineItems]
}

case class EtmpLiabilityReturns(mode: String,
                                propertyKey: String,
                                periodKey: String,
                                propertyDetails: Option[EtmpPropertyDetails] = None,
                                dateOfAcquisition: Option[LocalDate] = None,
                                valueAtAcquisition: Option[BigDecimal] = None,
                                dateOfValuation: LocalDate,
                                taxAvoidanceScheme: Option[String] = None,
                                taxAvoidancePromoterReference: Option[String] = None,
                                localAuthorityCode: Option[String] = None,
                                professionalValuation: Boolean,
                                ninetyDayRuleApplies: Boolean,
                                lineItems: Seq[EtmpLineItems])

object EtmpLiabilityReturns {
  implicit val formats = Json.format[EtmpLiabilityReturns]
}


case class SubmitEtmpReturnsRequest(acknowledgementReference: String,
                                    agentReferenceNumber: Option[String] = None,
                                    reliefReturns: Option[Seq[EtmpReliefReturns]] = None,
                                    liabilityReturns: Option[Seq[EtmpLiabilityReturns]] = None)

object SubmitEtmpReturnsRequest {
  implicit val formats = Json.format[SubmitEtmpReturnsRequest]
}


case class EtmpReliefReturnResponse(reliefDescription: String, formBundleNumber: String)

object EtmpReliefReturnResponse {
  implicit val formats = Json.format[EtmpReliefReturnResponse]
}


case class EtmpLiabilityReturnResponse(
                                        mode: String,
                                        propertyKey: String,
                                        liabilityAmount: BigDecimal,
                                        paymentReference: Option[String],
                                        formBundleNumber: Option[String]
                                      )

object EtmpLiabilityReturnResponse {
  implicit val formats = Json.format[EtmpLiabilityReturnResponse]
}


case class SubmitEtmpReturnsResponse(processingDate: String,
                                     reliefReturnResponse: Option[Seq[EtmpReliefReturnResponse]] = None,
                                     liabilityReturnResponse: Option[Seq[EtmpLiabilityReturnResponse]] = None)

object SubmitEtmpReturnsResponse {
  implicit val formats = Json.format[SubmitEtmpReturnsResponse]
//  implicit val reads = Json.reads[SubmitEtmpReturnsResponse]
//  implicit val writes = Json.writes[SubmitEtmpReturnsResponse]
}
