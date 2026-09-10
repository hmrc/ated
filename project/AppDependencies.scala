import play.sbt.PlayImport.ws
import sbt.*

private object AppDependencies {

  val bootstrapVersion = "10.8.0"
  val pekkoVersion     = "1.7.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30"   % bootstrapVersion,
    "io.github.samueleresca" %% "pekko-quartz-scheduler"      % "1.3.0-pekko-1.1.x"  exclude("com.mchange", "mchange-commons-java"),
    "com.mchange"            %  "mchange-commons-java"        % "0.6.1",
    "uk.gov.hmrc"            %% "domain-play-30"              % "13.0.0",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-30"          % "2.13.0",
    "uk.gov.hmrc"            %% "crypto-json-play-30"         % "8.4.0",
    "org.apache.pekko"       %% "pekko-protobuf-v3"           % pekkoVersion,
    "org.apache.pekko"       %% "pekko-serialization-jackson" % pekkoVersion,
    "org.apache.pekko"       %% "pekko-stream"                % pekkoVersion,
    "org.apache.pekko"       %% "pekko-actor-typed"           % pekkoVersion,
    "org.apache.pekko"       %% "pekko-slf4j"                 % pekkoVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion,
  ).map(_ % Test)

  val itDependencies: Seq[ModuleID] = Seq()

  def apply(): Seq[ModuleID] = compile ++ test
}
