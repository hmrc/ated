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

package mongo
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry, CodecRegistries}
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}
import org.mongodb.scala.bson.codecs.{Macros, DEFAULT_CODEC_REGISTRY}
import scala.reflect.ClassTag

object MongoCollection2 {
  def collection[A](collectionName: String, codecRegistry: CodecRegistry)(implicit ct: ClassTag[A]): MongoCollection[A] =
    MongoClient(uri = "mongodb://localhost:27017")
      .getDatabase(name = "ated")
      .getCollection[A](collectionName)
      .withCodecRegistry(CodecRegistries.fromRegistries(codecRegistry, DEFAULT_CODEC_REGISTRY))
}