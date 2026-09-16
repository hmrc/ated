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
import uk.gov.hmrc.crypto.{Crypted, PlainText, SymmetricCryptoFactory}

class MongoCryptoProviderSpec extends PlaySpec {

  private val gcmKeyA = "bc47EZM1MO0w9a4pUAwoGfKzasWhwp8HkOta+5XZsl8="
  private val gcmKeyB = "yY/qUIQgQjMjzPzyU8qCJ4GiTvtgfUv4UYVbrto7Puk="
  private val ecbKeyA = "FhFHtQ1nWbDtu7JVgCgqVOOh7t06kgxzT7RsKcRPZQo="
  private val ecbKeyB = "dhW8f6fyQr/7ufEcDT3SsN3F5wYm5iWusiXfHhu9Clc="
  private val ecbKeyOld = "XSr7MLfm62h+PLxCYwYJpeYU6ea/Dw/7HkDWHFRUa7M="

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

  private def gcmCrypto(cfg: Configuration) =
    SymmetricCryptoFactory.aesGcmCryptoFromConfig("mongodb.encryptionGcm", cfg.underlying)

  "MongoCryptoProvider" should {

    "encrypt and decrypt (round-trip) new values" in {
      val provider = new MongoCryptoProvider(cfgWith(gcmKeyA, ecbKeyA))
      val crypto   = provider.crypto
      val plain    = "Hello £Ü 𐍈 — {\"a\":1}"
      val enc      = crypto.encrypt(PlainText(plain))
      enc.value must not equal plain
      crypto.decrypt(enc).value mustBe plain
    }

    "decrypt legacy ECB-encrypted values" in {
      val cfg      = cfgWith(gcmKeyA, ecbKeyA)
      val existing = legacyEcbCrypto(cfg).encrypt(PlainText("legacy-record"))
      val provider = new MongoCryptoProvider(cfg)
      provider.crypto.decrypt(existing).value mustBe "legacy-record"
    }

    "write new values in legacy ECB format during stage 1" in {
      val cfg    = cfgWith(gcmKeyA, ecbKeyA)
      val cipher = new MongoCryptoProvider(cfg).crypto.encrypt(PlainText("new-record"))
      legacyEcbCrypto(cfg).decrypt(cipher).value mustBe "new-record"
    }

    "decrypt GCM-encrypted values via the fallback (stage 2 readiness)" in {
      val cfg      = cfgWith(gcmKeyA, ecbKeyA)
      val gcmValue = SymmetricCryptoFactory
        .aesGcmCryptoFromConfig("mongodb.encryptionGcm", cfg.underlying)
        .encrypt(PlainText("future-gcm-record"))
      new MongoCryptoProvider(cfg).crypto.decrypt(gcmValue).value mustBe "future-gcm-record"
    }

    "decrypt a value that GCM reads correctly but legacy ECB reads as garbage" in {
      val cfg   = cfgWith(gcmKeyA, ecbKeyA)
      val textToEncrypt = "\"ATED Tax Payer\""
      val wrongEcbEncryptionValue = "�\u05CC؏��\u00049���\u0013v���\u0000}�D�d3�<?��M($\u0012�1�\u001C�e�g�#�Ktw"
      val value = Crypted("8EFW3woBxybFCTkH69xxO5p3GW+MR8Z6GXT2BS8JgB+IVlLix+fOXBJIfCbHQRhR")

      gcmCrypto(cfg).decrypt(value).value mustBe textToEncrypt // GCM reads it correctly

      legacyEcbCrypto(cfg).decrypt(value).value must not equal textToEncrypt  // ECB succeeds, but is wrong

      legacyEcbCrypto(cfg).decrypt(value).value mustBe wrongEcbEncryptionValue

      // verify provider is correct
      new MongoCryptoProvider(cfg).crypto.decrypt(value).value mustBe textToEncrypt
    }

    "support rotation via ECB previousKeys (new provider reads old ciphertext)" in {
      val ciphertext  = legacyEcbCrypto(cfgWith(gcmKeyA, ecbKeyOld)).encrypt(PlainText("rotate-me"))
      val newProvider = new MongoCryptoProvider(cfgWith(gcmKeyA, ecbKeyA, ecbPrevious = Seq(ecbKeyOld)))
      newProvider.crypto.decrypt(ciphertext).value mustBe "rotate-me"
    }

    "fail to decrypt when neither the GCM key nor any ECB key matches" in {
      val enc = new MongoCryptoProvider(cfgWith(gcmKeyA, ecbKeyA)).crypto.encrypt(PlainText("secret"))
      val other = new MongoCryptoProvider(cfgWith(gcmKeyB, ecbKeyB))
      an [SecurityException] must be thrownBy other.crypto.decrypt(enc)
    }
  }
}