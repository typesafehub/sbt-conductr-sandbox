lazy val root = Project("plugins", file(".")).dependsOn(plugin)

lazy val plugin = file("../").getCanonicalFile.toURI

addSbtPlugin("com.typesafe.conductr" % "sbt-conductr" % "1.1.0")
