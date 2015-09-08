import org.scalatest.Matchers._
import ByteConversions._

name := "ports-multi-module"
version := "0.1.0-SNAPSHOT"

// ConductR global keys
SandboxKeys.ports in Global := Set(1111, 2222)
SandboxKeys.imageVersion in Global := sys.props.getOrElse("IMAGE_VERSION", default = "1.0.9")

lazy val common = (project in file("modules/common"))
  .settings(
    SandboxKeys.debugPort := 8888
  )

lazy val frontend = (project in file("modules/frontend"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(common)
  .settings(
    BundleKeys.nrOfCpus := 1.0,
    BundleKeys.memory := 64.MiB,
    BundleKeys.diskSpace := 50.MiB,
    BundleKeys.roles := Set("frontend"),
    BundleKeys.endpoints := Map("frontend" -> Endpoint("http", services = Set(URI("http://:9000")))),
    SandboxKeys.debugPort := 5555
  )

lazy val backend = (project in file("modules/backend"))
  .enablePlugins(JavaAppPackaging)
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
    """9200/tcp -> 0.0.0.0:9200""",
    """9999/tcp -> 0.0.0.0:9909""",
    """1111/tcp -> 0.0.0.0:1101""",
    """2222/tcp -> 0.0.0.0:2202""",
    """8888/tcp -> 0.0.0.0:8808""",
    """5555/tcp -> 0.0.0.0:5505""",
    """2999/tcp -> 0.0.0.0:2909"""
  )
  expectedLinesCond0.foreach(line => contentCond0 should include(line))

}
