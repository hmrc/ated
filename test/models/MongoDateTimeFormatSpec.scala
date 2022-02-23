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
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsObject, JsResult, JsString, JsSuccess, Json}

class MongoDateTimeFormatSpec extends PlaySpec with GuiceOneServerPerSuite {
  "MongoDateTimeFormat" should {
    "correctly parse an old string timestamp" in {
      val reads: JsResult[DateTime] = MongoDateTimeFormats.Implicits.mdDateTimeFormat.reads(JsString("1970-01-20T01:59:41.376"))

      reads mustBe JsSuccess(new DateTime(1645181376L))
    }

    "correctly parse new format dates" in {
      val jsObject: JsObject = Json.obj(
        "$date" -> Json.obj(
          "$numberLong" -> "1645181376"
        )
      )

      val reads: JsResult[DateTime] = MongoDateTimeFormats.Implicits.mdDateTimeFormat.reads(jsObject)

      reads mustBe JsSuccess(new DateTime(1645181376L))
    }
  }
}
