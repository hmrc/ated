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

import java.util.UUID

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId

import scala.concurrent.Future

class AuthConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfter {

  trait MockedVerbs extends CoreGet with CorePost
  val mockWSHttp: CoreGet with CorePost = mock[MockedVerbs]

  object TestAuthConnector extends AuthConnector {
    val serviceUrl = "auth"
    val authorityUri = ""
    val http: CoreGet with CorePost = mockWSHttp
  }

  before {
    reset(mockWSHttp)
  }

  "AuthConnector" must {

    "have a service url" in {
      AuthConnector.serviceUrl == "auth"
    }

    "agentReferenceNo" must {
      "Check that this returns the agent reference number if we have one" in {
        val successResponseJson = Json.parse( """{"accounts": {"agent": {"agentCode":"AGENT-123", "agentBusinessUtr":"JARN1234567"}}}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))

        val result = TestAuthConnector.agentReferenceNo
        val agentRefCode = await(result)

        agentRefCode must be(Some("JARN1234567"))
      }

      "Check that this returns None if we don't have one" in {
        val successResponseJson = Json.parse( """{"accounts": {"agent": {"payeReference":"PAYE-123"}}}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))

        val result = TestAuthConnector.agentReferenceNo
        val agentRefCode = await(result)
        agentRefCode.isDefined must be(false)
      }
      "Check that this returns None if the agent is also a client" in {
        val successResponseJson = Json.parse( """{"uri":"/auth/oid/57ee34b44800004800d0b292","confidenceLevel":50,"credentialStrength":"weak","userDetailsLink":"http://localhost:9978/user-details/id/5835790e2000006a00f3c888","legacyOid":"57ee34b44800004800d0b292","new-session":"/auth/oid/57ee34b44800004800d0b292/session","ids":"/auth/oid/57ee34b44800004800d0b292/ids","credentials":{"gatewayId":"cred-id-2345235235"},"accounts":{"ated":{"utr":"XN1200000100001","link":"/ated/XN1200000100001"},"org":{"org":"9tuPYSf-1gqhPLtuqKZhFC2YNiI","link":"/org/9tuPYSf-1gqhPLtuqKZhFC2YNiI"}},"lastUpdated":"2017-05-10T15:19:03.602Z","loggedInAt":"2017-05-10T15:19:03.602Z","previouslyLoggedInAt":"2017-05-08T13:46:26.269Z","levelOfAssurance":"1","enrolments":"/auth/oid/57ee34b44800004800d0b292/enrolments","affinityGroup":"Organisation","correlationId":"fbd19f1b8c8d8d3d326f37f9202843fed0dc91ffcaa5a2ba49f24acc1dbd22f9","credId":"cred-id-2345235235"}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))

        val result = TestAuthConnector.agentReferenceNo
        val agentRefCode = await(result)
        agentRefCode must be(None)

      }
      "returns None if the status from Auth isn't OK" in {
        val successResponseJson = Json.parse( """{"accounts": {"agent": {"agentCode":"AGENT-123"}}}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(successResponseJson))))

        val result = TestAuthConnector.agentReferenceNo
        val agentRefCode = await(result)
        agentRefCode.isDefined must be(false)
      }

      "returns None if User doesn't have an Agent" in {
        val successResponseJson = Json.parse( """{"accounts": {"sa": {"link":"/sa/individual/1872796160", "utr": "1872796160"}}}""")
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockWSHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))

        val result = TestAuthConnector.agentReferenceNo
        val agentRefCode = await(result)
        agentRefCode.isDefined must be(false)
      }
    }
  }

}
