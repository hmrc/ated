import sbt._

object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val bootstrapVersion = "7.21.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
		"com.enragedginger" %% "akka-quartz-scheduler"      % "1.9.3-akka-2.6.x",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28"  % bootstrapVersion,
    "uk.gov.hmrc"       %% "domain"                     % "8.3.0-play-28",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % "1.3.0",
    "uk.gov.hmrc"       %% "json-encryption"            % "5.1.0-play-28",
		"com.typesafe.play" %% "play-json-joda"             % "2.9.4"
  )

  trait TestDependencies {
    lazy val scope: String = "it,test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "uk.gov.hmrc"                  %% "bootstrap-test-play-28"  % bootstrapVersion    % scope,
        "org.scalatestplus.play"       %% "scalatestplus-play"      % "5.1.0"             % scope,
        "org.mockito"                  %  "mockito-core"            % "5.4.0"             % scope,
        "com.typesafe.play"            %% "play-test"               % PlayVersion.current % scope,
        "com.fasterxml.jackson.module" %% "jackson-module-scala"    % "2.15.2"            % scope
      )
    }.test
  }

	object IntegrationTest {
		def apply(): Seq[ModuleID] = new TestDependencies {
			override lazy val test = Seq(
        "uk.gov.hmrc"            %% "bootstrap-test-play-28" % bootstrapVersion    % scope,
				"com.typesafe.play"			 %% "play-test" 				     % PlayVersion.current % scope,
				"org.scalatestplus.play" %% "scalatestplus-play"     % "5.1.0"             % scope,
        "org.scalatestplus"      %% "mockito-4-6"            % "3.2.15.0"          % scope,
				"com.github.tomakehurst" %  "wiremock-jre8"			     % "2.35.0"            % scope
			)
		}.test
	}

  def apply(): Seq[ModuleID] = compile ++ Test() ++ IntegrationTest()
}
