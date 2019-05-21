import sbt._

object MicroServiceBuild extends Build with MicroService {
  val appName = "ated"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val microserviceBootstrapVersion = "10.4.0"
  private val domainVersion = "5.3.0"
  private val scalaTestVersion = "3.0.5"
  private val pegdownVersion = "1.6.0"
  private val scalaTestPlusVersion = "2.0.1"
  private val simpleReactivemongoVersion = "7.12.0-play-25"
  private val reactivemongoTestVersion = "4.7.0-play-25"
  private val jsonEncryptionVersion = "4.1.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    // "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactivemongoVersion,
    "org.mongodb.scala" %% "mongo-scala-driver" % "2.6.0",
    "uk.gov.hmrc" %% "json-encryption" % jsonEncryptionVersion
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatest" %% "scalatest" % scalaTestVersion % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "org.mockito" % "mockito-all" % "1.10.19" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope//,
        //"uk.gov.hmrc" %% "reactivemongo-test" % reactivemongoTestVersion % scope
      )
    }.test
  }

  def apply() = compile ++ Test()
}
