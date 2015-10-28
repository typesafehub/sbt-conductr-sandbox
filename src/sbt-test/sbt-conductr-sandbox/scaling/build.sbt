import org.scalatest.Matchers._
import ByteConversions._

name := "scaling"

version := "0.1.0-SNAPSHOT"

// ConductR bundle keys
BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB

// ConductR sandbox keys
SandboxKeys.imageVersion in Global := sys.props.getOrElse("IMAGE_VERSION", default = "1.0.12")

def resolveRunningContainers = """docker ps --quiet --filter name=cond""".lines_!

val checkContainers0 = taskKey[Unit]("Check that 0 containers are running.")
checkContainers0 := {
  resolveRunningContainers should have size 0
}

val checkContainers1 = taskKey[Unit]("Check that 1 container is running.")
checkContainers1 := {
  resolveRunningContainers should have size 1
}

val checkContainers2 = taskKey[Unit]("Check that 2 containers are running.")
checkContainers2 := {
  resolveRunningContainers should have size 2
}

val checkContainers3 = taskKey[Unit]("Check that 3 containers are running.")
checkContainers3 := {
  resolveRunningContainers should have size 3
}