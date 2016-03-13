import scalariform.formatter.preferences._
import bintray.Keys._

sbtPlugin := true

organization := "com.typesafe.conductr"
name := "sbt-conductr-sandbox"

scalaVersion := "2.10.4"
scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

scalariformSettings
ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

resolvers += Resolver.bintrayRepo("typesafe", "maven-releases")

addSbtPlugin("com.typesafe.conductr" % "sbt-conductr" % "1.5.0")

releaseSettings
ReleaseKeys.versionBump := sbtrelease.Version.Bump.Minor

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
publishMavenStyle := false
bintrayPublishSettings
repository in bintray := "sbt-plugins"
bintrayOrganization in bintray := Some("sbt-conductr-sandbox")

scriptedSettings
scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }
