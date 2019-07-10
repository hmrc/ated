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

package connectors

import javax.inject.Inject
import play.api.http.Status._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthConnectorImpl @Inject()(val servicesConfig: ServicesConfig,
                                  val http: HttpClient) extends AuthConnector {
  val serviceUrl = servicesConfig.baseUrl("auth")
  val authorityUri = "auth/authority"
}

trait AuthConnector extends RawResponseReads {
  def serviceUrl: String
  def authorityUri: String
  def http: HttpClient

  def agentReferenceNo(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val getUrl = s"""$serviceUrl/$authorityUri"""
    http.GET[HttpResponse](getUrl) map { response =>
      response.status match {
        case OK =>
          (response.json \ "accounts" \ "ated" \ "utr").asOpt[String] match {
           case None => (response.json \ "accounts" \ "agent" \ "agentBusinessUtr").asOpt[String]
           case _ => None
         }
        case status => None
      }
    }
  }

}
