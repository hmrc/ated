/*
 * Copyright 2026 HM Revenue & Customs
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
import uk.gov.hmrc.crypto.{PlainText, SymmetricCryptoFactory}

class MongoCryptoProviderSpec extends PlaySpec {

  private def randomBase64Key(bytes: Int): String = {
    val b = new Array[Byte](bytes)
    new java.security.SecureRandom().nextBytes(b)
    java.util.Base64.getEncoder.encodeToString(b)
  }

  private def cfgWith(gcmKey: String, ecbKey: String, ecbPrevious: Seq[String] = Nil): Configuration = {
    val prev = ecbPrevious.map(k => s""""$k"""").mkString(", ")
    val hocon =
      s"""
         |mongodb.encryption {
         |  enabled = true
         |  key = "$ecbKey"
         |  previousKeys = [ $prev ]
         |}
         |mongodb.encryptionGcm {
         |  key = "$gcmKey"
         |  previousKeys = []
         |}
         |""".stripMargin
    Configuration(ConfigFactory.parseString(hocon))
  }

  private def legacyEcbCrypto(cfg: Configuration) =
    SymmetricCryptoFactory.aesCryptoFromConfig("mongodb.encryption", cfg.underlying)

  "MongoCryptoProvider" should {

    "encrypt and decrypt (round-trip) new values via AES-GCM" in {
      val provider = new MongoCryptoProvider(cfgWith(randomBase64Key(32), randomBase64Key(32)))
      val crypto   = provider.crypto
      val plain    = "Hello £Ü 𐍈 — {\"a\":1}"
      val enc      = crypto.encrypt(PlainText(plain))
      enc.value must not equal plain
      crypto.decrypt(enc).value mustBe plain
    }

    "decrypt legacy ECB-encrypted values via the fallback" in {
      val ecbKey = randomBase64Key(32)
      val gcmKey = randomBase64Key(32)
      val cfg    = cfgWith(gcmKey, ecbKey)
      val existing = legacyEcbCrypto(cfg).encrypt(PlainText("legacy-record"))
      val provider = new MongoCryptoProvider(cfg)
      provider.crypto.decrypt(existing).value mustBe "legacy-record"
    }

    "write new values as AES-GCM, not legacy ECB" in {
      val ecbKey = randomBase64Key(32)
      val gcmKey = randomBase64Key(32)
      val cfg    = cfgWith(gcmKey, ecbKey)
      val gcmCipher = new MongoCryptoProvider(cfg).crypto.encrypt(PlainText("new-record"))
      an [SecurityException] must be thrownBy legacyEcbCrypto(cfg).decrypt(gcmCipher)
    }

    "support rotation via ECB previousKeys (new provider reads old ciphertext)" in {
      val oldEcbKey     = randomBase64Key(32)
      val currentEcbKey = randomBase64Key(32)
      val gcmKey        = randomBase64Key(32)
      val ciphertext    = legacyEcbCrypto(cfgWith(gcmKey, oldEcbKey)).encrypt(PlainText("rotate-me"))
      val newProvider   = new MongoCryptoProvider(cfgWith(gcmKey, currentEcbKey, ecbPrevious = Seq(oldEcbKey)))
      newProvider.crypto.decrypt(ciphertext).value mustBe "rotate-me"
    }

    "round-trip GCM values independently of the ECB key in config" in {
      val gcmKey   = randomBase64Key(32)
      val provider = new MongoCryptoProvider(cfgWith(gcmKey, randomBase64Key(32)))
      val enc      = provider.crypto.encrypt(PlainText("gcm-only"))
      val reread   = new MongoCryptoProvider(cfgWith(gcmKey, randomBase64Key(32)))
      reread.crypto.decrypt(enc).value mustBe "gcm-only"
    }

    "fail to decrypt when neither the GCM key nor any ECB key matches" in {
      val p1  = new MongoCryptoProvider(cfgWith(randomBase64Key(32), randomBase64Key(32)))
      val p2  = new MongoCryptoProvider(cfgWith(randomBase64Key(32), randomBase64Key(32)))
      val enc = p1.crypto.encrypt(PlainText("secret"))
      an [SecurityException] must be thrownBy p2.crypto.decrypt(enc)
    }
  }
}