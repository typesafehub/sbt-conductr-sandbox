import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(ConductRPlugin, ConductRSandbox)

name := "ports-basic"

version := "0.1.0-SNAPSHOT"

// ConductR bundle keys
BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.endpoints := Map("other" -> Endpoint("http", services = Set(URI("http://:9001/other-service"))))

// ConductR sandbox keys
SandboxKeys.ports in Global := Set(1111, 2222)
SandboxKeys.debugPort := 5432

/**
 * Check ports after 'sandbox run' command
 */
val checkPortsWithRun = taskKey[Unit]("Check that the specified ports are exposed to docker. Debug port should not be exposed.")
checkPortsWithRun := {
  val content = s"docker port cond-0".!!
  val expectedLines = Set(
    """9004/tcp -> 0.0.0.0:9004""",
    """9005/tcp -> 0.0.0.0:9005""",
    """9006/tcp -> 0.0.0.0:9006""",
    """9200/tcp -> 0.0.0.0:9200""",
    """9999/tcp -> 0.0.0.0:9909""",
    """1111/tcp -> 0.0.0.0:1101""",
    """2222/tcp -> 0.0.0.0:2202""",
    """9001/tcp -> 0.0.0.0:9001"""
  )

  expectedLines.foreach(line => content should include(line))
}

/**
 * Check bundle conf after 'sandbox run' and 'bundle:dist'
 */
val checkRunStartCommand = taskKey[Unit]("Check the start-command in bundle.conf. jvm-debug should not be part of it.")
checkRunStartCommand := {
  val contents = IO.read((target in Bundle).value / "tmp" / "bundle.conf")
  val expectedContents = """start-command    = ["ports-basic-0.1.0-SNAPSHOT/bin/ports-basic", "-J-Xms67108864", "-J-Xmx67108864"]""".stripMargin
  contents should include(expectedContents)
}

/**
 * Check ports after 'sandbox debug'
 */
val checkPortsWithDebug = taskKey[Unit]("Check that the specified ports are exposed to docker. Debug port should be exposed.")
checkPortsWithDebug := {
  val content = s"docker port cond-0".!!
  val expectedLines = Set(
    """9004/tcp -> 0.0.0.0:9004""",
    """9005/tcp -> 0.0.0.0:9005""",
    """9006/tcp -> 0.0.0.0:9006""",
    """9200/tcp -> 0.0.0.0:9200""",
    """9999/tcp -> 0.0.0.0:9909""",
    """1111/tcp -> 0.0.0.0:1101""",
    """2222/tcp -> 0.0.0.0:2202""",
    """5432/tcp -> 0.0.0.0:5402""",
    """9001/tcp -> 0.0.0.0:9001"""
  )

  expectedLines.foreach(line => content should include(line))
}

/**
 * Check bundle conf after 'sandbox debug' and 'bundle:dist'
 */
val checkDebugStartCommand = taskKey[Unit]("Check the start-command in bundle.conf. jvm-debug should be part of it.")
checkDebugStartCommand := {
  val contents = IO.read((target in Bundle).value / "tmp" / "bundle.conf")
  val expectedContents = """start-command    = ["ports-basic-0.1.0-SNAPSHOT/bin/ports-basic", "-J-Xms67108864", "-J-Xmx67108864", "-jvm-debug 5432"]""".stripMargin
  contents should include(expectedContents)
}