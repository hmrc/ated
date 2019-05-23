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

import org.bson.codecs.configuration.CodecRegistries
import org.mongodb.scala.{MongoCollection, MongoDatabase}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import play.api.libs.json.Format
import scala.reflect.ClassTag

// $COVERAGE-OFF$

trait CollectionFactory {
  def collection[A : ClassTag](db: MongoDatabase, collectionName: String, format: Format[A]): MongoCollection[A] =
    db
      .getCollection[A](collectionName)
      .withCodecRegistry(CodecRegistries.fromRegistries(
          CodecRegistries.fromCodecs(Codecs.playFormatCodec(format))
        , DEFAULT_CODEC_REGISTRY
        ))

}

object CollectionFactory extends CollectionFactory

// $COVERAGE-ON$
