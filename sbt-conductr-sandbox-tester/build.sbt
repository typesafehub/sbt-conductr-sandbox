import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(ConductRPlugin, ConductRSandbox)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web-server")

BundleKeys.configurationName := "frontend"

SandboxKeys.image in Global := "conductr/conductr"
SandboxKeys.nrOfContainers in Global := 3
SandboxKeys.ports in Global := Set(9999)