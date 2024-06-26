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

import java.time.ZonedDateTime
import play.api.libs.json.{Json, OFormat}

case class EditLiabilityReturnsResponse(mode: String,
                                        oldFormBundleNumber: String,
                                        formBundleNumber: Option[String],
                                        liabilityAmount: BigDecimal,
                                        amountDueOrRefund: BigDecimal,
                                        paymentReference: Option[String])

object EditLiabilityReturnsResponse {

 /* implicit val reads: Reads[EditLiabilityReturnsResponse] = (
    (JsPath \ "mode").read[String] and
      (JsPath \ "oldFormBundleNumber").read[String] and
      (JsPath \ "formBundleNumber").readNullable[String] and
      //because ETMP returns padded with spaces
      (JsPath \ "liabilityAmount").read[String].map(a => BigDecimal(a.trim.replaceAll("\\s+", ""))) and
      //because ETMP returns padded with spaces
      (JsPath \ "amountDueOrRefund").read[String].map(a => BigDecimal(a.trim.replaceAll("\\s+", ""))) and
      (JsPath \ "paymentReference").readNullable[String]
    ) (EditLiabilityReturnsResponse.apply _)

  implicit val writes = Json.writes[EditLiabilityReturnsResponse]*/
    implicit val formats: OFormat[EditLiabilityReturnsResponse] = Json.format[EditLiabilityReturnsResponse]
}

case class EditLiabilityReturnsResponseModel(processingDate: ZonedDateTime,
                                             liabilityReturnResponse: Seq[EditLiabilityReturnsResponse],
                                             accountBalance: BigDecimal)

object EditLiabilityReturnsResponseModel {
  implicit val formats: OFormat[EditLiabilityReturnsResponseModel] = Json.format[EditLiabilityReturnsResponseModel]
}
