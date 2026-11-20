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
 

  private def cfgWith(gcmKey: String): Configuration = {
    val hocon =
      s"""
         |mongodb.encryptionGcm {
         |  key = "$gcmKey"
         |  previousKeys = []
         |}
         |""".stripMargin
    Configuration(ConfigFactory.parseString(hocon))
  }
  
  private def gcmCrypto(cfg: Configuration) =
    SymmetricCryptoFactory.aesGcmCryptoFromConfig("mongodb.encryptionGcm", cfg.underlying)

  "MongoCryptoProvider" should {

    "encrypt and decrypt (round-trip) new values" in {
      val provider = new MongoCryptoProvider(cfgWith(gcmKeyA))
      val crypto   = provider.gcmCrypto
      val plain    = "Hello £Ü 𐍈 — {\"a\":1}"
      val enc      = crypto.encrypt(PlainText(plain))
      enc.value must not equal plain
      crypto.decrypt(enc).value mustBe plain
    }
    
    "decrypt GCM-encrypted values via the fallback " in {
      val cfg      = cfgWith(gcmKeyA)
      val gcmValue =MongoCryptoProvider(cfg).gcmCrypto.encrypt(PlainText("future-gcm-record"))
      new MongoCryptoProvider(cfg).gcmCrypto.decrypt(gcmValue).value mustBe "future-gcm-record"
    }

    "decrypt a value that GCM reads correctly " in {
      val cfg   = cfgWith(gcmKeyA)
      val textToEncrypt = "\"ATED Tax Payer\""
      val value = Crypted("8EFW3woBxybFCTkH69xxO5p3GW+MR8Z6GXT2BS8JgB+IVlLix+fOXBJIfCbHQRhR")

      gcmCrypto(cfg).decrypt(value).value mustBe textToEncrypt // GCM reads it correctly
      
    }
    
    "fail to decrypt when the GCM key does not  match" in {
      val enc = new MongoCryptoProvider(cfgWith(gcmKeyA)).gcmCrypto.encrypt(PlainText("secret"))
      val other = new MongoCryptoProvider(cfgWith(gcmKeyB))
      an [SecurityException] must be thrownBy other.gcmCrypto.decrypt(enc)
    }
  }
}