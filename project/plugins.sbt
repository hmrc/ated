resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "3.22.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "2.5.0")

addSbtPlugin("org.playframework"  % "sbt-plugin"  % "3.0.3")

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "2.0.11")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.5.3")


