import org.scalatest.Matchers._
import ByteConversions._

name := "ports-multi-module"
version := "0.1.0-SNAPSHOT"

// ConductR global keys
SandboxKeys.ports in Global := Set(1111, 2222)
SandboxKeys.image in Global := "conductr/conductr"
SandboxKeys.nrOfContainers in Global := 2

lazy val common = (project in file("modules/common"))
  .enablePlugins(ConductRSandbox)
  .settings(
    SandboxKeys.debugPort := 8888
  )

lazy val frontend = (project in file("modules/frontend"))
  .enablePlugins(ConductRPlugin, ConductRSandbox)
  .dependsOn(common)
  .settings(
    BundleKeys.nrOfCpus := 1.0,
    BundleKeys.memory := 64.MiB,
    BundleKeys.diskSpace := 50.MiB,
    BundleKeys.roles := Set("frontend"),
    BundleKeys.endpoints := Map("frontend" -> Endpoint("http", services = Set(URI("http://:9000")))),
    SandboxKeys.debugPort := 9999
  )

lazy val backend = (project in file("modules/backend"))
  .enablePlugins(ConductRPlugin, ConductRSandbox)
  .dependsOn(common)
  .settings(
    BundleKeys.nrOfCpus := 1.0,
    BundleKeys.memory := 128.MiB,
    BundleKeys.diskSpace := 50.MiB,
    BundleKeys.roles := Set("backend"),
    BundleKeys.endpoints := Map("backend" -> Endpoint("http", services = Set(URI("http://:2551")))),
    SandboxKeys.debugPort := 2999
  )

val checkDockerContainers = taskKey[Unit]("Check that the specified ports are exposed to docker.")

checkDockerContainers := {
  // cond-0
  val contentCond0 = s"docker port cond-0".!!
  val expectedLinesCond0 = Set(
    """9004/tcp -> 0.0.0.0:9004""",
    """9005/tcp -> 0.0.0.0:9005""",
    """9006/tcp -> 0.0.0.0:9006""",
    """1111/tcp -> 0.0.0.0:1101""",
    """2222/tcp -> 0.0.0.0:2202""",
    """8888/tcp -> 0.0.0.0:8808""",
    """9999/tcp -> 0.0.0.0:9909""",
    """2999/tcp -> 0.0.0.0:2909"""
  )
  expectedLinesCond0.foreach(line => contentCond0 should include(line))

  // cond-1
  val contentCond1 = s"docker port cond-1".!!
  val expectedLinesCond1 = Set(
    """9004/tcp -> 0.0.0.0:9014""",
    """9005/tcp -> 0.0.0.0:9015""",
    """9006/tcp -> 0.0.0.0:9016""",
    """1111/tcp -> 0.0.0.0:1111""",
    """2222/tcp -> 0.0.0.0:2212""",
    """8888/tcp -> 0.0.0.0:8818""",
    """9999/tcp -> 0.0.0.0:9919""",
    """2999/tcp -> 0.0.0.0:2919"""
  )
  expectedLinesCond1.foreach(line => contentCond1 should include(line))
}