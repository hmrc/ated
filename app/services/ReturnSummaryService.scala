/*
 * Copyright 2020 HM Revenue & Customs
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

import connectors.EtmpReturnsConnector
import javax.inject.Inject
import models._
import org.joda.time.LocalDate
import play.api.http.Status._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.AtedConstants._
import utils.ReliefUtils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnSummaryServiceImpl @Inject()(val etmpConnector: EtmpReturnsConnector,
                                         val propertyDetailsService: PropertyDetailsService,
                                         val reliefsService: ReliefsService,
                                         val disposeLiabilityReturnService: DisposeLiabilityReturnService) extends ReturnSummaryService

trait ReturnSummaryService {

  def etmpConnector: EtmpReturnsConnector
  def propertyDetailsService: PropertyDetailsService
  def reliefsService: ReliefsService
  def disposeLiabilityReturnService: DisposeLiabilityReturnService

  val years: Int = 6

  private def createDraftReturns(reliefDrafts: Seq[ReliefsTaxAvoidance], liabilityDrafts: Seq[PropertyDetails],
                                 disposeLiabilityDrafts: Seq[DisposeLiabilityReturn]): Seq[DraftReturns] = {
    val reliefDraftSeq: Seq[DraftReturns] = reliefDrafts.map { a =>
      convertDraftReliefsToDraftDescription(a).map(b =>
        DraftReturns(
          a.periodKey,
          "",
          b,
          None,
          TypeReliefDraft
        )
      )
    }.fold(Nil)((accumulatorList, actualList) => accumulatorList ++ actualList)

    val liabilityDraftSeq: Seq[DraftReturns] = liabilityDrafts.map { x =>
      DraftReturns(
        x.periodKey,
        x.id.toString,
        x.addressProperty.line_1 + " " + x.addressProperty.line_2,
        x.calculated.flatMap(_.liabilityAmount),
        if (x.formBundleReturn.isDefined && x.id.length == 12){
          TypeChangeLiabilityDraft
        } else {
          TypeLiabilityDraft
        }
      )
                              // 12 chars is the length of the formBundleNo,
                              // if it was 10 then it would be a return created from a previous year
    }

    val disposeLiabilityDraftsSeq: Seq[DraftReturns] = disposeLiabilityDrafts.map { x =>
      DraftReturns(
        x.formBundleReturn.periodKey.trim.toInt,
        x.id.toString,
        x.formBundleReturn.propertyDetails.address.addressLine1 + " " + x.formBundleReturn.propertyDetails.address.addressLine2,
        x.calculated.map(_.liabilityAmount),
        TypeDisposeLiabilityDraft
      )
    }
    reliefDraftSeq ++ liabilityDraftSeq ++ disposeLiabilityDraftsSeq
  }

  def getPartialSummaryReturn(atedRef: String)(implicit hc: HeaderCarrier): Future[SummaryReturnsModel] = {
    val reliefDraftsFuture = reliefsService.retrieveDraftReliefs(atedRef)
    val liabilityDraftsFuture = propertyDetailsService.retrieveDraftPropertyDetails(atedRef)
    val disposeLiabilityDraftsFuture = disposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(atedRef)

    for {
      reliefDrafts    <- reliefDraftsFuture
      liabilityDrafts <- liabilityDraftsFuture
      disposeLiabilityDrafts <- disposeLiabilityDraftsFuture
    } yield {
      val allDraftReturns     = createDraftReturns(reliefDrafts, liabilityDrafts, disposeLiabilityDrafts)
      val draftReturnsPeriods = allDraftReturns.map(_.periodKey)
      val allPeriodsInSeq     = draftReturnsPeriods
      if (allPeriodsInSeq.nonEmpty) {
        val allPeriods = allPeriodsInSeq.distinct.sortWith(_ > _)
        val periodSummaryReturns: Seq[PeriodSummaryReturns] = allPeriods.map { pk =>
          val draftReturns = allDraftReturns.filter(_.periodKey == pk)
          PeriodSummaryReturns(pk, draftReturns, None)
        }
        SummaryReturnsModel(None, periodSummaryReturns)
      } else {
        SummaryReturnsModel(None, Nil) //means we did call to api9 - found 404, also no drafts at all
      }
    }
  }

  def getFullSummaryReturns(atedRef: String)(implicit hc: HeaderCarrier): Future[SummaryReturnsModel] = {
    val etmpReturnsFuture = etmpConnector.getSummaryReturns(atedRef, years)
    val reliefDraftsFuture = reliefsService.retrieveDraftReliefs(atedRef)
    val liabilityDraftsFuture = propertyDetailsService.retrieveDraftPropertyDetails(atedRef)
    val disposeLiabilityDraftsFuture = disposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(atedRef)

    def extractEtmpReturns(etmpResponse: HttpResponse): Option[EtmpGetReturnsResponse] = {
      etmpResponse.status match {
        case OK => etmpResponse.json.asOpt[EtmpGetReturnsResponse]
        case _  => None
      }
    }
    for {
      etmpReturnsResponse    <- etmpReturnsFuture
      reliefDrafts           <- reliefDraftsFuture
      liabilityDrafts        <- liabilityDraftsFuture
      disposeLiabilityDrafts <- disposeLiabilityDraftsFuture
    } yield {
      val allDraftReturns = createDraftReturns(reliefDrafts, liabilityDrafts, disposeLiabilityDrafts)
      val etmpReturns = extractEtmpReturns(etmpReturnsResponse)

      etmpReturns match {
        case Some(x) => createSubmittedReturnsFromEtmpAndDraft(x, allDraftReturns)
        case None => {
          val draftReturnsPeriods = allDraftReturns.map(_.periodKey)
          val allPeriodsInSeq = draftReturnsPeriods
          if (allPeriodsInSeq.nonEmpty) {
            val allPeriods = allPeriodsInSeq.distinct.sortWith(_ > _)
            val periodSummaryReturns: Seq[PeriodSummaryReturns] = allPeriods.map { pk =>
              val draftReturns = allDraftReturns.filter(_.periodKey == pk)
              PeriodSummaryReturns(pk, draftReturns, None)
            }
            SummaryReturnsModel(None, periodSummaryReturns)
          } else {
            SummaryReturnsModel(None, Nil) //means we did call to api9 - found 404, also no drafts at all
          }
        }
      }
    }
  }

  def filterReturnsByOldAndNew(etmpPropertySummary: Seq[EtmpPropertySummary]): (Seq[SubmittedLiabilityReturns], Seq[SubmittedLiabilityReturns]) = {
    implicit val localDateOrdering: Ordering[LocalDate] = Ordering.by(_.toDate.getTime)
    implicit val returnOrdering: Ordering[EtmpReturn]   = Ordering.by{etmp: EtmpReturn => (etmp.dateOfSubmission, etmp.changeAllowed)}

    val liabilityReturnsTuple = etmpPropertySummary.map{ property =>
        val address = property.addressLine1 + " " + property.addressLine2
//        val latestDate = property.`return`.headOption.map(_.dateOfSubmission)
        val orderedReturns = property.`return`.sorted.reverse.map(f =>
          SubmittedLiabilityReturns(
            f.formBundleNumber,
            address,
            f.liabilityAmount,
            f.dateFrom,
            f.dateTo,
            f.dateOfSubmission,
            f.changeAllowed,
            f.paymentReference
          )
        )
        orderedReturns match {
          case head :: second :: tail =>
            (head.dateOfSubmission == second.dateOfSubmission, head.changeAllowed, second.changeAllowed) match {
              case (true, true, false)  => (List(head), second :: tail)
              case (true, true, true)   => (List(head, second), tail)
              case (true, false, false) => (List(head, second), tail)
              case _                    => (List(head), second :: tail)
            }
          case head :: tail => (List(head), tail)
          case _ => (Nil, Nil)
        }
    }

    val currentLiabilityReturns = liabilityReturnsTuple.flatMap { case (current, _) => current }
    val oldLiabilityReturns     = liabilityReturnsTuple.flatMap { case (_, past) => past }
    (currentLiabilityReturns, oldLiabilityReturns)
  }

  def createSubmittedReturnsFromEtmpAndDraft(x: EtmpGetReturnsResponse, allDraftReturns: Seq[DraftReturns]): SummaryReturnsModel = {
    def convertReliefs(reliefs: Option[Seq[EtmpReliefReturnsSummary]]): Seq[SubmittedReliefReturns] = {
      reliefs.fold(Nil: Seq[SubmittedReliefReturns])(_.map { c =>
        SubmittedReliefReturns(
          c.formBundleNumber,
          atedReliefNameForEtmpReliefName(c.relief),
          c.reliefStartDate,
          c.reliefEndDate,
          c.dateOfSubmission,
          c.taxAvoidanceScheme,
          c.taxAvoidancePromoterReference
        )
      })
    }

    val summaryReturns: Seq[SubmittedReturns] = x.periodData.map { a =>
      val submittedReliefs: Seq[SubmittedReliefReturns]     = convertReliefs(a.returnData.reliefReturnSummary)
      val liabilityDataSeq: Seq[EtmpLiabilityReturnSummary] = a.returnData.liabilityReturnSummary.getOrElse(Nil)
      val etmpPropertySummary: Seq[EtmpPropertySummary]     = liabilityDataSeq.map(_.propertySummary.getOrElse(Nil)).fold(Nil)(_ ++ _)
      val (newReturns, oldReturns) = filterReturnsByOldAndNew(etmpPropertySummary)
      val periodKey = a.periodKey.trim.toInt

      SubmittedReturns(periodKey, submittedReliefs, newReturns, oldReturns)
    }
    groupReturnsByPeriod(x.atedBalance, summaryReturns, allDraftReturns)
  }

  def groupReturnsByPeriod(atedBalance: BigDecimal, submittedReturns: Seq[SubmittedReturns], allDraftReturns: Seq[DraftReturns]): SummaryReturnsModel = {
    def createPeriodSummaryReturns(allPeriods: Seq[Int]): Seq[PeriodSummaryReturns] = {
      val allPeriodsSorted = allPeriods.distinct.sortWith(_ > _)
      allPeriodsSorted.map { pk =>
        val submittedPeriodReturns = submittedReturns.find(_.periodKey == pk)
        val draftReturns = allDraftReturns.filter(_.periodKey == pk)
        PeriodSummaryReturns(pk, draftReturns, submittedPeriodReturns)
      }
    }

    val submittedReturnPeriods = submittedReturns.map(_.periodKey)
    val draftReturnsPeriods    = allDraftReturns.map(_.periodKey)
    val allPeriodsInSeq        = submittedReturnPeriods ++ draftReturnsPeriods
    if (allPeriodsInSeq.nonEmpty) {
      createPeriodSummaryReturns(allPeriodsInSeq)
      SummaryReturnsModel(Some(atedBalance), createPeriodSummaryReturns(allPeriodsInSeq))
    } else {
      SummaryReturnsModel(None, Nil) //means we did call to api9 - found 404, also no drafts at all
    }
  }
}
