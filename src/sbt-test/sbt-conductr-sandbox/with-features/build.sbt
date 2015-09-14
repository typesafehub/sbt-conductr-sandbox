import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "with-features"

version := "0.1.0-SNAPSHOT"

val envValue = "-dSomeBundleKey=100"

// ConductR bundle keys
BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
SandboxKeys.envs in Global := Map("CONDUCTR_ARGS" -> envValue)
SandboxKeys.imageVersion in Global := sys.props.getOrElse("IMAGE_VERSION", default = "1.0.9")

/**
 * Check ports after 'sandbox run' command
 */
val checkPorts = taskKey[Unit]("Check that the specified ports are exposed to docker. Debug port should not be exposed.")
checkPorts := {
  val content = "docker port cond-0".!!
  val expectedLines = Set(
    """9999/tcp -> 0.0.0.0:9909""",
    """9200/tcp -> 0.0.0.0:9200"""
  )

  expectedLines.foreach(line => content should include(line))
}

val checkEnvs = taskKey[Unit]("Check if environment variables for features are set.")
checkEnvs := {
  val content = "docker inspect --format='{{.Config.Env}}' cond-0".!!
  val expectedConductrFeatures = "CONDUCTR_FEATURES=visualization,logging"
  content should include(expectedConductrFeatures)
  val expectedConductrArgs = s"CONDUCTR_ARGS=$envValue -Dcontrail.syslog.server.port=9200 -Dcontrail.syslog.server.elasticsearch.enabled=on"
  content should include(expectedConductrArgs)
}