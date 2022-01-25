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

import play.api.libs.json._
import org.bson.types.ObjectId
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats.Implicits._
import scala.collection.Set
import scala.language.implicitConversions

case class Id(id: String)

object Id {

  import play.api.libs.json.{Format, Reads, Writes}

  implicit def stringToId(s: String): Id = new Id(s)

  private val idWrite: Writes[Id] = (value: Id) => JsString(value.id)

  private val idRead: Reads[Id] = {
    case v: JsString => v.validate[String].map(Id.apply)
    case noParsed => throw new Exception(s"Could not read Json value of 'id' in $noParsed")
  }

  implicit val idFormats: Format[Id] = Format(idRead, idWrite)
}


case class Cache(_id: Id, data: Option[JsValue] = None,
                 modifiedDetails: CreationAndLastModifiedDetail = CreationAndLastModifiedDetail(),
                 atomicId: Option[ObjectId] = None) extends Cacheable {
}

object Cache {
  final val DataAttributeName = "data"

  implicit val cacheFormat: OFormat[Cache] = Json.format[Cache]

  val mongoFormats: Format[Cache] = cacheFormat
}


trait Cacheable {

  val _id: Id
  val data: Option[JsValue]
  val modifiedDetails: CreationAndLastModifiedDetail

  def dataKeys(): Option[Set[String]] = data.map(js => js.as[JsObject].keys)
}
