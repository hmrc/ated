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

import java.time.{ZoneId, ZonedDateTime}
import play.api.libs.json.{Json, OFormat}
import models.mongo.MongoDateTimeFormats.Implicits._

case class CreationAndLastModifiedDetail(
                                          createdAt: ZonedDateTime   = ZonedDateTime.now(ZoneId.of("UTC")),
                                          lastUpdated: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"))) {

  def updated(updatedTime: ZonedDateTime): CreationAndLastModifiedDetail = copy(
    lastUpdated = updatedTime
  )
}

object CreationAndLastModifiedDetail {
  implicit val formats: OFormat[CreationAndLastModifiedDetail] = Json.format[CreationAndLastModifiedDetail]

  def withTime(time: ZonedDateTime) = new CreationAndLastModifiedDetail(
    createdAt   = time,
    lastUpdated = time
  )
}
