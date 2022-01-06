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

package models

import org.joda.time.DateTime
import play.api.libs.json.{Format, Json, Reads, Writes}


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
    implicit val formats = Json.format[EditLiabilityReturnsResponse]
}

case class EditLiabilityReturnsResponseModel(processingDate: DateTime,
                                             liabilityReturnResponse: Seq[EditLiabilityReturnsResponse],
                                             accountBalance: BigDecimal)

object EditLiabilityReturnsResponseModel {
  import play.api.libs.json.JodaReads.jodaDateReads
  import play.api.libs.json.JodaWrites.jodaDateWrites

  val reads: Reads[DateTime] = jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val writes: Writes[DateTime] = jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")

  implicit val datetimeFormat: Format[DateTime] = Format(reads, writes)

  implicit val formats = Json.format[EditLiabilityReturnsResponseModel]
}
