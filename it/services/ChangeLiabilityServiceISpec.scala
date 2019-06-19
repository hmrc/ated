package services

import connectors.{AuthConnector, EmailConnector, EtmpReturnsConnector}
import helpers.{ComponentSpecBase, WiremockHelper}
import models.{PropertyDetails, PropertyDetailsAddress, PropertyDetailsCalculated}
import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import repository.PropertyDetailsMongoRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class ChangeLiabilityServiceISpec extends ComponentSpecBase with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val mockMongoRepository: PropertyDetailsMongoRepository = mock[PropertyDetailsMongoRepository]

  lazy val service: ChangeLiabilityService = new ChangeLiabilityService {
    val etmpConnector: EtmpReturnsConnector = EtmpReturnsConnector
    val authConnector: AuthConnector = AuthConnector
    val subscriptionDataService: SubscriptionDataService = SubscriptionDataService
    val emailConnector: EmailConnector = EmailConnector

    override def propertyDetailsCache: PropertyDetailsMongoRepository = mockMongoRepository
  }

  lazy val propertyDetails = PropertyDetails("refNo", "oldId", 1, PropertyDetailsAddress("line1", "line2", None, None),
    calculated = Some(PropertyDetailsCalculated(valuationDateToUse = Some(LocalDate.now()), professionalValuation = Some(true))))

  "Calling .submitChangeLiability" should {

    "return a status of OK with a valid response" when {

      "submitting a change for a valid submission and matching old id" in {
        WiremockHelper.stubGet("/auth/authority", 200, """{"accounts":{"ated":{"utr":"a"}}}""")
        WiremockHelper.stubGet("/annual-tax-enveloped-dwellings/subscription/refNo", 200, """{"organisationName" : "Company Name", "emailAddress" : "example@email.com"}""")
        WiremockHelper.stubPut("/annual-tax-enveloped-dwellings/returns/refNo", 200,
          s"""{
             | "processingDate" : "2019-06-17T07:55:07Z",
             | "liabilityReturnResponse" : [
             |   {
             |     "mode" : "",
             |     "oldFormBundleNumber" : "oldNum",
             |     "formBundleNumber" : "currentNum",
             |     "liabilityAmount" : 100,
             |     "amountDueOrRefund" : 50,
             |     "paymentReference" : ""
             |   }
             | ],
             | "accountBalance" : "1000.0"
             |}""".stripMargin)
        when(mockMongoRepository.fetchPropertyDetails(Matchers.eq("refNo"))).thenReturn(Future.successful(Seq(propertyDetails)))

        val result: HttpResponse = await(service.submitChangeLiability("refNo", "oldId"))
        result.status shouldBe 200
        WiremockHelper.verifyPut("/annual-tax-enveloped-dwellings/returns/refNo",
          """
            |{
            |"liabilityReturn" : [ {
            |  "oldFormBundleNumber" : "oldId",
            |  "mode" : "Post",
            |  "periodKey" : "1",
            |  "propertyDetails" : {
            |    "address" : {
            |      "addressLine1" : "line1",
            |      "addressLine2" : "line2",
            |      "countryCode" : "GB"
            |    }
            |  },
            |  "dateOfValuation" : "2019-06-17",
            |  "professionalValuation" : true,
            |  "ninetyDayRuleApplies" : false,
            |  "lineItem" : [ ]
            |} ]
            |}
          """.stripMargin)
      }
    }
  }
}
