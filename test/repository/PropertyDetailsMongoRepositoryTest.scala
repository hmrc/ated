package repository

import crypto.MongoCryptoProvider
import metrics.ServiceMetrics
import models.PropertyDetails
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext


private class PropertyDetailsMongoRepositoryTest extends PlaySpec with GuiceOneAppPerSuite with DefaultPlayMongoRepositorySupport[PropertyDetails] {
  private val mongoCrypto: MongoCryptoProvider = app.injector.instanceOf[MongoCryptoProvider]

  given crypto: (Encrypter & Decrypter) = mongoCrypto.crypto
  given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override protected val repository: PropertyDetailsReactiveMongoRepository = new PropertyDetailsReactiveMongoRepository(mongoComponent, mock[ServiceMetrics])

  "PropertyDetailsRepository" must {

    "have a ttl index defined" in {
      // automatically tested by DefaultPlayMongoRepositorySupport
    }
  }

}
