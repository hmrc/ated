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

import connectors.{AuthConnector, EtmpReturnsConnector}
import models._
import org.joda.time.LocalDate
import repository.PropertyDetailsMongoRepository
import utils.ReliefConstants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait PropertyDetailsPeriodService extends ReliefConstants {

  def etmpConnector: EtmpReturnsConnector

  def authConnector: AuthConnector

  def propertyDetailsCache: PropertyDetailsMongoRepository

  def cacheDraftFullTaxPeriod(atedRefNo: String, id: String, updatedDetails: IsFullTaxPeriod)
                             (implicit hc: HeaderCarrier): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (Some(updatedDetails.isFullPeriod) == foundPropertyDetails.period.flatMap(_.isFullPeriod))
          //Don't update anything
            foundPropertyDetails
          else {
            //Clear down the values
            val liabilityPeriods = updatedDetails.datesLiable match {
              case Some(x) =>
                List[LineItem](new LineItem(lineItemType = TypeLiability, startDate = x.startDate, endDate = x.endDate, None))
              case None => List[LineItem]()
            }
            val updatedperiods = Some(PropertyDetailsPeriod(isFullPeriod = Some(updatedDetails.isFullPeriod), liabilityPeriods = liabilityPeriods))
            foundPropertyDetails.copy(period = updatedperiods, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftInRelief(atedRefNo: String, id: String, updatedDetails: PropertyDetailsInRelief)
                        (implicit hc: HeaderCarrier): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          val updatePeriod = foundPropertyDetails.period.map(foundPeriod =>
            foundPeriod.copy(isInRelief = updatedDetails.isInRelief)
          )
          foundPropertyDetails.copy(period = updatePeriod, calculated = None)
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }


  def cacheDraftDatesLiable(atedRefNo: String, id: String, updatedDetails: PropertyDetailsDatesLiable)
                           (implicit hc: HeaderCarrier): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      def lineItemMatches(lineItem: LineItem) = {
        (lineItem.startDate == updatedDetails.startDate &&
          lineItem.endDate == updatedDetails.endDate &&
          lineItem.lineItemType == TypeLiability)
      }

      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>

          val oldLiabilityPeriods = foundPropertyDetails.period.map(_.liabilityPeriods).getOrElse(Nil)
          (oldLiabilityPeriods.size, oldLiabilityPeriods.headOption) match {
            case (1, Some(x)) if (lineItemMatches(x)) => foundPropertyDetails
            case _ =>
              val newLineItem = new LineItem(lineItemType = TypeLiability,
                startDate = updatedDetails.startDate,
                endDate = updatedDetails.endDate,
                None
              )

              val updatedPeriod = foundPropertyDetails.period.map(_.copy(
                liabilityPeriods = List(newLineItem),
                reliefPeriods = Nil,
                isFullPeriod = Some(false)
              ))
              foundPropertyDetails.copy(period = updatedPeriod, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }


  def addDraftDatesLiable(atedRefNo: String, id: String, updatedDetails: PropertyDetailsDatesLiable)
                         (implicit hc: HeaderCarrier): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {

      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>

          val updatedPeriod = foundPropertyDetails.period.map {
            foundPeriod =>
              val newLineItem = new LineItem(lineItemType = TypeLiability,
                startDate = updatedDetails.startDate,
                endDate = updatedDetails.endDate
              )

              val updatedReliefPeriods = foundPeriod.reliefPeriods.filter(p => p.startDate != updatedDetails.startDate)
              val updatedLiabilityPeriods = foundPeriod.liabilityPeriods.filter(p => p.startDate != updatedDetails.startDate)
              foundPeriod.copy(reliefPeriods = updatedReliefPeriods, liabilityPeriods = newLineItem :: updatedLiabilityPeriods, isFullPeriod = Some(false))
          }
          foundPropertyDetails.copy(period = updatedPeriod, calculated = None)
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }


  def addDraftDatesInRelief(atedRefNo: String, id: String, updatedDetails: PropertyDetailsDatesInRelief)
                           (implicit hc: HeaderCarrier): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {

      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>

          val updatedPeriod = foundPropertyDetails.period.map {
            foundPeriod =>
              val newLineItem = new LineItem(lineItemType = TypeRelief,
                startDate = updatedDetails.startDate,
                endDate = updatedDetails.endDate,
                description = updatedDetails.description
              )

              val updatedReliefPeriods = foundPeriod.reliefPeriods.filter(p => p.startDate != updatedDetails.startDate)
              val updatedLiabilityPeriods = foundPeriod.liabilityPeriods.filter(p => p.startDate != updatedDetails.startDate)
              foundPeriod.copy(reliefPeriods = newLineItem :: updatedReliefPeriods, liabilityPeriods = updatedLiabilityPeriods, isFullPeriod = Some(false))
          }
          foundPropertyDetails.copy(period = updatedPeriod, calculated = None)
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }


  def deleteDraftPeriod(atedRefNo: String, id: String, periodStartDate: LocalDate)
                       (implicit hc: HeaderCarrier): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {

      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>

          val updatedPeriod = foundPropertyDetails.period.map {
            foundPeriod =>
              val updatedReliefPeriods = foundPeriod.reliefPeriods.filter(p => p.startDate != periodStartDate)
              val updatedLiabilityPeriods = foundPeriod.liabilityPeriods.filter(p => p.startDate != periodStartDate)
              foundPeriod.copy(reliefPeriods = updatedReliefPeriods, liabilityPeriods = updatedLiabilityPeriods, isFullPeriod = Some(false))
          }
          foundPropertyDetails.copy(period = updatedPeriod, calculated = None)
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }


  private def cacheDraftPropertyDetails(atedRefNo: String, updatePropertyDetails: Seq[PropertyDetails] => Future[Option[PropertyDetails]])
                                       (implicit hc: HeaderCarrier): Future[Option[PropertyDetails]] = {
    for {
      propertyDetailsList <- propertyDetailsCache.fetchPropertyDetails(atedRefNo)
      newPropertyDetails <- updatePropertyDetails(propertyDetailsList)
      cacheResponse <- newPropertyDetails match {
        case Some(x) =>
          val filteredPropertyDetailsList = propertyDetailsList.filterNot(_.id == x.id)
          val updatedList = filteredPropertyDetailsList :+ x
          updatedList.map { updateProp =>
            propertyDetailsCache.cachePropertyDetails(updateProp).map(_ => newPropertyDetails)}.head
        case None => Future.successful(None)
      }
    } yield cacheResponse
  }

}

object PropertyDetailsPeriodService extends PropertyDetailsPeriodService {
  val propertyDetailsCache: PropertyDetailsMongoRepository = PropertyDetailsMongoRepository()
  val etmpConnector: EtmpReturnsConnector = EtmpReturnsConnector
  val authConnector: AuthConnector = AuthConnector
}
