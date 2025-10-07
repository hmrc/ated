/*
 * Copyright 2025 HM Revenue & Customs
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

package crypto
import com.typesafe.config.ConfigFactory
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.crypto.PlainText   // note: only PlainText imported
class MongoCryptoProviderSpec extends PlaySpec {
  private def randomBase64Key(bytes: Int = 32): String = {
    val b = new Array[Byte](bytes)
    new java.security.SecureRandom().nextBytes(b)
    java.util.Base64.getEncoder.encodeToString(b)
  }
  private def cfgWith(key: String, previous: Seq[String] = Nil): Configuration = {
    val prev = previous.map(k => s""""$k"""").mkString(", ")
    val hocon =
      s"""
         |mongodb.encryption {
         |  enabled = true
         |  key = "$key"
         |  previousKeys = [ $prev ]
         |}
         |""".stripMargin
    Configuration(ConfigFactory.parseString(hocon))
  }
  "MongoCryptoProvider" should {
    "encrypt and decrypt (round-trip) with the same key" in {
      val key = randomBase64Key(32)
      val provider = new MongoCryptoProvider(cfgWith(key))
      val crypto   = provider.crypto
      val plain = "Hello £Ü 𐍈 — {\"a\":1}"
      val enc   = crypto.encrypt(PlainText(plain))
      enc.value must not equal plain
      crypto.decrypt(enc).value mustBe plain
    }
    "fail to decrypt with a different key" in {
      val key1 = randomBase64Key(32)
      val key2 = randomBase64Key(32)
      val p1 = new MongoCryptoProvider(cfgWith(key1))
      val p2 = new MongoCryptoProvider(cfgWith(key2))
      val enc = p1.crypto.encrypt(PlainText("secret"))
      an [SecurityException] must be thrownBy p2.crypto.decrypt(enc)
    }
    "support rotation via previousKeys (new key reads old ciphertext)" in {
      val oldKey = randomBase64Key(32)
      val newKey = randomBase64Key(32)
      val oldProvider = new MongoCryptoProvider(cfgWith(oldKey))
      val ciphertext  = oldProvider.crypto.encrypt(PlainText("rotate-me"))
      val newProvider = new MongoCryptoProvider(cfgWith(newKey, previous = Seq(oldKey)))
      newProvider.crypto.decrypt(ciphertext).value mustBe "rotate-me"
    }
  }
}