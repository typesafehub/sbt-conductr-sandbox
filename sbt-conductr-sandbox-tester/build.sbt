import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("bundle-role-1", "bundle-role-2")
BundleKeys.startCommand := Seq("-Xms1G")

BundleKeys.configurationName := "frontend"
BundleKeys.endpoints := Map("other" -> Endpoint("http", services = Set(URI("http://:9001/other-service"))))
SandboxKeys.ports in Global := Set(9999)
SandboxKeys.imageVersion in Global := "1.0.14"

SandboxKeys.debugPort := 5432
