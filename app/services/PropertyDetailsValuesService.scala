/*
 * Copyright 2021 HM Revenue & Customs
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
import repository.{PropertyDetailsMongoRepository, PropertyDetailsMongoWrapper}
import uk.gov.hmrc.auth.core.AuthConnector
import utils.ReliefConstants

import scala.concurrent.{ExecutionContext, Future}

class PropertyDetailsValuesServiceImpl @Inject()(val etmpConnector: EtmpReturnsConnector,
                                                 val authConnector: AuthConnector,
                                                 val propertyDetails: PropertyDetailsMongoWrapper) extends PropertyDetailsValuesService {
  lazy val propertyDetailsCache: PropertyDetailsMongoRepository = propertyDetails()
}

trait PropertyDetailsValuesService extends ReliefConstants {

  def etmpConnector: EtmpReturnsConnector

  def authConnector: AuthConnector

  def propertyDetailsCache: PropertyDetailsMongoRepository

  def cacheDraftPropertyDetailsOwnedBefore(atedRefNo: String, id: String, updatedDetails: PropertyDetailsOwnedBefore)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          val updatedValued = foundPropertyDetails.value match {
            case Some(_) =>
              foundPropertyDetails.value.map(_.copy(
                isOwnedBeforePolicyYear = updatedDetails.isOwnedBeforePolicyYear,
                ownedBeforePolicyYearValue = updatedDetails.ownedBeforePolicyYearValue
              ))
            case None => Some(PropertyDetailsValue(isOwnedBeforePolicyYear = updatedDetails.isOwnedBeforePolicyYear, ownedBeforePolicyYearValue = updatedDetails.ownedBeforePolicyYearValue))
          }
          foundPropertyDetails.copy(value = updatedValued, calculated = None)
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }


  def cacheDraftHasValueChanged(atedRefNo: String, id: String, newValue: Boolean)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (foundPropertyDetails.value.flatMap(_.hasValueChanged).contains(newValue)) {
            foundPropertyDetails
          }
          else {
            val updatedValue = foundPropertyDetails.value.map(_.copy(hasValueChanged = Some(newValue)))
            foundPropertyDetails.copy(value = updatedValue, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }


  def cacheDraftPropertyDetailsAcquisition(atedRefNo: String, id: String, newValue: Boolean)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (foundPropertyDetails.value.flatMap(_.anAcquisition).contains(newValue)) {
            foundPropertyDetails
          } else {
            val updatedvalues = foundPropertyDetails.value.map(_.copy(
              anAcquisition = Some(newValue),
              isPropertyRevalued = None,
              revaluedDate = None,
              revaluedValue = None,
              partAcqDispDate = None
            ))
            foundPropertyDetails.copy(value = updatedvalues, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftPropertyDetailsRevalued(atedRefNo: String, id: String, updatedDetails: PropertyDetailsRevalued)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (updatedDetails.isPropertyRevalued == foundPropertyDetails.value.flatMap(_.isPropertyRevalued) &&
            updatedDetails.revaluedDate == foundPropertyDetails.value.flatMap(_.revaluedDate) &&
            updatedDetails.revaluedValue == foundPropertyDetails.value.flatMap(_.revaluedValue) &&
            updatedDetails.partAcqDispDate == foundPropertyDetails.value.flatMap(_.partAcqDispDate)) {
            foundPropertyDetails
          } else {
            val updatedValued = foundPropertyDetails.value.map(_.copy(
              isPropertyRevalued = updatedDetails.isPropertyRevalued,
              revaluedDate = updatedDetails.revaluedDate,
              revaluedValue = updatedDetails.revaluedValue,
              partAcqDispDate = updatedDetails.partAcqDispDate
            ))
            foundPropertyDetails.copy(value = updatedValued, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftPropertyDetailsIsNewBuild(atedRefNo: String, id: String, updatedDetails: PropertyDetailsIsNewBuild)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (updatedDetails.isNewBuild == foundPropertyDetails.value.flatMap(_.isNewBuild)) {
            foundPropertyDetails
          }
          else {
            val updatedValued = foundPropertyDetails.value.map(_.copy(
              isNewBuild = updatedDetails.isNewBuild
            ))
            foundPropertyDetails.copy(value = updatedValued, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftPropertyDetailsNewBuildDates(atedRefNo: String, id: String, updatedDetails: PropertyDetailsNewBuildDates)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (updatedDetails.newBuildOccupyDate == foundPropertyDetails.value.flatMap(_.newBuildDate) &&
            updatedDetails.newBuildRegisterDate == foundPropertyDetails.value.flatMap(_.localAuthRegDate)) {
            foundPropertyDetails
          }
          else {
            val updatedValue = foundPropertyDetails.value.map(_.copy(
              newBuildDate = updatedDetails.newBuildOccupyDate,
              localAuthRegDate = updatedDetails.newBuildRegisterDate
            ))
            foundPropertyDetails.copy(value = updatedValue, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)

  }

  def cacheDraftPropertyDetailsNewBuildValue(atedRefNo: String, id: String, updatedDetails: PropertyDetailsNewBuildValue)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (updatedDetails.newBuildValue == foundPropertyDetails.value.flatMap(_.newBuildValue)) {
            foundPropertyDetails
          } else {
            val updatedValue = foundPropertyDetails.value.map(_.copy(
              newBuildValue = updatedDetails.newBuildValue
            ))
            foundPropertyDetails.copy(value = updatedValue, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftPropertyDetailsValueAcquired(atedRefNo: String, id: String, updatedDetails: PropertyDetailsValueOnAcquisition)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (updatedDetails.acquiredValue == foundPropertyDetails.value.flatMap(_.notNewBuildValue)) {
            foundPropertyDetails
          } else {
            val updatedValue = foundPropertyDetails.value.map(_.copy(
              notNewBuildValue = updatedDetails.acquiredValue
            ))
            foundPropertyDetails.copy(value = updatedValue, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)

  }

  def cacheDraftPropertyDetailsDatesAcquired(atedRefNo: String, id: String, updatedDetails: PropertyDetailsDateOfAcquisition)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (updatedDetails.acquiredDate == foundPropertyDetails.value.flatMap(_.notNewBuildDate)) {
            foundPropertyDetails
          } else {
            val updatdeValue = foundPropertyDetails.value.map(_.copy(
              notNewBuildDate = updatedDetails.acquiredDate
            ))
            foundPropertyDetails.copy(value = updatdeValue, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  def cacheDraftPropertyDetailsProfessionallyValued(atedRefNo: String, id: String, updatedDetails: PropertyDetailsProfessionallyValued)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {

    def updatePropertyDetails(propertyDetailsList: Seq[PropertyDetails]): Future[Option[PropertyDetails]] = {
      val updatedPropertyDetails = propertyDetailsList.find(_.id == id).map {
        foundPropertyDetails =>
          if (updatedDetails.isValuedByAgent == foundPropertyDetails.value.flatMap(_.isValuedByAgent)) {
            foundPropertyDetails
          }
          else {
            val updatedValued = foundPropertyDetails.value.map(_.copy(
              isValuedByAgent = updatedDetails.isValuedByAgent
            ))
            foundPropertyDetails.copy(value = updatedValued, calculated = None)
          }
      }
      Future.successful(updatedPropertyDetails)
    }

    cacheDraftPropertyDetails(atedRefNo, updatePropertyDetails)
  }

  private def cacheDraftPropertyDetails(atedRefNo: String, updatePropertyDetails: Seq[PropertyDetails] => Future[Option[PropertyDetails]])(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {
    for {
      propertyDetailsList <- propertyDetailsCache.fetchPropertyDetails(atedRefNo)
      newPropertyDetails <- updatePropertyDetails(propertyDetailsList)
      cacheResponse <- newPropertyDetails match {
        case Some(x) =>
          val filteredPropertyDetailsList = propertyDetailsList.filterNot(_.id == x.id)
          val updatedList = filteredPropertyDetailsList :+ x
          updatedList.map { updateProp =>
            propertyDetailsCache.cachePropertyDetails(updateProp).map(_ => newPropertyDetails)
          }.head
        case None => Future.successful(None)
      }
    } yield cacheResponse
  }

}
