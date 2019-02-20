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

package services

import builders.ChangeLiabilityReturnBuilder._
import builders._
import connectors.EtmpReturnsConnector
import models._
import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._

import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

class ReturnSummaryServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockEtmpConnector = mock[EtmpReturnsConnector]
  val mockPropertyDetailsService = mock[PropertyDetailsService]
  val mockReliefsService = mock[ReliefsService]
  val mockDisposeLiabilityReturnService = mock[DisposeLiabilityReturnService]
  val atedRefNo = "ATED-123"
  val noOfYears = 4
  val successResponseJson = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val formBundle1 = "123456789012"
  val formBundle2 = "123456789000"
  val periodKey = 2014
  val formBundleReturn1 = generateFormBundleResponse(periodKey)
  val formBundleReturn2 = generateFormBundleResponse(periodKey)
  val disposeCalculated2 = DisposeCalculated(1000, 200)

  override def beforeEach = {
    reset(mockEtmpConnector)
    reset(mockPropertyDetailsService)
    reset(mockReliefsService)
    reset(mockDisposeLiabilityReturnService)
  }

  object TestReturnSummaryService extends ReturnSummaryService {
    override val etmpConnector = mockEtmpConnector
    override val propertyDetailsService = mockPropertyDetailsService
    override val reliefsService = mockReliefsService
    override val disposeLiabilityReturnService = mockDisposeLiabilityReturnService
  }

  "ReturnSummaryService" must {

    lazy val disposeLiability1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleReturn1)
    lazy val disposeLiability2 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleReturn1, calculated = Some(disposeCalculated2))

    "use the correct services" in {
      ReturnSummaryService.reliefsService must be(ReliefsService)
      ReturnSummaryService.propertyDetailsService must be(PropertyDetailsService)
      ReturnSummaryService.disposeLiabilityReturnService must be(DisposeLiabilityReturnService)
    }
    "use the correct Etmpconnector" in {
      ReturnSummaryService.etmpConnector must be(EtmpReturnsConnector)
    }

    "getPartialSummaryReturn" must {

      "return SummaryReturnModel with drafts, from Mongo DB" in {
        val relDraft = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts = Seq(relDraft)
        val propDetails = PropertyDetailsBuilder.getPropertyDetails("1")
        val propDetailsSeq = Seq(propDetails)
        val dispLiab = Seq(disposeLiability1)

        val expected = SummaryReturnsModel(None, List(PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", None, "Liability")), None),
          PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789012", "line1 line1", None, "Dispose_Liability")), None)))

        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val result = TestReturnSummaryService.getPartialSummaryReturn(atedRefNo)
        await(result) must be(expected)
      }

      "return blank SummaryReturnModel, for no matching period key" in {
        val reliefDrafts = Nil
        val propDetailsSeq = Nil

        val dispLiab = Nil

        val expected = SummaryReturnsModel(None, Nil)
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val result = TestReturnSummaryService.getPartialSummaryReturn(atedRefNo)
        await(result) must be(expected)
      }
    }

    "getFullSummaryReturn" must {

      "return SummaryReturnModel with drafts and submitted return when we only have new  - from Mongo DB and ETMP" in {

        val etmpReturn = EtmpReturnsResponseModelBuilder.generateEtmpGetReturnsResponse(periodKey.toString)
        //TODO: if etmp reverts back to numeric, uncomment next line and comment next-to-next
        //val etmpReturnJson = Json.toJson(etmpReturn)
        val etmpReturnJson =
          Json.parse(
            """ {"safeId":"123Safe","organisationName":"ACNE LTD.","periodData":[{"periodKey":"2014",
              |"returnData":{"reliefReturnSummary":[{"formBundleNumber":"12345","dateOfSubmission":"2014-05-05","relief":"Farmhouses","reliefStartDate":"2014-09-05","reliefEndDate":"2014-10-05"}],
              |"liabilityReturnSummary":[{"propertySummary":[{"contractObject":"abc","addressLine1":"line1","addressLine2":"line2",
              |"return":[
              |{"formBundleNumber":"12345","dateOfSubmission":"2014-05-05","dateFrom":"2014-09-05","dateTo":"2014-10-05","liabilityAmount":"1000","paymentReference":"pay-123","changeAllowed":true}
              |]}]}]}}],"atedBalance":"10000"} """.stripMargin)
        val relDraft = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts = Seq(relDraft)
        val propDetails = PropertyDetailsBuilder.getPropertyDetails("1")
        val propDetailsSeq = Seq(propDetails)
        val dispLiab = Seq(disposeLiability1)

        val years = 6

        val expected = SummaryReturnsModel(Some(10000), List(PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", None, "Liability")), None),
          PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789012", "line1 line1", None, "Dispose_Liability")),
            Some(SubmittedReturns(periodKey, List(SubmittedReliefReturns("12345", "Farmhouses", new LocalDate("2014-09-05"), new LocalDate("2014-10-05"), new LocalDate("2014-05-05"), None, None)),
              List(SubmittedLiabilityReturns("12345", "line1 line2", 1000, new LocalDate("2014-09-05"), new LocalDate("2014-10-05"), new LocalDate("2014-05-05"),
                true, "pay-123")))))))

        when(mockEtmpConnector.getSummaryReturns(Matchers.eq(atedRefNo), Matchers.eq(years))).
          thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(etmpReturnJson))))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val result = TestReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return SummaryReturnModel with drafts and submitted return when we have new and old returns  - from Mongo DB and ETMP" in {

        val etmpReturn = EtmpReturnsResponseModelBuilder.generateEtmpGetReturnsResponse(periodKey.toString)
        //TODO: if etmp reverts back to numeric, uncomment next line and comment next-to-next
        //val etmpReturnJson = Json.toJson(etmpReturn)
        val etmpReturnJson =
          Json.parse(
            """ {"safeId":"123Safe","organisationName":"ACNE LTD.","periodData":[{"periodKey":"2014",
              |"returnData":{"reliefReturnSummary":[{"formBundleNumber":"12345","dateOfSubmission":"2014-05-05","relief":"Farmhouses","reliefStartDate":"2014-09-05","reliefEndDate":"2014-10-05"}],
              |"liabilityReturnSummary":[{"propertySummary":[{"contractObject":"abc","addressLine1":"line1","addressLine2":"line2",
              |"return":[
              |{"formBundleNumber":"12346","dateOfSubmission":"2014-05-05","dateFrom":"2014-09-05","dateTo":"2014-10-05","liabilityAmount":"1000","paymentReference":"pay-123","changeAllowed":true},
              |{"formBundleNumber":"12345","dateOfSubmission":"2014-01-01","dateFrom":"2014-09-05","dateTo":"2014-10-05","liabilityAmount":"1000","paymentReference":"pay-123","changeAllowed":false}
              |]}]}]}}],"atedBalance":"10000"} """.stripMargin)
        val relDraft = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts = Seq(relDraft)
        val propDetails = PropertyDetailsBuilder.getPropertyDetails("1")
        val propDetailsSeq = Seq(propDetails)
        val dispLiab = Seq(disposeLiability1)

        val years = 6

        val expected = SummaryReturnsModel(Some(10000),
          List(
            PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", None, "Liability")), None),
            PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789012", "line1 line1", None, "Dispose_Liability")),
              Some(SubmittedReturns(periodKey, List(SubmittedReliefReturns("12345", "Farmhouses", new LocalDate("2014-09-05"), new LocalDate("2014-10-05"), new LocalDate("2014-05-05"), None, None)),
                  List(
                    SubmittedLiabilityReturns("12346", "line1 line2", 1000, new LocalDate("2014-09-05"), new LocalDate("2014-10-05"), new LocalDate("2014-05-05"), true, "pay-123")
                  ),
                  List(
                    SubmittedLiabilityReturns("12345", "line1 line2", 1000, new LocalDate("2014-09-05"), new LocalDate("2014-10-05"), new LocalDate("2014-01-01"), false, "pay-123")
                  )
                )
              )
            )
          )
        )

        when(mockEtmpConnector.getSummaryReturns(Matchers.eq(atedRefNo), Matchers.eq(years))).
          thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(etmpReturnJson))))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val result = TestReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return SummaryReturnModel with drafts and submitted return - from Mongo DB and ETMP - no liability or draft" in {

        val etmpReturn = EtmpReturnsResponseModelBuilder.generateEtmpGetReturnsResponseNoDraftandLiabilty(periodKey.toString)
        val etmpReturnJson = Json.parse("""{"safeId":"123Safe","organisationName":"organisationName","periodData":[{"periodKey":"2014","returnData":{}}],"atedBalance":"0"}""")
        val relDraft = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts = Seq(relDraft)
        val propDetails = PropertyDetailsBuilder.getPropertyDetails("1")
        val propDetailsSeq = Seq(propDetails)
        val dispLiab = Seq(disposeLiability1)

        val years = 6

        val expected = SummaryReturnsModel(Some(0), List(PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", None, "Liability")), None),
          PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789012", "line1 line1", None, "Dispose_Liability")),
            Some(SubmittedReturns(periodKey, List(), List())))))

        when(mockEtmpConnector.getSummaryReturns(Matchers.eq(atedRefNo), Matchers.eq(years))).
          thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(etmpReturnJson))))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val result = TestReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return SummaryReturnModel with drafts and submitted return - from Mongo DB and ETMP - no liabliity amount in liabilty return" in {

        val etmpReturn = EtmpReturnsResponseModelBuilder.generateEmptyEtmpGetReturnsResponse(periodKey.toString)
        val etmpReturnJson = Json.parse("""{"safeId":"123Safe","organisationName":"organisationName","periodData":[{"periodKey":"2014","returnData":{"reliefReturnSummary":[{"formBundleNumber":"12345","dateOfSubmission":"2014-05-05","relief":"Farmhouses","reliefStartDate":"2014-09-05","reliefEndDate":"2014-10-05"}],"liabilityReturnSummary":[{}]}}],"atedBalance":"0"}""")
        val relDraft = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts = Seq(relDraft)
        val propDetails = PropertyDetails(atedRefNo = "ated-ref-1", "123456789099", periodKey, addressProperty = PropertyDetailsBuilder.getPropertyDetailsAddress(None), calculated = None, formBundleReturn = Some(ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)))
        val propDetailsSeq = Seq(propDetails)
        val dispLiab = Seq(disposeLiability2)

        val years = 6

        val expected = SummaryReturnsModel(Some(0), List(PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789099", "addr1 addr2", None, "Change_Liability"),
          DraftReturns(periodKey, "123456789012", "line1 line1", Some(1000), "Dispose_Liability")),
          Some(SubmittedReturns(periodKey, List(SubmittedReliefReturns("12345", "Farmhouses", new LocalDate("2014-09-05"), new LocalDate("2014-10-05"),
            new LocalDate("2014-05-05"), None, None)), List())))))

        when(mockEtmpConnector.getSummaryReturns(Matchers.eq(atedRefNo), Matchers.eq(years))).
          thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(etmpReturnJson))))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val result = TestReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return SummaryReturnModel with drafts but no ETMP data found - from Mongo DB " in {

        val relDraft = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, Reliefs(periodKey, rentalBusiness = true))
        val reliefDrafts = Seq(relDraft)
        val propDetails = PropertyDetailsBuilder.getPropertyDetails("1", liabilityAmount = Some(1000))
        val propDetailsSeq = Seq(propDetails)
        val dispLiab = Seq(disposeLiability1)
        val years = 6

        when(mockEtmpConnector.getSummaryReturns(Matchers.eq(atedRefNo), Matchers.eq(years))).
          thenReturn(Future.successful(HttpResponse(NOT_FOUND, responseJson = None)))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val expected = SummaryReturnsModel(None, List(PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", Some(1000), "Liability")), None),
          PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "", "Rental businesses", None, "Relief"),
            DraftReturns(periodKey, "123456789012", "line1 line1", None, "Dispose_Liability")), None)))

        val result = TestReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }
      "return blank SummaryReturnModel no drafts and NO submitted ETMP return found, - for no matching period keys" in {
        val reliefDrafts = Nil
        val propDetailsSeq = Nil
        val dispLiab = Nil
        val years = 6

        val expected = SummaryReturnsModel(None, Nil)

        when(mockEtmpConnector.getSummaryReturns(Matchers.eq(atedRefNo), Matchers.eq(years))).
          thenReturn(Future.successful(HttpResponse(NOT_FOUND, responseJson = None)))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val result = TestReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return blank SummaryReturnModel no ETMP Return - internal server error" in {
        val reliefDrafts = Nil
        val propDetailsSeq = Nil
        val dispLiab = Nil
        val years = 6

        val expected = SummaryReturnsModel(None, Nil)

        when(mockEtmpConnector.getSummaryReturns(Matchers.eq(atedRefNo), Matchers.eq(years))).
          thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = None)))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val result = TestReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return blank SummaryReturnModel for no drafts but no matching period key" in {
        val etmpReturn = EtmpReturnsResponseModelBuilder.generateEtmpGetReturnsResponseWithNoPeriodData(periodKey.toString)
        val etmpReturnJson = Json.parse("""{"safeId":"123Safe","organisationName":"organisationName","periodData":[],"atedBalance":"0"}""")
        val reliefDrafts = Nil
        val propDetailsSeq = Nil
        val dispLiab = Nil
        val years = 6


        val expected = SummaryReturnsModel(None, Nil)

        when(mockEtmpConnector.getSummaryReturns(Matchers.eq(atedRefNo), Matchers.eq(years))).
          thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(etmpReturnJson))))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(Matchers.eq(atedRefNo))(Matchers.any())).thenReturn(Future.successful(reliefDrafts))

        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(Matchers.eq(atedRefNo))(Matchers.any())).
          thenReturn(Future.successful(dispLiab))

        val result = TestReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }
    }
  }

  "filterReturnsByOldAndNew" must {
    "return Nil if we have no Periods" in {
      val returnTuple = TestReturnSummaryService.filterReturnsByOldAndNew(Nil)
      returnTuple._1.isEmpty must be (true)
      returnTuple._2.isEmpty must be (true)
    }

    "return Nil if we have no returns for a property" in {
      val propertySummary = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = Nil)

      val returnTuple = TestReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary))
      returnTuple._1.isEmpty must be (true)
      returnTuple._2.isEmpty must be (true)
    }

    "return new if we only have one return for each property" in {
      val liability1 = EtmpReturn(formBundleNumber = "1", dateOfSubmission = new LocalDate("2014-05-05"),
            dateFrom = new LocalDate("2014-04-15"),
            dateTo = new LocalDate("2015-03-31"),
            liabilityAmount = BigDecimal(1000),
            paymentReference = "123123m",
            changeAllowed = false)
      val propertySummary1 = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1))

      val liability2 = EtmpReturn(formBundleNumber = "2", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val propertySummary2 = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability2))

      val returnTuple = TestReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1, propertySummary2))
      returnTuple._2.isEmpty must be (true)

      returnTuple._1.size must be (2)
      returnTuple._1.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._1.find(_.formBundleNo == "2").isDefined must be (true)
    }

    "return new and old if we have multiple returns for a property" in {
      val liability1 = EtmpReturn(formBundleNumber = "1", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability2 = EtmpReturn(formBundleNumber = "2", dateOfSubmission = new LocalDate("2014-05-06"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3 = EtmpReturn(formBundleNumber = "3", dateOfSubmission = new LocalDate("2014-05-04"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val propertySummary1 = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple = TestReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (1)
      returnTuple._1.find(_.formBundleNo == "2").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (2)
      returnTuple._2.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._2.find(_.formBundleNo == "3").isDefined must be (true)
    }

    "if two returns have the same date, return the one that is editable as new (last in list is  editable)" in {
      val liability1 = EtmpReturn(formBundleNumber = "1", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability2 = EtmpReturn(formBundleNumber = "2", dateOfSubmission = new LocalDate("2014-05-04"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3 = EtmpReturn(formBundleNumber = "3", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = true)
      val propertySummary1 = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple = TestReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (1)
      returnTuple._1.find(_.formBundleNo == "3").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (2)
      returnTuple._2.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._2.find(_.formBundleNo == "2").isDefined must be (true)
    }

    "if two returns have the same date, return the one that is editable as new (first in list is  editable)" in {
      val liability1 = EtmpReturn(formBundleNumber = "1", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = true)
      val liability2 = EtmpReturn(formBundleNumber = "2", dateOfSubmission = new LocalDate("2014-05-04"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3 = EtmpReturn(formBundleNumber = "3", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val propertySummary1 = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple = TestReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (1)
      returnTuple._1.find(_.formBundleNo == "1").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (2)
      returnTuple._2.find(_.formBundleNo == "2").isDefined must be (true)
      returnTuple._2.find(_.formBundleNo == "3").isDefined must be (true)
    }

    "if two returns have the same date, return the one that is editable as new : if neither is editable, put both in new" in {
      val liability1 = EtmpReturn(formBundleNumber = "1", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability2 = EtmpReturn(formBundleNumber = "2", dateOfSubmission = new LocalDate("2014-05-04"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3 = EtmpReturn(formBundleNumber = "3", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val propertySummary1 = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple = TestReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (2)
      returnTuple._1.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._1.find(_.formBundleNo == "3").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (1)
      returnTuple._2.find(_.formBundleNo == "2").isDefined must be (true)
    }

    "if two returns have the same date, return the one that is editable as new : if both are editable, put both in new" in {
      val liability1 = EtmpReturn(formBundleNumber = "1", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = true)
      val liability2 = EtmpReturn(formBundleNumber = "2", dateOfSubmission = new LocalDate("2014-05-04"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3 = EtmpReturn(formBundleNumber = "3", dateOfSubmission = new LocalDate("2014-05-05"),
        dateFrom = new LocalDate("2014-04-15"),
        dateTo = new LocalDate("2015-03-31"),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = true)
      val propertySummary1 = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple = TestReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (2)
      returnTuple._1.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._1.find(_.formBundleNo == "3").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (1)
      returnTuple._2.find(_.formBundleNo == "2").isDefined must be (true)
    }
  }
}
