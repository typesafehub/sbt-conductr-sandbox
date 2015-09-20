import org.scalatest.Matchers._
import ByteConversions._

name := "conductr-roles"

version := "0.1.0-SNAPSHOT"

// Only tests the conductr roles of the first node because tests are using the conductr-dev image.
// Multiple nodes scenario is tested with acceptance tests within conductr.

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

// ConductR bundle keys
BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("bundle-role-1", "bundle-role-2")

// ConductR sandbox keys
SandboxKeys.imageVersion in Global := sys.props.getOrElse("IMAGE_VERSION", default = "1.0.9")

val checkConductrRolesByBundle = taskKey[Unit]("Check that the bundle roles are used if no SandboxKeys.conductrRoles is specified.")
checkConductrRolesByBundle := {
  val content = "docker inspect --format='{{.Config.Env}}' cond-0".!!
  val expectedContent = "CONDUCTR_ROLES=bundle-role-1,bundle-role-2"
  content should include(expectedContent)
}

val checkConductrRolesBySandboxKey = taskKey[Unit]("Check that the roles declared by SandboxKeys.conductrRoles are used.")
checkConductrRolesBySandboxKey := {
  val content = "docker inspect --format='{{.Config.Env}}' cond-0".!!
  val expectedContent = "CONDUCTR_ROLES=conductr-role-1,conductr-role-2"
  content should include(expectedContent)
}