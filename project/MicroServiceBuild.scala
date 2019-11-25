import sbt._

object MicroServiceBuild extends Build with MicroService {
  val appName = "ated"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-play-26"    % "1.3.0",
    "uk.gov.hmrc"       %% "domain"               % "5.6.0-play-26",
    "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.20.0-play-26",
    "uk.gov.hmrc"       %% "json-encryption"      % "4.4.0-play-26",
    "uk.gov.hmrc"       %% "auth-client"          % "2.31.0-play-26",
    "com.typesafe.play" %% "play-json-joda"       % "2.6.10"
  )

  trait TestDependencies {
    lazy val scope: String = "it,test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "org.scalatest"           %% "scalatest"          % "3.0.5"             % scope,
        "org.scalatestplus.play"  %% "scalatestplus-play" % "3.1.2"             % scope,
        "org.pegdown"              % "pegdown"            % "1.6.0"             % scope,
        "org.mockito"              % "mockito-core"       % "2.24.5"            % scope,
        "com.typesafe.play"       %% "play-test"          % PlayVersion.current % scope,
        "uk.gov.hmrc"             %% "reactivemongo-test" % "4.15.0-play-26"    % scope
      )
    }.test
  }

  def apply() = compile ++ Test()
}

