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

import connectors.{EmailConnector, EmailNotSent, EmailStatus}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.{ExecutionContext, Future}

trait NotificationService  {

  implicit val ec: ExecutionContext
  def emailConnector: EmailConnector

  def sendMail(subscriptionData: JsValue, template: String, reference: Map[String, String] = Map.empty)(implicit hc: HeaderCarrier): Future[EmailStatus] = {

    (subscriptionData \\ "emailAddress").headOption.map {
      emailAddressJson =>
        val emailAddress = emailAddressJson.as[String]
        val companyName = (subscriptionData \ "organisationName").as[String]
        val params = Map(
          "first_name" -> "",
          "last_name" -> "customer",
          "company_name" -> companyName,
          "date" -> LocalDate.now().format(DateTimeFormatter.ofPattern("d LLLL yyyy")))
        emailConnector.sendTemplatedEmail(
          emailAddress = emailAddress,
          templateName = template,
          params = params ++ reference
        )
    }.getOrElse(Future.successful(EmailNotSent))
  }
}
