import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

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
SandboxKeys.imageVersion in Global := sys.props.getOrElse("IMAGE_VERSION", default = "1.0.12")

/**
 * Check ports after 'sandbox run' command
 */
val checkPortsWithRun = taskKey[Unit]("Check that the specified ports are exposed to docker. Debug port should not be exposed.")
checkPortsWithRun := {
  for (i <- 0 to 2) {
    val content = s"docker port cond-$i".!!
    val expectedLines = i match {
      case 0 =>
        Set(
          """9004/tcp -> 0.0.0.0:9004""",
          """9005/tcp -> 0.0.0.0:9005""",
          """9006/tcp -> 0.0.0.0:9006""",
          """9200/tcp -> 0.0.0.0:9200""",
          """9999/tcp -> 0.0.0.0:9999""",
          """1111/tcp -> 0.0.0.0:1111""",
          """2222/tcp -> 0.0.0.0:2222""",
          """9001/tcp -> 0.0.0.0:9001"""
        )

      case 1 =>
        Set(
          """9004/tcp -> 0.0.0.0:9014""",
          """9005/tcp -> 0.0.0.0:9015""",
          """9006/tcp -> 0.0.0.0:9016""",
          """9200/tcp -> 0.0.0.0:9210""",
          """9999/tcp -> 0.0.0.0:9909""",
          """1111/tcp -> 0.0.0.0:1121""",
          """2222/tcp -> 0.0.0.0:2232""",
          """9001/tcp -> 0.0.0.0:9011"""
        )

      case 2 =>
        Set(
          """9004/tcp -> 0.0.0.0:9024""",
          """9005/tcp -> 0.0.0.0:9025""",
          """9006/tcp -> 0.0.0.0:9026""",
          """9200/tcp -> 0.0.0.0:9220""",
          """9999/tcp -> 0.0.0.0:9919""",
          """1111/tcp -> 0.0.0.0:1131""",
          """2222/tcp -> 0.0.0.0:2242""",
          """9001/tcp -> 0.0.0.0:9021"""
        )
    }

    expectedLines.foreach(line => content should include(line))
  }
}

/**
 * Check bundle conf after 'sandbox run' and 'bundle:dist'
 */
val checkRunStartCommand = taskKey[Unit]("Check the start-command in bundle.conf. jvm-debug should not be part of it.")
checkRunStartCommand := {
  val contents = IO.read((target in Bundle).value / "bundle"/ "tmp" / "bundle.conf")
  val expectedContents = """start-command    = ["ports-basic/bin/ports-basic", "-J-Xms67108864", "-J-Xmx67108864"]""".stripMargin
  contents should include(expectedContents)
}

/**
 * Check ports after 'sandbox debug'
 */
val checkPortsWithDebug = taskKey[Unit]("Check that the specified ports are exposed to docker. Debug port should be exposed.")
checkPortsWithDebug := {
  for (i <- 0 to 2) {
    val content = s"docker port cond-$i".!!
    val expectedLines = i match {
      case 0 =>
        Set(
          """9004/tcp -> 0.0.0.0:9004""",
          """9005/tcp -> 0.0.0.0:9005""",
          """9006/tcp -> 0.0.0.0:9006""",
          """9200/tcp -> 0.0.0.0:9200""",
          """9999/tcp -> 0.0.0.0:9999""",
          """1111/tcp -> 0.0.0.0:1111""",
          """2222/tcp -> 0.0.0.0:2222""",
          """5432/tcp -> 0.0.0.0:5432""",
          """9001/tcp -> 0.0.0.0:9001"""
        )

      case 1 =>
        Set(
          """9004/tcp -> 0.0.0.0:9014""",
          """9005/tcp -> 0.0.0.0:9015""",
          """9006/tcp -> 0.0.0.0:9016""",
          """9200/tcp -> 0.0.0.0:9210""",
          """9999/tcp -> 0.0.0.0:9909""",
          """1111/tcp -> 0.0.0.0:1121""",
          """2222/tcp -> 0.0.0.0:2232""",
          """5432/tcp -> 0.0.0.0:5442""",
          """9001/tcp -> 0.0.0.0:9011"""
        )

      case 2 =>
        Set(
          """9004/tcp -> 0.0.0.0:9024""",
          """9005/tcp -> 0.0.0.0:9025""",
          """9006/tcp -> 0.0.0.0:9026""",
          """9200/tcp -> 0.0.0.0:9220""",
          """9999/tcp -> 0.0.0.0:9919""",
          """1111/tcp -> 0.0.0.0:1131""",
          """2222/tcp -> 0.0.0.0:2242""",
          """5432/tcp -> 0.0.0.0:5452""",
          """9001/tcp -> 0.0.0.0:9021"""
        )
    }

    expectedLines.foreach(line => content should include(line))
  }
}

/**
 * Check bundle conf after 'sandbox debug' and 'bundle:dist'
 */
val checkDebugStartCommand = taskKey[Unit]("Check the start-command in bundle.conf. jvm-debug should be part of it.")
checkDebugStartCommand := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContents = """start-command    = ["ports-basic/bin/ports-basic", "-jvm-debug", "5432", "-J-Xms67108864", "-J-Xmx67108864"]""".stripMargin
  contents should include(expectedContents)
}