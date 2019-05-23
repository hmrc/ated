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

import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import play.api.libs.json.{Format, Json, JsValue, JsError, JsSuccess}
import scala.reflect.ClassTag

// $COVERAGE-OFF$

trait Codecs {
  def playFormatCodec[A](format: Format[A])(implicit ct: ClassTag[A]) = new Codec[A] {
    private val documentCodec = DEFAULT_CODEC_REGISTRY.get(classOf[Document])

    override def getEncoderClass(): Class[A] =
      ct.runtimeClass.asInstanceOf[Class[A]]

    override def encode(writer: BsonWriter, value: A, encoderContext: EncoderContext) = {
      val json: JsValue = format.writes(value)
      val document = Document(json.toString)
      documentCodec.encode(writer, document, encoderContext)
    }

    override def decode(reader: BsonReader, decoderContext: DecoderContext): A = {
      val document: Document = documentCodec.decode(reader, decoderContext)
      val json = document.toJson
      format.reads(Json.parse(json)) match {
        case JsSuccess(v, _) => v
        case JsError(errors) => sys.error(s"Failed to parse json as ${ct.runtimeClass.getName} '$json': $errors")
      }
    }
  }
}

object Codecs extends Codecs

trait CodecProviders extends mongo.CodecProviders {

  def playFormatCodecProvider[A : ClassTag](format: Format[A]) =
    createProvider[A](Codecs.playFormatCodec(format))
}

object CodecProviders extends CodecProviders

// $COVERAGE-ON$
