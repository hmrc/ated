import sbt.*

object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val bootstrapVersion = "8.5.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-30" % "8.5.0",
    "io.github.samueleresca" %% "pekko-quartz-scheduler" % "1.0.0-pekko-1.0.x",
    "uk.gov.hmrc" %% "domain-play-30" % "9.0.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30" % "1.8.0",
    "uk.gov.hmrc" %% "json-encryption" % "5.3.0-play-28"
  )

  trait TestDependencies {
    lazy val scope: String = "it,test"
    lazy val test: Seq[ModuleID] = ???
  }


  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % "8.5.0",
    "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1",
    "org.mockito" % "mockito-core" % "5.11.0",
    "org.scalatestplus" %% "mockito-4-11" % "3.2.18.0",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.17.0"
  ).map(_ % Test)


  object IntegrationTest {
		def apply(): Seq[ModuleID] = new TestDependencies {
			override lazy val test = Seq(
        "org.scalatestplus.play" %% "scalatestplus-play"     % "7.0.1"             % scope,
        "org.scalatestplus"      %% "mockito-4-11"           % "3.2.18.0"          % scope
			)
		}.test
	}

  val itDependencies: Seq[ModuleID] = Seq()


  def apply(): Seq[ModuleID] = compile ++ test
}
