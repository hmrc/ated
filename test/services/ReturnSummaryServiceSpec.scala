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

package services

import builders.ChangeLiabilityReturnBuilder._
import builders._
import connectors.EtmpReturnsConnector
import models._

import java.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class ReturnSummaryServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockEtmpConnector: EtmpReturnsConnector = mock[EtmpReturnsConnector]
  val mockPropertyDetailsService: PropertyDetailsService = mock[PropertyDetailsService]
  val mockReliefsService: ReliefsService = mock[ReliefsService]
  val mockDisposeLiabilityReturnService: DisposeLiabilityReturnService = mock[DisposeLiabilityReturnService]
  val atedRefNo = "ATED-123"
  val noOfYears = 4
  val successResponseJson: JsValue = Json.parse( """{"sapNumber":"1234567890", "safeId": "EX0012345678909", "agentReferenceNumber": "AARN1234567"}""")

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val formBundle1 = "123456789012"
  val formBundle2 = "123456789000"
  val periodKey = 2014
  val formBundleReturn1: FormBundleReturn = generateFormBundleResponse(periodKey)
  val formBundleReturn2: FormBundleReturn = generateFormBundleResponse(periodKey)
  val disposeCalculated2: DisposeCalculated = DisposeCalculated(1000, 200)

  override def beforeEach(): Unit = {
    reset(mockEtmpConnector)
    reset(mockPropertyDetailsService)
    reset(mockReliefsService)
    reset(mockDisposeLiabilityReturnService)
  }

  trait Setup {
    class TestReturnSummaryService extends ReturnSummaryService {
      override val etmpConnector: EtmpReturnsConnector = mockEtmpConnector
      override val propertyDetailsService: PropertyDetailsService = mockPropertyDetailsService
      override val reliefsService: ReliefsService = mockReliefsService
      override val disposeLiabilityReturnService: DisposeLiabilityReturnService = mockDisposeLiabilityReturnService
    }

    val testReturnSummaryService = new TestReturnSummaryService()
  }

  "ReturnSummaryService" must {

    lazy val disposeLiability1 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleReturn1)
    lazy val disposeLiability2 = DisposeLiabilityReturn(atedRefNo, formBundle1, formBundleReturn1, calculated = Some(disposeCalculated2))

    "getPartialSummaryReturn" must {

      "return SummaryReturnModel with drafts, from Mongo DB" in new Setup {
        val relDraft: ReliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts: Seq[ReliefsTaxAvoidance] = Seq(relDraft)
        val propDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1")
        val propDetailsSeq: Seq[PropertyDetails] = Seq(propDetails)
        val dispLiab: Seq[DisposeLiabilityReturn] = Seq(disposeLiability1)

        val expected = SummaryReturnsModel(None, List(PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", None, "Liability")), None),
          PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789012", "line1 line2", None, "Dispose_Liability")), None)))

        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getPartialSummaryReturn(atedRefNo)
        await(result) must be(expected)
      }

      "return blank SummaryReturnModel, for no matching period key" in new Setup {
        val reliefDrafts: Seq[Nothing] = Nil
        val propDetailsSeq: Seq[Nothing] = Nil

        val dispLiab: Seq[Nothing] = Nil

        val expected: SummaryReturnsModel = SummaryReturnsModel(None, Nil)
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getPartialSummaryReturn(atedRefNo)
        await(result) must be(expected)
      }
    }

    "getFullSummaryReturn" must {

      "return SummaryReturnModel with drafts and submitted return when we only have new  - from Mongo DB and ETMP" in new Setup {

        //TODO: if etmp reverts back to numeric, uncomment next line and comment next-to-next
        //val etmpReturnJson = Json.toJson(etmpReturn)
        val etmpReturnJson: JsValue =
          Json.parse(
            """ {"safeId":"123Safe","organisationName":"ACNE LTD.","periodData":[{"periodKey":"2014",
              |"returnData":{"reliefReturnSummary":[{"formBundleNumber":"12345","dateOfSubmission":"2014-05-05","relief":"Farmhouses","reliefStartDate":"2014-09-05","reliefEndDate":"2014-10-05"}],
              |"liabilityReturnSummary":[{"propertySummary":[{"contractObject":"abc","addressLine1":"line1","addressLine2":"line2",
              |"return":[
              |{"formBundleNumber":"12345","dateOfSubmission":"2014-05-05","dateFrom":"2014-09-05","dateTo":"2014-10-05","liabilityAmount":"1000","paymentReference":"pay-123","changeAllowed":true}
              |]}]}]}}],"atedBalance":"10000"} """.stripMargin)
        val relDraft: ReliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts: Seq[ReliefsTaxAvoidance] = Seq(relDraft)
        val propDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1")
        val propDetailsSeq: Seq[PropertyDetails] = Seq(propDetails)
        val dispLiab: Seq[DisposeLiabilityReturn] = Seq(disposeLiability1)

        val years = 6

        val expected = SummaryReturnsModel(Some(10000), List(PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", None, "Liability")), None),
          PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789012", "line1 line2", None, "Dispose_Liability")),
            Some(SubmittedReturns(periodKey, List(SubmittedReliefReturns("12345", "Farmhouses", LocalDate.of(2014, 9, 5), LocalDate.of(2014, 10, 5), LocalDate.of(2014, 5, 5), None, None)),
              List(SubmittedLiabilityReturns("12345", "line1 line2", 1000, LocalDate.of(2014, 9, 5), LocalDate.of(2014, 10, 5), LocalDate.of(2014, 5, 5),
                changeAllowed = true, paymentReference = "pay-123")))))))

        when(mockEtmpConnector.getSummaryReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(years))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, etmpReturnJson, Map.empty[String, Seq[String]])))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return SummaryReturnModel with drafts and submitted return when we have new and old returns  - from Mongo DB and ETMP" in new Setup {

        //TODO: if etmp reverts back to numeric, uncomment next line and comment next-to-next
        //val etmpReturnJson = Json.toJson(etmpReturn)
        val etmpReturnJson: JsValue =
          Json.parse(
            """ {"safeId":"123Safe","organisationName":"ACNE LTD.","periodData":[{"periodKey":"2014",
              |"returnData":{"reliefReturnSummary":[{"formBundleNumber":"12345","dateOfSubmission":"2014-05-05","relief":"Farmhouses","reliefStartDate":"2014-09-05","reliefEndDate":"2014-10-05"}],
              |"liabilityReturnSummary":[{"propertySummary":[{"contractObject":"abc","addressLine1":"line1","addressLine2":"line2",
              |"return":[
              |{"formBundleNumber":"12346","dateOfSubmission":"2014-05-05","dateFrom":"2014-09-05","dateTo":"2014-10-05","liabilityAmount":"1000","paymentReference":"pay-123","changeAllowed":true},
              |{"formBundleNumber":"12345","dateOfSubmission":"2014-01-01","dateFrom":"2014-09-05","dateTo":"2014-10-05","liabilityAmount":"1000","paymentReference":"pay-123","changeAllowed":false}
              |]}]}]}}],"atedBalance":"10000"} """.stripMargin)
        val relDraft: ReliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts: Seq[ReliefsTaxAvoidance] = Seq(relDraft)
        val propDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1")
        val propDetailsSeq: Seq[PropertyDetails] = Seq(propDetails)
        val dispLiab: Seq[DisposeLiabilityReturn] = Seq(disposeLiability1)

        val years = 6

        val expected = SummaryReturnsModel(Some(10000),
          List(
            PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", None, "Liability")), None),
            PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789012", "line1 line2", None, "Dispose_Liability")),
              Some(SubmittedReturns(periodKey, List(SubmittedReliefReturns("12345", "Farmhouses", LocalDate.of(2014, 9, 5), LocalDate.of(2014, 10, 5), LocalDate.of(2014, 5, 5), None, None)),
                  List(
                    SubmittedLiabilityReturns("12346", "line1 line2", 1000, LocalDate.of(2014, 9, 5), LocalDate.of(2014, 10, 5), LocalDate.of(2014, 5, 5), changeAllowed = true, paymentReference = "pay-123")
                  ),
                  List(
                    SubmittedLiabilityReturns("12345", "line1 line2", 1000, LocalDate.of(2014, 9, 5), LocalDate.of(2014, 10, 5), LocalDate.of(2014, 1, 1), changeAllowed = false, paymentReference = "pay-123")
                  )
                )
              )
            )
          )
        )

        when(mockEtmpConnector.getSummaryReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(years))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, etmpReturnJson, Map.empty[String, Seq[String]])))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return SummaryReturnModel with drafts and submitted return - from Mongo DB and ETMP - no liability or draft" in new Setup {

        val etmpReturnJson: JsValue = Json.parse("""{"safeId":"123Safe","organisationName":"organisationName","periodData":[{"periodKey":"2014","returnData":{}}],"atedBalance":"0"}""")
        val relDraft: ReliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts: Seq[ReliefsTaxAvoidance] = Seq(relDraft)
        val propDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1")
        val propDetailsSeq: Seq[PropertyDetails] = Seq(propDetails)
        val dispLiab: Seq[DisposeLiabilityReturn] = Seq(disposeLiability1)

        val years = 6

        val expected: SummaryReturnsModel = SummaryReturnsModel(Some(0), List(PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", None, "Liability")), None),
          PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789012", "line1 line2", None, "Dispose_Liability")),
            Some(SubmittedReturns(periodKey, List(), List())))))

        when(mockEtmpConnector.getSummaryReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(years))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, etmpReturnJson, Map.empty[String, Seq[String]])))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return SummaryReturnModel with drafts and submitted return - from Mongo DB and ETMP - no liabliity amount in liabilty return" in new Setup {

        val etmpReturnJson: JsValue = Json.parse("""{"safeId":"123Safe","organisationName":"organisationName","periodData":[{"periodKey":"2014","returnData":{"reliefReturnSummary":[{"formBundleNumber":"12345","dateOfSubmission":"2014-05-05","relief":"Farmhouses","reliefStartDate":"2014-09-05","reliefEndDate":"2014-10-05"}],"liabilityReturnSummary":[{}]}}],"atedBalance":"0"}""")
        val relDraft: ReliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey)
        val reliefDrafts: Seq[ReliefsTaxAvoidance] = Seq(relDraft)
        val propDetails: PropertyDetails = PropertyDetails(atedRefNo = "ated-ref-1", "123456789099", periodKey, addressProperty = PropertyDetailsBuilder.getPropertyDetailsAddress(None), calculated = None, formBundleReturn = Some(ChangeLiabilityReturnBuilder.generateFormBundleResponse(periodKey)))
        val propDetailsSeq: Seq[PropertyDetails] = Seq(propDetails)
        val dispLiab: Seq[DisposeLiabilityReturn] = Seq(disposeLiability2)

        val years = 6

        val expected: SummaryReturnsModel = SummaryReturnsModel(Some(0), List(PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "123456789099", "addr1 addr2", None, "Change_Liability"),
          DraftReturns(periodKey, "123456789012", "line1 line2", Some(1000), "Dispose_Liability")),
          Some(SubmittedReturns(periodKey, List(SubmittedReliefReturns("12345", "Farmhouses", LocalDate.of(2014, 9, 5), LocalDate.of(2014, 10, 5),
            LocalDate.of(2014, 5, 5), None, None)), List())))))

        when(mockEtmpConnector.getSummaryReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(years))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, etmpReturnJson, Map.empty[String, Seq[String]])))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return SummaryReturnModel with drafts but no ETMP data found - from Mongo DB " in new Setup {

        val relDraft: ReliefsTaxAvoidance = ReliefBuilder.reliefTaxAvoidance(atedRefNo, periodKey, Reliefs(periodKey, rentalBusiness = true))
        val reliefDrafts: Seq[ReliefsTaxAvoidance] = Seq(relDraft)
        val propDetails: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", liabilityAmount = Some(1000))
        val propDetailsSeq: Seq[PropertyDetails] = Seq(propDetails)
        val dispLiab: Seq[DisposeLiabilityReturn] = Seq(disposeLiability1)
        val years = 6

        when(mockEtmpConnector.getSummaryReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(years))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, "")))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val expected: SummaryReturnsModel = SummaryReturnsModel(None, List(PeriodSummaryReturns(2015, List(DraftReturns(2015, "1", "addr1 addr2", Some(1000), "Liability")), None),
          PeriodSummaryReturns(periodKey, List(DraftReturns(periodKey, "", "Rental businesses", None, "Relief"),
            DraftReturns(periodKey, "123456789012", "line1 line2", None, "Dispose_Liability")), None)))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }
      "return blank SummaryReturnModel no drafts and NO submitted ETMP return found, - for no matching period keys" in new Setup {
        val reliefDrafts: Seq[Nothing] = Nil
        val propDetailsSeq: Seq[Nothing] = Nil
        val dispLiab: Seq[Nothing] = Nil
        val years = 6

        val expected: SummaryReturnsModel = SummaryReturnsModel(None, Nil)

        when(mockEtmpConnector.getSummaryReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(years))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, "")))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return blank SummaryReturnModel no ETMP Return - internal server error" in new Setup {
        val reliefDrafts: Seq[Nothing] = Nil
        val propDetailsSeq: Seq[Nothing] = Nil
        val dispLiab: Seq[Nothing] = Nil
        val years = 6

        val expected: SummaryReturnsModel = SummaryReturnsModel(None, Nil)

        when(mockEtmpConnector.getSummaryReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(years))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))
        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }

      "return blank SummaryReturnModel for no drafts but no matching period key" in new Setup {
        val etmpReturnJson: JsValue = Json.parse("""{"safeId":"123Safe","organisationName":"organisationName","periodData":[],"atedBalance":"0"}""")
        val reliefDrafts: Seq[Nothing] = Nil
        val propDetailsSeq: Seq[Nothing] = Nil
        val dispLiab: Seq[Nothing] = Nil
        val years = 6

        val expected: SummaryReturnsModel = SummaryReturnsModel(None, Nil)

        when(mockEtmpConnector.getSummaryReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(years))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, etmpReturnJson, Map.empty[String, Seq[String]])))
        when(mockPropertyDetailsService.retrieveDraftPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(propDetailsSeq))
        when(mockReliefsService.retrieveDraftReliefs(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(reliefDrafts))

        when(mockDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(dispLiab))

        val result: Future[SummaryReturnsModel] = testReturnSummaryService.getFullSummaryReturns(atedRefNo)
        await(result) must be(expected)
      }
    }
  }

  "filterReturnsByOldAndNew" must {
    "return Nil if we have no Periods" in new Setup {
      val returnTuple: (Seq[SubmittedLiabilityReturns], Seq[SubmittedLiabilityReturns]) = testReturnSummaryService.filterReturnsByOldAndNew(Nil)
      returnTuple._1.isEmpty must be (true)
      returnTuple._2.isEmpty must be (true)
    }

    "return Nil if we have no returns for a property" in new Setup {
      val propertySummary: EtmpPropertySummary = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = Nil)

      val returnTuple: (Seq[SubmittedLiabilityReturns], Seq[SubmittedLiabilityReturns]) = testReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary))
      returnTuple._1.isEmpty must be (true)
      returnTuple._2.isEmpty must be (true)
    }

    "return new if we only have one return for each property" in new Setup {
      val liability1: EtmpReturn = EtmpReturn(formBundleNumber = "1", dateOfSubmission = LocalDate.of(2014, 5, 5),
            dateFrom = LocalDate.of(2014, 4, 15),
            dateTo = LocalDate.of(2015, 3, 31),
            liabilityAmount = BigDecimal(1000),
            paymentReference = "123123m",
            changeAllowed = false)
      val propertySummary1: EtmpPropertySummary = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1))

      val liability2: EtmpReturn = EtmpReturn(formBundleNumber = "2", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val propertySummary2: EtmpPropertySummary = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability2))

      val returnTuple: (Seq[SubmittedLiabilityReturns], Seq[SubmittedLiabilityReturns]) = testReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1, propertySummary2))
      returnTuple._2.isEmpty must be (true)

      returnTuple._1.size must be (2)
      returnTuple._1.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._1.find(_.formBundleNo == "2").isDefined must be (true)
    }

    "return new and old if we have multiple returns for a property" in new Setup {
      val liability1: EtmpReturn = EtmpReturn(formBundleNumber = "1", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability2: EtmpReturn = EtmpReturn(formBundleNumber = "2", dateOfSubmission = LocalDate.of(2014, 5, 6),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3: EtmpReturn = EtmpReturn(formBundleNumber = "3", dateOfSubmission = LocalDate.of(2014, 5, 4),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val propertySummary1: EtmpPropertySummary = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple: (Seq[SubmittedLiabilityReturns], Seq[SubmittedLiabilityReturns]) = testReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (1)
      returnTuple._1.find(_.formBundleNo == "2").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (2)
      returnTuple._2.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._2.find(_.formBundleNo == "3").isDefined must be (true)
    }

    "if two returns have the same date, return the one that is editable as new (last in list is  editable)" in new Setup {
      val liability1: EtmpReturn = EtmpReturn(formBundleNumber = "1", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability2: EtmpReturn = EtmpReturn(formBundleNumber = "2", dateOfSubmission = LocalDate.of(2014, 5, 4),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3: EtmpReturn = EtmpReturn(formBundleNumber = "3", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = true)
      val propertySummary1: EtmpPropertySummary = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple: (Seq[SubmittedLiabilityReturns], Seq[SubmittedLiabilityReturns]) = testReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (1)
      returnTuple._1.find(_.formBundleNo == "3").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (2)
      returnTuple._2.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._2.find(_.formBundleNo == "2").isDefined must be (true)
    }

    "if two returns have the same date, return the one that is editable as new (first in list is  editable)" in new Setup {
      val liability1: EtmpReturn = EtmpReturn(formBundleNumber = "1", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = true)
      val liability2: EtmpReturn = EtmpReturn(formBundleNumber = "2", dateOfSubmission = LocalDate.of(2014, 5, 4),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3: EtmpReturn = EtmpReturn(formBundleNumber = "3", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val propertySummary1: EtmpPropertySummary = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple: (Seq[SubmittedLiabilityReturns], Seq[SubmittedLiabilityReturns]) = testReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (1)
      returnTuple._1.find(_.formBundleNo == "1").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (2)
      returnTuple._2.find(_.formBundleNo == "2").isDefined must be (true)
      returnTuple._2.find(_.formBundleNo == "3").isDefined must be (true)
    }

    "if two returns have the same date, return the one that is editable as new : if neither is editable, put both in new" in new Setup {
      val liability1: EtmpReturn = EtmpReturn(formBundleNumber = "1", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability2: EtmpReturn = EtmpReturn(formBundleNumber = "2", dateOfSubmission = LocalDate.of(2014, 5, 4),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3: EtmpReturn = EtmpReturn(formBundleNumber = "3", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val propertySummary1: EtmpPropertySummary = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple: (Seq[SubmittedLiabilityReturns], Seq[SubmittedLiabilityReturns]) = testReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (2)
      returnTuple._1.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._1.find(_.formBundleNo == "3").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (1)
      returnTuple._2.find(_.formBundleNo == "2").isDefined must be (true)
    }

    "if two returns have the same date, return the one that is editable as new : if both are editable, put both in new" in new Setup {
      val liability1: EtmpReturn = EtmpReturn(formBundleNumber = "1", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = true)
      val liability2: EtmpReturn = EtmpReturn(formBundleNumber = "2", dateOfSubmission = LocalDate.of(2014, 5, 4),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = false)
      val liability3: EtmpReturn = EtmpReturn(formBundleNumber = "3", dateOfSubmission = LocalDate.of(2014, 5, 5),
        dateFrom = LocalDate.of(2014, 4, 15),
        dateTo = LocalDate.of(2015, 3, 31),
        liabilityAmount = BigDecimal(1000),
        paymentReference = "123123m",
        changeAllowed = true)
      val propertySummary1: EtmpPropertySummary = EtmpPropertySummary(contractObject = "", titleNumber = None, addressLine1 = "1", addressLine2 = "2", `return` = List(liability1, liability2, liability3))


      val returnTuple: (Seq[SubmittedLiabilityReturns], Seq[SubmittedLiabilityReturns]) = testReturnSummaryService.filterReturnsByOldAndNew(List(propertySummary1))

      returnTuple._1.size must be (2)
      returnTuple._1.find(_.formBundleNo == "1").isDefined must be (true)
      returnTuple._1.find(_.formBundleNo == "3").isDefined must be (true)

      returnTuple._2.isEmpty must be (false)
      returnTuple._2.size must be (1)
      returnTuple._2.find(_.formBundleNo == "2").isDefined must be (true)
    }
  }
}
