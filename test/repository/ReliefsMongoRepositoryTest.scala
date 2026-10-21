package repository

import metrics.ServiceMetrics
import models.ReliefsTaxAvoidance
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext

class ReliefsMongoRepositoryTest extends PlaySpec with GuiceOneAppPerSuite with DefaultPlayMongoRepositorySupport[ReliefsTaxAvoidance] {

  given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override protected val repository: ReliefsReactiveMongoRepository = new ReliefsReactiveMongoRepository(mongoComponent, metrics = mock[ServiceMetrics])

  "ChangeLiabilityReturnRepository" must {

    "have a ttl index defined" in {
      // automatically tested by DefaultPlayMongoRepositorySupport
    }
  }
}
