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

package utils

import models._
import org.joda.time.LocalDate
import play.api.Logger
import utils.PropertyDetailsUtils._


object LiabilityUtils extends LiabilityUtils

trait LiabilityUtils extends ReliefConstants {


  def createPostReturnsRequest(id: String, propertyDetails: PropertyDetails, agentRefNo: Option[String] = None): Option[SubmitEtmpReturnsRequest] = {
    createLiabilityReturnsRequest(id, propertyDetails, ReliefUtils.Post, agentRefNo)
  }

  def createPreCalculationReturnsRequest(id: String, propertyDetails: PropertyDetails, agentRefNo: Option[String] = None): Option[SubmitEtmpReturnsRequest] = {
    createLiabilityReturnsRequest(id, propertyDetails, ReliefUtils.PreCalculation, agentRefNo)
  }

  private def createLiabilityReturnsRequest(id: String, property: PropertyDetails, mode: String, agentRefNo: Option[String] = None): Option[SubmitEtmpReturnsRequest] = {

    (property.calculated.flatMap(_.valuationDateToUse), property.calculated.flatMap(_.professionalValuation), property.calculated) match {
      case (Some(valuationDate), Some(professionalValuation), Some(propertyCalc)) => {
        val liabilityReturns = createLiabilityReturns(id, property, propertyCalc, mode, agentRefNo, valuationDate, professionalValuation)
        Some(SubmitEtmpReturnsRequest(acknowledgementReference = SessionUtils.getUniqueAckNo,
          agentRefNo,
          reliefReturns = None,
          liabilityReturns = liabilityReturns))
      }
      case (None, Some(professionalValuation), Some(propertyCalc)) if (property.calculated.flatMap(_.acquistionDateToUse).isDefined) => {
        val liabilityReturns = createLiabilityReturns(id, property, propertyCalc, mode, agentRefNo, property.calculated.flatMap(_.acquistionDateToUse).get, professionalValuation)
        Some(SubmitEtmpReturnsRequest(acknowledgementReference = SessionUtils.getUniqueAckNo,
          agentRefNo,
          reliefReturns = None,
          liabilityReturns = liabilityReturns))
      }
      case _ => None
    }
  }

  private def createLiabilityReturns(id: String, property: PropertyDetails, propCalc: PropertyDetailsCalculated,
                                        mode: String, agentRefNo: Option[String] = None,
                                        valuationDate : LocalDate,
                                        professionallyValued : Boolean) = {

    val liabilityReturn = EtmpLiabilityReturns(mode = mode,
      propertyKey = id,
      periodKey = property.periodKey.toString,
      propertyDetails = Some(getEtmpPropertyDetails(property)),
      dateOfAcquisition = propCalc.acquistionDateToUse,
      valueAtAcquisition = propCalc.acquistionValueToUse,
      dateOfValuation = valuationDate,
      taxAvoidanceScheme = getTaxAvoidanceScheme(property),
      taxAvoidancePromoterReference = getTaxAvoidancePromoterReference(property),
      professionalValuation = professionallyValued,
      localAuthorityCode = None,
      ninetyDayRuleApplies = getNinetyDayRuleApplies(property),
      lineItems = getLineItems(propCalc))

    Some(List(liabilityReturn))
  }

}
