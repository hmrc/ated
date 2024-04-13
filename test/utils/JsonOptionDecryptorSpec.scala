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
/*
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json, OFormat}
import uk.gov.hmrc.crypto.json._
import uk.gov.hmrc.crypto.{Encrypter, Decrypter, _}

class JsonEncryptionSpec extends PlaySpec {

  "formatting an entity" should {

    "encrypt the elements" in {

      val e = TestEntity("unencrypted",
        "encrypted",
        true,
        BigDecimal("234")
      )

      val json = Json.toJson(e)(TestEntity.formats)

      (json \ "normalString").get mustBe JsString("unencrypted")
      (json \ "encryptedString").get mustBe JsString("3TW3L1raxsKBYuKvtKqPEQ==")
      (json \ "encryptedBoolean").get mustBe JsString("YhWm43Ad3rW5Votdy855Kg==")
      (json \ "encryptedNumber").get mustBe JsString("Z/ipDOvm7C3ck/TBkiteAg==")

    }

    "encrypt empty elements" in {

      val e = TestEntity("unencrypted",
        Option[String](None),
        Option[Boolean](None),
        Option[BigDecimal](None)
      )

      val json = Json.toJson(e)(TestEntity.formats)

      (json \ "normalString").get mustBe JsString("unencrypted")
      (json \ "encryptedString").get mustBe JsString("rEMu/lGbPQCXd8ohhLl47A==")
      (json \ "encryptedBoolean").get mustBe JsString("rEMu/lGbPQCXd8ohhLl47A==")
      (json \ "encryptedNumber").get mustBe JsString("rEMu/lGbPQCXd8ohhLl47A==")

    }

    "decrypt the elements" in {

      val jsonString = """{
        "normalString":"unencrypted",
        "encryptedString" : "3TW3L1raxsKBYuKvtKqPEQ==",
        "encryptedBoolean" : "YhWm43Ad3rW5Votdy855Kg==",
        "encryptedNumber" : "Z/ipDOvm7C3ck/TBkiteAg=="
      }""".stripMargin

      val entity = Json.fromJson(Json.parse(jsonString))(TestEntity.formats).get

      entity mustBe TestEntity("unencrypted",
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

      entity mustBe TestEntity("unencrypted",
        Protected[Option[String]](None),
        Protected[Option[Boolean]](None),
        Protected[Option[BigDecimal]](None)
      )

    }
  }

}

case class TestEntity(normalString: String,
                      encryptedString: Option[String],
                      encryptedBoolean: Option[Boolean],
                      encryptedNumber: Option[BigDecimal])

object TestEntity {

  implicit val crypto: Encrypter with Decrypter = SymmetricCryptoFactory.aesCrypto("P5xsJ9Nt+quxGZzB4DeLfw==")  

  object JsonStringEncryption extends JsonEncryptor[Option[String]]
  object JsonBooleanEncryption extends JsonEncryptor[Option[Boolean]]
  object JsonBigDecimalEncryption extends JsonEncryptor[Option[BigDecimal]]

  object JsonStringDecryption extends JsonOptionDecryptor[String]
  object JsonBooleanDecryption extends JsonOptionDecryptor[Boolean]
  object JsonBigDecimalDecryption extends JsonOptionDecryptor[BigDecimal]

  implicit val formats: OFormat[TestEntity] = {
    implicit val encryptedStringFormats: JsonStringEncryption.type = JsonStringEncryption
    implicit val encryptedBooleanFormats: JsonBooleanEncryption.type = JsonBooleanEncryption
    implicit val encryptedBigDecimalFormats: JsonBigDecimalEncryption.type = JsonBigDecimalEncryption

    implicit val decryptedStringFormats: JsonStringDecryption.type = JsonStringDecryption
    implicit val decryptedBooleanFormats: JsonBooleanDecryption.type = JsonBooleanDecryption
    implicit val decryptedBigDecimalFormats: JsonBigDecimalDecryption.type = JsonBigDecimalDecryption

    Json.format[TestEntity]
  }
}

case class TestForm(name: String, sname: String, amount: Int, isValid: Boolean)

object TestForm {
  implicit val formats: OFormat[TestForm] = Json.format[TestForm]
}
*/

