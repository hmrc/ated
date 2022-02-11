/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json, OFormat}
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import play.api.libs.json.JodaReads._
import play.api.libs.json.JodaWrites._

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
  val formats: OFormat[DisposeLiability] = {
    Json.format[DisposeLiability]
  }
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
  def formats(implicit crypto: CompositeSymmetricCrypto): OFormat[DisposeLiabilityReturn] = {
    implicit val disposeLiabilityFormat: OFormat[DisposeLiability] = DisposeLiability.formats
    implicit val bankDetailsModelFormat: Format[BankDetailsModel] = BankDetailsModel.format

    Json.format[DisposeLiabilityReturn]
  }

  def mongoFormats(implicit crypto: CompositeSymmetricCrypto): OFormat[DisposeLiabilityReturn] = {
    implicit val bankDetailsModelFormat: Format[BankDetailsModel] = BankDetailsModel.format
    implicit val disposeLiabilityFormat: OFormat[DisposeLiability] = DisposeLiability.formats

    import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits.jotDateTimeFormat

    Json.format[DisposeLiabilityReturn]
  }
}
