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

import play.api.libs.json.Json
import org.joda.time.{DateTime, DateTimeZone, LocalDate}

case class PendingClient(
                          atedReferenceNo: String,
                          clientName: Option[String] = None,
                          expiryDate: Option[LocalDate] = None,
                          clientRejected: Boolean = false,
                          clientRejectedDate: Option[LocalDate] = None
                        )

object PendingClient {
  implicit val formats = Json.format[PendingClient]
}

case class PendingAgent(
                         agentReferenceNo: String,
                         agentName: String,
                         atedReferenceNo: String,
                         rejected: Boolean = false
                       )

object PendingAgent {
  implicit val formats = Json.format[PendingAgent]
}

case class SavePendingClientRequest(
                                     agentReferenceNo: String,
                                     pendingClient: PendingClient,
                                     pendingAgent: PendingAgent
                                   )

object SavePendingClientRequest {
  implicit val formats = Json.format[SavePendingClientRequest]
}

case class ClientsAgent(
                         arn: String,
                         atedRefNo: String,
                         agentName: String,
                         agentRejected: Boolean = false,
                         isEtmpData: Boolean = false
                       )

object ClientsAgent {
  implicit val formats = Json.format[ClientsAgent]
}

case class Client(atedReferenceNo: String, clientName: String)

// single client for an agent

object Client {
  implicit val formats = Json.format[Client]
}

case class DisposeLiability(dateOfDisposal: Option[LocalDate] = None, periodKey: Int)

object DisposeLiability {
  implicit val formats = Json.format[DisposeLiability]
}

case class DisposeCalculated(liabilityAmount: BigDecimal, amountDueOrRefund: BigDecimal)

object DisposeCalculated {
  implicit val formats = Json.format[DisposeCalculated]
}

case class DisposeLiabilityReturn(atedRefNo: String,
                                  id: String,
                                  formBundleReturn: FormBundleReturn,
                                  disposeLiability: Option[DisposeLiability] = None,
                                  calculated: Option[DisposeCalculated] = None,
                                  bankDetails: Option[BankDetailsModel] = None,
                                  timeStamp: DateTime = DateTime.now(DateTimeZone.UTC))

object DisposeLiabilityReturn {
  implicit val formats = Json.format[DisposeLiabilityReturn]
}
