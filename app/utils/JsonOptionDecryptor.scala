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

package utils

import play.api.libs.json._
import uk.gov.hmrc.crypto.{Crypted, Protected, CompositeSymmetricCrypto}

class JsonOptionDecryptor[T](implicit crypto: CompositeSymmetricCrypto, rds: Reads[T]) extends Reads[Protected[Option[T]]] {
  override def reads(json: JsValue): JsResult[Protected[Option[T]]] = {
      val crypted = Crypted(json.as[String])
      JsSuccess(readFromJson(crypted))
  }

  private def readFromJson(encrypted: Crypted): Protected[Option[T]] = {
    val plainText = crypto.decrypt(encrypted)
    val obj = Json.parse(plainText.value).asOpt[T]
    Protected(obj)
  }

}
