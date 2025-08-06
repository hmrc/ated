import play.sbt.PlayImport.ws
import sbt.*

private object AppDependencies {

  val bootstrapVersion = "10.0.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "io.github.samueleresca" %% "pekko-quartz-scheduler"    % "1.2.2-pekko-1.0.x",
    "uk.gov.hmrc"            %% "domain-play-30"            % "11.0.0",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-30"        % "2.7.0",
    "uk.gov.hmrc"            %% "crypto-json-play-30"       % "8.3.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion,
  ).map(_ % Test)

  val itDependencies: Seq[ModuleID] = Seq()

  def apply(): Seq[ModuleID] = compile ++ test
}
