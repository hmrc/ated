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

package mongo.playjson

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json

case class CreationAndLastModifiedDetail(
    createdAt: DateTime   = DateTime.now.withZone(DateTimeZone.UTC)
  , lastUpdated: DateTime = DateTime.now.withZone(DateTimeZone.UTC)
  ) {

  def updated(updatedTime: DateTime) =
    copy(lastUpdated = updatedTime)
}

object CreationAndLastModifiedDetail {
  implicit val formats = {
    implicit val dtf = ReactiveMongoFormats.dateTimeFormats
    Json.format[CreationAndLastModifiedDetail]
  }

  def withTime(time: DateTime) =
    new CreationAndLastModifiedDetail(
      createdAt   = time
    , lastUpdated = time
    )
}