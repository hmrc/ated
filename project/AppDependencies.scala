import sbt._

object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    ws,
		"com.enragedginger" %% "akka-quartz-scheduler"      % "1.9.1-akka-2.6.x",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28"  % "5.16.0",
    "uk.gov.hmrc"       %% "domain"                     % "6.2.0-play-28",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % "0.58.0",
    "uk.gov.hmrc"       %% "json-encryption"            % "4.10.0-play-28",
		"uk.gov.hmrc"			  %% "mongo-lock"					        % "7.0.0-play-28",
		"com.typesafe.play" %% "play-json-joda"             % "2.9.2"
  )

  trait TestDependencies {
    lazy val scope: String = "it,test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "org.scalatestplus.play"       %% "scalatestplus-play"   % "5.1.0"             % scope,
        "org.pegdown"                  %  "pegdown"              % "1.6.0"             % scope,
        "org.mockito"                  %  "mockito-core"         % "3.12.4"            % scope,
        "com.typesafe.play"            %% "play-test"            % PlayVersion.current % scope,
        "uk.gov.hmrc"                  %% "reactivemongo-test"   % "5.0.0-play-28"     % scope,
        "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.5"            % scope
      )
    }.test
  }

	object IntegrationTest {
		def apply(): Seq[ModuleID] = new TestDependencies {
			override lazy val test = Seq(
				"org.pegdown" 					 %  "pegdown" 					 % 	"1.6.0"						  % scope,
				"com.typesafe.play"			 %% "play-test" 				 % 	PlayVersion.current % scope,
				"org.scalatestplus.play" %% "scalatestplus-play" % 	"5.1.0" 						% scope,
        "org.scalatestplus"      %% "mockito-3-12"       % "3.2.10.0"           % scope,
        "com.vladsch.flexmark"   %  "flexmark-all"       %  "0.35.10"           % scope,
				"com.github.tomakehurst" %  "wiremock-jre8"			 %  "2.31.0"            % scope
			)
		}.test
	}

  def apply(): Seq[ModuleID] = compile ++ Test() ++ IntegrationTest()
}

