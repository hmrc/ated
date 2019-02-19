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

package models

import org.joda.time.LocalDate
import play.api.libs.json.Json

case class RegisteredAddressDetails(addressLine1: String,
                                    addressLine2: String,
                                    addressLine3: Option[String]=None,
                                    addressLine4: Option[String]=None,
                                    postalCode: Option[String]=None,
                                    countryCode: String)

object RegisteredAddressDetails {
  implicit val formats = Json.format[RegisteredAddressDetails]
}

case class Individual(firstName: String,
                      middleName: Option[String],
                      laseName: String,
                      dateOfBirth: LocalDate)

object Individual {
  implicit val formats = Json.format[Individual]
}


case class Organisation(organisationName: String)

object Organisation {
  implicit val formats = Json.format[Organisation]
}

case class Identification(idNumber: String, issuingInstitution: String, issuingCountryCode: String)

object Identification {
  implicit val formats = Json.format[Identification]
}

case class UpdateRegistrationDetailsRequest(acknowledgementReference: Option[String],
                                           isAnIndividual: Boolean,
                                           individual: Option[Individual],
                                           organisation: Option[Organisation],
                                           address: RegisteredAddressDetails,
                                           contactDetails: ContactDetails,
                                           isAnAgent: Boolean,
                                           isAGroup: Boolean,
                                           identification: Option[Identification] = None)

object UpdateRegistrationDetailsRequest {
  implicit val formats = Json.format[UpdateRegistrationDetailsRequest]
}
