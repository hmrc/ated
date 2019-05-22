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

package mongo.json

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, Json, JsError, JsPath, JsValue, JsResult, JsSuccess, Reads, Writes, __}
import org.mongodb.scala.bson.BsonObjectId
import scala.util.{Try, Success, Failure}

trait ReactiveMongoFormats {
  val dateTimeRead: Reads[DateTime] =
    (__ \ "$date").read[Long].map { dateTime =>
        new DateTime(dateTime, DateTimeZone.UTC)
      }

  val dateTimeWrite: Writes[DateTime] = new Writes[DateTime] {
    def writes(dateTime: DateTime): JsValue =
      Json.obj("$date" -> dateTime.getMillis)
  }

  val dateTimeFormats = Format(dateTimeRead, dateTimeWrite)

  val objectIdRead: Reads[BsonObjectId] = Reads[BsonObjectId] { json =>
    (json \ "$oid").validate[String].flatMap { str =>
      Try(BsonObjectId(str)) match {
        case Success(bsonId) => JsSuccess(bsonId)
        case Failure(err)    => JsError(__, s"Invalid BSON Object ID $json; ${err.getMessage}")
      }
    }
  }

  val objectIdWrite: Writes[BsonObjectId] = new Writes[BsonObjectId] {
    def writes(objectId: BsonObjectId): JsValue = Json.obj(
      "$oid" -> objectId.toString
    )
  }

  val objectIdFormats = Format(objectIdRead, objectIdWrite)

  def mongoEntity[A](baseFormat: Format[A]): Format[A] = {
    import JsonExtensions._
    val publicIdPath: JsPath  = __ \ '_id
    val privateIdPath: JsPath = __ \ 'id
    new Format[A] {
      def reads(json: JsValue): JsResult[A] = baseFormat.compose(copyKey(publicIdPath, privateIdPath)).reads(json)

      def writes(o: A): JsValue = baseFormat.transform(moveKey(privateIdPath, publicIdPath)).writes(o)
    }
  }
}

object ReactiveMongoFormats extends ReactiveMongoFormats