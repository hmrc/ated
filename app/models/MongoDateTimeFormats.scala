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

import org.joda.time
import org.joda.time._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats

trait MongoDateTimeFormats {
  final val tolerantDateTimeReads: Reads[DateTime] = (js: JsValue) =>
    (js \ "$date" \ "$numberLong").validate[String] match {
      case _ @ JsError(_) =>
        // Fall back to try and read date as string
        js.validate[Long] match {
          case _ @ JsError(_) =>
            js.validate[String] match {
              case _ @ JsSuccess(dt, pth) => JsSuccess(DateTime.parse(dt), pth)
              case err3 @ JsError(_) => err3
            }
          case _ @ JsSuccess(dt, pth) => JsSuccess(new DateTime(dt, DateTimeZone.UTC), pth)
        }
      case JsSuccess(dt, pth) => JsSuccess(new DateTime(dt.toLong, DateTimeZone.UTC), pth)
    }

  final val tolerantDateTimeFormat: Format[DateTime] =
    Format(tolerantDateTimeReads, MongoJodaFormats.dateTimeFormat)

  trait Implicits {
    implicit val mdLocalDateTimeFormat: Format[time.LocalDateTime] = MongoJodaFormats.localDateTimeFormat
    implicit val mdDateTimeFormat: Format[DateTime] = tolerantDateTimeFormat
  }

  object Implicits extends Implicits
}

object MongoDateTimeFormats extends MongoDateTimeFormats
