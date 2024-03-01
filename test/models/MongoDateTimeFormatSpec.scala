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

package models.mongo

import java.time.{LocalDate, ZonedDateTime, ZoneId, Instant}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsNumber, JsObject, JsResult, JsString, JsSuccess, Json}

class MongoDateTimeFormatSpec extends PlaySpec with GuiceOneServerPerSuite {
  "MongoDateTimeFormat" should {
    "correctly parse an old string timestamp" in {
      val reads: JsResult[ZonedDateTime] = MongoDateTimeFormats.Implicits.mdDateTimeFormat.reads(JsString("1970-01-20T00:59:41.376Z"))

      reads mustBe JsSuccess(ZonedDateTime.ofInstant(Instant.ofEpochMilli(1645181376L), ZoneId.of("Z")))
    }

    "correctly parse an old int timestamp" in {
      val reads: JsResult[ZonedDateTime] = MongoDateTimeFormats.Implicits.mdDateTimeFormat.reads(JsNumber(1645181376L))

      reads mustBe JsSuccess(ZonedDateTime.ofInstant(Instant.ofEpochMilli(1645181376L), ZoneId.of("UTC")))
    }

    "correctly parse new format dates" in {
      val jsObject: JsObject = Json.obj(
        "$date" -> Json.obj(
          "$numberLong" -> "1645181376"
        )
      )

      val reads: JsResult[ZonedDateTime] = MongoDateTimeFormats.Implicits.mdDateTimeFormat.reads(jsObject)

      reads mustBe JsSuccess(ZonedDateTime.ofInstant(Instant.ofEpochMilli(1645181376L), ZoneId.of("UTC")))
    }

    "correctly Read LocalDate in either format" in {
      val jsObject: JsObject = Json.obj(
        "$date" -> Json.obj(
          "$numberLong" -> "1645181376"
        )
      )

      val altJsObject: JsString = JsString("1970-01-20")

      val reads: JsResult[LocalDate] = MongoDateTimeFormats.Implicits.mdLocalDateFormat.reads(jsObject)

      val altReads: JsResult[LocalDate] = MongoDateTimeFormats.Implicits.mdLocalDateFormat.reads(altJsObject)

      reads mustBe JsSuccess(LocalDate.ofInstant(Instant.ofEpochMilli(1645181376L), ZoneId.of("UTC")))

      altReads mustBe JsSuccess(LocalDate.ofInstant(Instant.ofEpochMilli(1645181376L), ZoneId.of("UTC")))
    }
  }
}
