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

/*
 * Copyright 2016 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{Writes, JsString, Json}
import uk.gov.hmrc.crypto._
import uk.gov.hmrc.crypto.json._
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, Protected, ApplicationCrypto}

class JsonEncryptionSpec extends WordSpec with Matchers {

  "formatting an entity" should {

    "encrypt the elements" in {

      val e = TestEntity("unencrypted",
        Protected[Option[String]](Some("encrypted")),
        Protected[Option[Boolean]](Some(true)),
        Protected[Option[BigDecimal]](Some(BigDecimal("234")))
      )

      val json = Json.toJson(e)(TestEntity.formats)

      (json \ "normalString").get shouldBe JsString("unencrypted")
      (json \ "encryptedString").get shouldBe JsString("3TW3L1raxsKBYuKvtKqPEQ==")
      (json \ "encryptedBoolean").get shouldBe JsString("YhWm43Ad3rW5Votdy855Kg==")
      (json \ "encryptedNumber").get shouldBe JsString("Z/ipDOvm7C3ck/TBkiteAg==")

    }

    "encrypt empty elements" in {

      val e = TestEntity("unencrypted",
        Protected[Option[String]](None),
        Protected[Option[Boolean]](None),
        Protected[Option[BigDecimal]](None)
      )

      val json = Json.toJson(e)(TestEntity.formats)

      (json \ "normalString").get shouldBe JsString("unencrypted")
      (json \ "encryptedString").get shouldBe JsString("rEMu/lGbPQCXd8ohhLl47A==")
      (json \ "encryptedBoolean").get shouldBe JsString("rEMu/lGbPQCXd8ohhLl47A==")
      (json \ "encryptedNumber").get shouldBe JsString("rEMu/lGbPQCXd8ohhLl47A==")

    }

    "decrypt the elements" in {

      val jsonString = """{
        "normalString":"unencrypted",
        "encryptedString" : "3TW3L1raxsKBYuKvtKqPEQ==",
        "encryptedBoolean" : "YhWm43Ad3rW5Votdy855Kg==",
        "encryptedNumber" : "Z/ipDOvm7C3ck/TBkiteAg=="
      }""".stripMargin

      val entity = Json.fromJson(Json.parse(jsonString))(TestEntity.formats).get

      entity shouldBe TestEntity("unencrypted",
        Protected[Option[String]](Some("encrypted")),
        Protected[Option[Boolean]](Some(true)),
        Protected[Option[BigDecimal]](Some(BigDecimal("234")))
      )

    }

    "decrypt empty elements" in {

      val jsonString = """{
        "normalString":"unencrypted",
        "encryptedString" : "rEMu/lGbPQCXd8ohhLl47A==",
        "encryptedBoolean" : "rEMu/lGbPQCXd8ohhLl47A==",
        "encryptedNumber" : "rEMu/lGbPQCXd8ohhLl47A=="
      }""".stripMargin

      val entity = Json.fromJson(Json.parse(jsonString))(TestEntity.formats).get

      entity shouldBe TestEntity("unencrypted",
        Protected[Option[String]](None),
        Protected[Option[Boolean]](None),
        Protected[Option[BigDecimal]](None)
      )

    }
  }

}

object Crypto extends CompositeSymmetricCrypto {
  override protected val currentCrypto: Encrypter with Decrypter = new AesCrypto {
    override protected val encryptionKey: String = "P5xsJ9Nt+quxGZzB4DeLfw=="
  }
  override protected val previousCryptos: Seq[Decrypter] = Seq.empty
}

case class TestEntity(normalString: String,
                      encryptedString: Protected[Option[String]],
                      encryptedBoolean: Protected[Option[Boolean]],
                      encryptedNumber: Protected[Option[BigDecimal]])

object TestEntity {

  implicit val crypto = Crypto

  object JsonStringEncryption extends JsonEncryptor[Option[String]]
  object JsonBooleanEncryption extends JsonEncryptor[Option[Boolean]]
  object JsonBigDecimalEncryption extends JsonEncryptor[Option[BigDecimal]]

  object JsonStringDecryption extends JsonOptionDecryptor[String]
  object JsonBooleanDecryption extends JsonOptionDecryptor[Boolean]
  object JsonBigDecimalDecryption extends JsonOptionDecryptor[BigDecimal]

  implicit val formats = {
    implicit val encryptedStringFormats= JsonStringEncryption
    implicit val encryptedBooleanFormats = JsonBooleanEncryption
    implicit val encryptedBigDecimalFormats = JsonBigDecimalEncryption

    implicit val decryptedStringFormats = JsonStringDecryption
    implicit val decryptedBooleanFormats = JsonBooleanDecryption
    implicit val decryptedBigDecimalFormats = JsonBigDecimalDecryption

    Json.format[TestEntity]
  }
}

case class TestForm(name: String, sname: String, amount: Int, isValid: Boolean)

object TestForm {
  implicit val formats = Json.format[TestForm]
}
