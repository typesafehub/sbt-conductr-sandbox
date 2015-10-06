import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web")
BundleKeys.startCommand := Seq("-Xms1G")

BundleKeys.configurationName := "frontend"

SandboxKeys.ports in Global := Set(9999)
SandboxKeys.imageVersion in Global := "1.0.11"

SandboxKeys.debugPort := 5432
