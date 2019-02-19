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
import play.api.libs.json.Json

case class EditLiabilityReturnsRequest(oldFormBundleNumber: String,
                                       mode: String,
                                       periodKey: String,
                                       propertyDetails: EtmpPropertyDetails,
                                       dateOfAcquisition: Option[LocalDate] = None,
                                       valueAtAcquisition: Option[BigDecimal] = None,
                                       dateOfValuation: LocalDate,
                                       taxAvoidanceScheme: Option[String] = None,
                                       taxAvoidancePromoterReference: Option[String] = None,
                                       localAuthorityCode: Option[String] = None,
                                       professionalValuation: Boolean,
                                       ninetyDayRuleApplies: Boolean,
                                       lineItem: Seq[EtmpLineItems],
                                       bankDetails: Option[EtmpBankDetails] = None)

object EditLiabilityReturnsRequest {
  implicit val formats = Json.format[EditLiabilityReturnsRequest]
}

case class EditLiabilityReturnsRequestModel(acknowledgmentReference: String,
                                            agentReferenceNumber: Option[String] = None,
                                            liabilityReturn: Seq[EditLiabilityReturnsRequest])

object EditLiabilityReturnsRequestModel {
  implicit val formats = Json.format[EditLiabilityReturnsRequestModel]
}
