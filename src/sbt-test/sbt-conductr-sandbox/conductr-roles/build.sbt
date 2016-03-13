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
SandboxKeys.imageVersion in Global := sys.props.getOrElse("IMAGE_VERSION", default = "1.1.2")

val checkConductrRolesByBundle = taskKey[Unit]("Check that the bundle roles are used if no SandboxKeys.conductrRoles is specified.")
checkConductrRolesByBundle := {
  for (i <- 0 to 2) {
    val content = s"docker inspect --format='{{.Config.Env}}' cond-$i".!!
    val expectedContent = "CONDUCTR_ROLES=bundle-role-1,bundle-role-2"
    content should not include(expectedContent)
  }
}

val checkConductrRolesBySandboxKey = taskKey[Unit]("Check that the roles declared by SandboxKeys.conductrRoles are used.")
checkConductrRolesBySandboxKey := {
  for (i <- 0 to 3) {
    val content = s"docker inspect --format='{{.Config.Env}}' cond-$i".!!
    val expectedContent =
      if(i % 2 == 0) "CONDUCTR_ROLES=new-role"
      else           "CONDUCTR_ROLES=other-role"
    content should include(expectedContent)
  }
}

val checkConductRIsStopped = taskKey[Unit]("")
checkConductRIsStopped := {
  """docker ps --quiet --filter name=cond""".lines_! should have size 0
}