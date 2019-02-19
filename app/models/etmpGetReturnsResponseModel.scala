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

case class EtmpReturn(formBundleNumber: String,
                      dateOfSubmission: LocalDate,
                      dateFrom: LocalDate,
                      dateTo: LocalDate,
                      liabilityAmount: BigDecimal,
                      paymentReference: String,
                      changeAllowed: Boolean)

object EtmpReturn {

/*  implicit val reads: Reads[EtmpReturn] = (
    (JsPath \ "formBundleNumber").read[String] and
      (JsPath \ "dateOfSubmission").read[LocalDate] and
      (JsPath \ "dateFrom").read[LocalDate] and
      (JsPath \ "dateTo").read[LocalDate] and
      //because ETMP returns padded with spaces
      (JsPath \ "liabilityAmount").read[String].map(a => BigDecimal(a.trim.replaceAll("\\s+", ""))) and
      (JsPath \ "paymentReference").read[String] and
      (JsPath \ "changeAllowed").read[Boolean]
    ) (EtmpReturn.apply _)

  implicit val writes = Json.writes[EtmpReturn]*/
   implicit val formats = Json.format[EtmpReturn]
}

case class EtmpPropertySummary(contractObject: String,
                               titleNumber: Option[String] = None,
                               addressLine1: String,
                               addressLine2: String,
                               `return`: Seq[EtmpReturn])

object EtmpPropertySummary {
  implicit val formats = Json.format[EtmpPropertySummary]
}


case class EtmpLiabilityReturnSummary(propertySummary: Option[Seq[EtmpPropertySummary]] = None)

object EtmpLiabilityReturnSummary {
  implicit val formats = Json.format[EtmpLiabilityReturnSummary]
}

case class EtmpReliefReturnsSummary(formBundleNumber: String,
                                    dateOfSubmission: LocalDate,
                                    relief: String,
                                    reliefStartDate: LocalDate,
                                    reliefEndDate: LocalDate,
                                    arn: Option[String] = None,
                                    taxAvoidanceScheme: Option[String] = None,
                                    taxAvoidancePromoterReference: Option[String] = None)

object EtmpReliefReturnsSummary {
  implicit val formats = Json.format[EtmpReliefReturnsSummary]
}

case class EtmpReturnData(reliefReturnSummary: Option[Seq[EtmpReliefReturnsSummary]] = None,
                          liabilityReturnSummary: Option[Seq[EtmpLiabilityReturnSummary]] = None)

object EtmpReturnData {
  implicit val formats = Json.format[EtmpReturnData]
}

case class EtmpPeriodSummary(periodKey: String, returnData: EtmpReturnData)

object EtmpPeriodSummary {
  implicit val formats = Json.format[EtmpPeriodSummary]
}

case class EtmpGetReturnsResponse(
                                   safeId: String,
                                   organisationName: String,
                                   periodData: Seq[EtmpPeriodSummary],
                                   atedBalance: BigDecimal
                                 )

object EtmpGetReturnsResponse {

  implicit val reads: Reads[EtmpGetReturnsResponse] = (
    (JsPath \ "safeId").read[String] and
      (JsPath \ "organisationName").read[String] and
      (JsPath \ "periodData").read[Seq[EtmpPeriodSummary]] and
      //because ETMP returns padded with spaces
      (JsPath \ "atedBalance").read[String].map(a => BigDecimal(a.trim.replaceAll("\\s+", "")))
    ) (EtmpGetReturnsResponse.apply _)

  implicit val writes = Json.writes[EtmpGetReturnsResponse]

  //  implicit val formats = Json.format[EtmpGetReturnsResponse]
}
