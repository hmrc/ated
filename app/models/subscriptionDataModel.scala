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

package models

import play.api.libs.json.{Json, OFormat}

case class ContactDetails(phoneNumber: Option[String] = None,
                          mobileNumber: Option[String] = None,
                          faxNumber: Option[String] = None,
                          emailAddress: Option[String] = None)

object ContactDetails {
  implicit val formats: OFormat[ContactDetails] = Json.format[ContactDetails]
}

case class AddressDetails(addressType: String,
                          addressLine1: String,
                          addressLine2: String,
                          addressLine3: Option[String] = None,
                          addressLine4: Option[String] = None,
                          postalCode: Option[String] = None,
                          countryCode: String)

object AddressDetails {
  implicit val formats: OFormat[AddressDetails] = Json.format[AddressDetails]
}

case class Address(name1: Option[String] = None,
                   name2: Option[String] = None,
                   addressDetails: AddressDetails,
                   contactDetails: Option[ContactDetails] = None)

object Address {
  implicit val formats: OFormat[Address] = Json.format[Address]
}


case class ChangeIndicators(nameChanged: Boolean = false,
                            permanentPlaceOfBusinessChanged: Boolean = false,
                            correspondenceChanged: Boolean = false,
                            contactDetailsChanged: Boolean = false)

object ChangeIndicators {
  implicit val formats: OFormat[ChangeIndicators] = Json.format[ChangeIndicators]
}

case class UpdateSubscriptionDataRequest(emailConsent: Boolean, changeIndicators: ChangeIndicators, address: Seq[Address])

object UpdateSubscriptionDataRequest {
  implicit val formats: OFormat[UpdateSubscriptionDataRequest] = Json.format[UpdateSubscriptionDataRequest]
}

case class UpdateEtmpSubscriptionDataRequest(acknowledgementReference: String,
                                             emailConsent: Boolean,
                                             changeIndicators: ChangeIndicators,
                                             agentReferenceNumber: Option[String],
                                             address: Seq[Address])

object UpdateEtmpSubscriptionDataRequest {
  implicit val formats: OFormat[UpdateEtmpSubscriptionDataRequest] = Json.format[UpdateEtmpSubscriptionDataRequest]
}
