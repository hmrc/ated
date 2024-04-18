import play.sbt.PlayImport.ws
import sbt.*

private object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % "8.5.0",
    "io.github.samueleresca" %% "pekko-quartz-scheduler"    % "1.0.0-pekko-1.0.x",
    "uk.gov.hmrc"            %% "domain-play-30"            % "9.0.0",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-30"        % "1.8.0",
    "uk.gov.hmrc"            %% "crypto-json-play-30"       % "7.6.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30" % "8.5.0",
  ).map(_ % Test)

  val itDependencies: Seq[ModuleID] = Seq()

  def apply(): Seq[ModuleID] = compile ++ test
}