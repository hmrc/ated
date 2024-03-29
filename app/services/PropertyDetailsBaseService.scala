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


import connectors.EtmpReturnsConnector
import models._
import repository.{PropertyDetailsDelete, PropertyDetailsMongoRepository}
import uk.gov.hmrc.auth.core.AuthConnector
import utils._

import scala.concurrent.{ExecutionContext, Future}

trait PropertyDetailsBaseService extends ReliefConstants {

  def etmpConnector: EtmpReturnsConnector
  def authConnector: AuthConnector
  def propertyDetailsCache: PropertyDetailsMongoRepository

  def retrieveDraftPropertyDetails(atedRefNo: String): Future[Seq[PropertyDetails]] = {
    propertyDetailsCache.fetchPropertyDetails(atedRefNo)
  }

  def retrieveDraftPropertyDetail(atedRefNo: String, id: String)(
    implicit ec: ExecutionContext): Future[Option[PropertyDetails]] = {
    propertyDetailsCache.fetchPropertyDetails(atedRefNo).map {
      propertyDetailsList =>
        PropertyDetailsUtils.populateBankDetails(propertyDetailsList.find(_.id == id))
    }
  }

  def deleteDraftPropertyDetail(atedRefNo: String, id: String): Future[PropertyDetailsDelete] =
    propertyDetailsCache.deletePropertyDetailsByfieldName(atedRefNo, id)

  protected def cacheDraftPropertyDetails(atedRefNo: String, updatePropertyDetails: Seq[PropertyDetails] => Future[Option[PropertyDetails]])(
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
    } yield PropertyDetailsUtils.populateBankDetails(cacheResponse)
  }
}
