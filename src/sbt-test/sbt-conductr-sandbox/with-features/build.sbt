import org.scalatest.Matchers._
import ByteConversions._

import scala.util.Try

lazy val root = (project in file(".")).enablePlugins(ConductRSandbox)

name := "with-features"

version := "0.1.0-SNAPSHOT"

// ConductR bundle keys
BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB

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
  val expectedContent = "CONDUCTR_FEATURES=visualization,logging"
  content should include(expectedContent)
}

val waitForBundles = taskKey[Unit]("Wait that bundles has been started.")
waitForBundles := {
  Thread.sleep(30000)
}

val checkConnection = taskKey[Unit]("Check if the features are reachable.")
checkConnection := {
  val dockerIp =
    Try("boot2docker ip".!!.trim.reverse.takeWhile(_ != ' ').reverse).getOrElse("hostname".!!.trim)

  def curl(port: Int): String =
    (s"curl -IL http://$dockerIp:$port 2>/dev/null" #| "head -n 1").!!

  val visualization_status_code = curl(9909)
  visualization_status_code should include("200")

  val logging_status_code = curl(9200)
  logging_status_code should include("503")
}