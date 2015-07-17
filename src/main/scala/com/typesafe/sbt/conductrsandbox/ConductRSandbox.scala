package com.typesafe.sbt.conductrsandbox

import com.typesafe.conductr.sbt.{ ConductRKeys, ConductRPlugin }
import com.typesafe.sbt.bundle.Import.BundleKeys
import com.typesafe.sbt.bundle.SbtBundle
import sbt._

import scala.util.Try

object Import {

  object SandboxKeys {

    val envs = SettingKey[Map[String, String]](
      "conductr-sandbox-envs",
      "A map of environment variables to be set for each ConductR container."
    )

    val image = SettingKey[String](
      "conductr-sandbox-image",
      "The Docker image to use. By default `conductr/conductr-dev` is used i.e. the single node version of ConductR. For the full version please [download it via our website](http://www.typesafe.com/products/conductr) and then use just `conductr/conductr`."
    )

    val ports = SettingKey[Set[Int]](
      "conductr-sandbox-ports",
      "A sequence of ports to be made public by each of the ConductR containers. By default, this will be initialized to the `endpoints` setting's service ports declared for `sbt-bundle`."
    )

    val logLevel = SettingKey[String](
      "conductr-sandbox-logLevel",
      """The log level of ConductR which can be one of "debug", "warning" or "info". By default this is set to "info". You can observe ConductR's logging via the `docker logs` command. For example `docker logs -f cond-0` will follow the logs of the first ConductR container."""
    )

    val nrOfContainers = SettingKey[Int](
      "conductr-sandbox-nrOfContainers",
      """Sets the number of ConductR containers to run in the background. By default 1 is run. Note that by default you may only have more than one if the image being used is *not* conductr/conductr-dev (the default, single node version of ConductR for general development)."""
    )
  }
}

object ConductRSandbox extends AutoPlugin {

  import Import._
  import SandboxKeys._

  val autoImport = Import

  override def `requires` = ConductRPlugin && SbtBundle

  override def trigger = AllRequirements

  override def projectSettings = Seq(
    envs := Map.empty,
    image := "conductr/conductr-dev",
    ports := BundleKeys.endpoints.value.flatMap {
      case (_, endpoint) =>
        endpoint.services.map { uri =>
          if (uri.getHost != null) uri.getPort else uri.getAuthority.drop(1).toInt
        }.collect {
          case port if port >= 0 => port
        }
    }.toSet,
    logLevel := "info",
    nrOfContainers := 1,

    ConductRKeys.conductrControlServerUrl := url(s"http://${resolveDockerHostIp()}:$ConductrPort")
  )

  private final val ConductrPort = 9005

  private def resolveDockerHostIp(): String =
    Try("boot2docker ip".!!.trim.reverse.takeWhile(_ != ' ').reverse).getOrElse("hostname".!!.trim)
}
