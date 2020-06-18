import sbt._

object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    ws,
		"com.enragedginger" %% "akka-quartz-scheduler" % "1.8.0-akka-2.5.x",
    "uk.gov.hmrc"       %% "bootstrap-play-26"     % "1.8.0",
    "uk.gov.hmrc"       %% "domain"                % "5.9.0-play-26",
    "uk.gov.hmrc"       %% "simple-reactivemongo"  % "7.26.0-play-26",
    "uk.gov.hmrc"       %% "json-encryption"       % "4.5.0-play-26",
    "uk.gov.hmrc"       %% "auth-client"           % "3.0.0-play-26",
		"uk.gov.hmrc"			  %% "mongo-lock"					   % "6.21.0-play-26",
		"com.typesafe.play" %% "play-json-joda"        % "2.6.10"
  )

  trait TestDependencies {
    lazy val scope: String = "it,test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "org.scalatest"           %% "scalatest"          % "3.0.8"             % scope,
        "org.scalatestplus.play"  %% "scalatestplus-play" % "3.1.3"             % scope,
        "org.pegdown"              % "pegdown"            % "1.6.0"             % scope,
        "org.mockito"              % "mockito-core"       % "3.3.3"            % scope,
        "com.typesafe.play"       %% "play-test"          % PlayVersion.current % scope,
        "uk.gov.hmrc"             %% "reactivemongo-test" % "4.19.0-play-26"    % scope
      )
    }.test
  }

	object IntegrationTest {
		def apply(): Seq[ModuleID] = new TestDependencies {
			override lazy val test = Seq(
				"uk.gov.hmrc"						 %% "hmrctest" 						 % 	"3.9.0-play-26" 				% scope,
				"org.pegdown" 					 % "pegdown" 							 % 	"1.6.0"								  % scope,
				"com.typesafe.play"			 %% "play-test" 				 	 % 	PlayVersion.current 		% scope,
				"org.scalatestplus.play" %% "scalatestplus-play"	 % 	"3.1.3" 								% scope,
				"com.github.tomakehurst" % "wiremock-jre8"				 % 	"2.23.2" 								% scope
			)
		}.test
	}

  def apply(): Seq[ModuleID] = compile ++ Test() ++ IntegrationTest()
}
