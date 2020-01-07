
package helpers.application

import helpers.wiremock.WireMockConfig
import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSRequest}

trait IntegrationApplication extends GuiceOneServerPerSuite with WireMockConfig {
  self: TestSuite =>

  val currentAppBaseUrl: String = "ated"
  val testAppUrl: String        = s"http://localhost:$port"

  lazy val ws: WSClient = app.injector.instanceOf[WSClient]

  val appConfig: Map[String, Any] = Map(
    "application.router"                                  -> "testOnlyDoNotUseInAppConf.Routes",
    "microservice.metrics.graphite.host"                  -> "localhost",
    "microservice.metrics.graphite.port"                  -> 2003,
    "microservice.metrics.graphite.prefix"                -> "play.ated.",
    "microservice.metrics.graphite.enabled"               -> true,
    "microservice.services.etmp-hod.host"                 -> wireMockHost,
    "microservice.services.etmp-hod.port"                 -> wireMockPort,
    "microservice.services.datastream.host"               -> wireMockHost,
    "microservice.services.datastream.port"               -> wireMockPort,
    "auditing.consumer.baseUri.host"                      -> wireMockHost,
    "auditing.consumer.baseUri.port"                      -> wireMockPort,
    "metrics.rateUnit"                                    -> "SECONDS",
    "metrics.durationUnit"                                -> "SECONDS",
    "metrics.showSamples"                                 -> true,
    "metrics.jvm"                                         -> true,
    "metrics.enabled"                                     -> false,
		"auditing.enabled" 																		-> false
  )

  def additionalConfig(a: Map[String, Any] = Map()): Map[String, Any] = appConfig ++ a

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfig())
    .build()

  def makeRequest(uri: String): WSRequest = ws.url(s"http://localhost:$port/$uri")
}
