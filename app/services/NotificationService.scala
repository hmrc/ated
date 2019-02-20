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

import connectors.{EmailConnector, EmailNotSent, EmailStatus}
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.JsValue

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait NotificationService {

  def emailConnector: EmailConnector

  def sendMail(subscriptionData: JsValue, template: String, reference: Map[String, String] = Map.empty)(implicit hc: HeaderCarrier): Future[EmailStatus] = {

    val emailAddressJson = (subscriptionData \\ "emailAddress").headOption

    emailAddressJson match {
      case Some(x) =>

        val emailAddress = x.as[String]
        val companyName = (subscriptionData \ "organisationName").as[String]
        val params = Map("company_name" -> companyName,
          "date" -> DateTimeFormat.forPattern("d MMMM yyyy").print(new LocalDate()))

        emailConnector.sendTemplatedEmail(emailAddress, template, params = params ++ reference)
      case _ => Future.successful(EmailNotSent)
    }
  }
}
