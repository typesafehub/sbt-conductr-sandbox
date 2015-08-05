import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(ConductRSandbox)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

// ConductR Sandbox
SandboxKeys.ports := Set(1111, 2222)

val checkPorts = taskKey[Unit]("Check that the specified ports are exposed to docker.")

checkPorts := {
  val content = s"docker port cond-0".!!
  val expectedLines = Set(
    """9004/tcp -> 0.0.0.0:9004""",
    """9005/tcp -> 0.0.0.0:9005""",
    """9006/tcp -> 0.0.0.0:9006""",
    """1111/tcp -> 0.0.0.0:1101""",
    """2222/tcp -> 0.0.0.0:2202"""
  )

  expectedLines.foreach(line => content should include(line))
}