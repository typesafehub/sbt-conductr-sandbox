import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(ConductRSandbox)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

SandboxKeys.image := "conductr/conductr"

val checkConductRIsRunning = taskKey[Unit]("")
val checkConductRIsStopped = taskKey[Unit]("")

checkConductRIsRunning := s"docker ps -q -f name=cond-".lines_!.size shouldBe 3

checkConductRIsStopped := s"docker ps -q -f name=cond-".lines_!.size shouldBe 0
