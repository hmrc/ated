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

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.crypto.{Encrypter, Decrypter}

case class PropertyDetails(atedRefNo: String,
                           id: String,
                           periodKey: Int,
                           addressProperty: PropertyDetailsAddress,
                           title: Option[PropertyDetailsTitle] = None,
                           value: Option[PropertyDetailsValue] = None,
                           period: Option[PropertyDetailsPeriod] = None,
                           calculated: Option[PropertyDetailsCalculated] = None,
                           formBundleReturn: Option[FormBundleReturn] = None,
                           bankDetails: Option[BankDetailsModel] = None,
                           timeStamp: DateTime = DateTime.now(DateTimeZone.UTC))

object PropertyDetails {

  def formats(implicit crypto: Encrypter with Decrypter): OFormat[PropertyDetails] = {
    implicit val bankDetailsModelFormat: Format[BankDetailsModel] = BankDetailsModel.format

    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits.jotDateTimeFormat

    Json.format[PropertyDetails]
  }
}
