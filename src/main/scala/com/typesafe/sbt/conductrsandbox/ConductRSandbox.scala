package com.typesafe.sbt.conductrsandbox

import com.typesafe.conductr.sbt.ConductRKeys
import com.typesafe.sbt.bundle.Import.BundleKeys
import sbt._
import sbt.Keys._

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
      "A sequence of ports to be made public by each of the ConductR containers. This will be complemented to the `endpoints` setting's service ports declared for `sbt-bundle`."
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

  val sandbox = config("conductr-sandbox")
}

object ConductRSandbox extends AutoPlugin {

  import Import._
  import SandboxKeys._

  val autoImport = Import

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      envs := Map.empty,
      image := "conductr/conductr-dev",
      ports := Set.empty,
      logLevel := "info",
      nrOfContainers := 1,

      ConductRKeys.conductrControlServerUrl in Global := url(s"http://${resolveDockerHostIp()}:$ConductrPort"),

      run in sandbox := runConductRs(ScopeFilter(inAnyProject, inAnyConfiguration)).value,
      onUnload := (stopConductRs _).andThen(onUnload.value)
    )

  private final val ConductrNamePrefix = "cond-"

  private final val ConductrPort = 9005

  private def inspectCond0Ip(): String =
    s"""docker inspect --format="{{.NetworkSettings.IPAddress}}" ${ConductrNamePrefix}0""".!!

  private def resolveDockerHostIp(): String =
    Try("boot2docker ip".!!.trim.reverse.takeWhile(_ != ' ').reverse).getOrElse("hostname".!!.trim)

  // FIXME: The filter must be passed in presently: https://github.com/sbt/sbt/issues/1095
  private def runConductRs(filter: ScopeFilter): Def.Initialize[Task[Unit]] = Def.task {

    val bundlePorts = BundleKeys.endpoints.all(filter).value.reduce(_ ++ _)
      .flatMap {
        case (_, endpoint) =>
          endpoint.services.map { uri =>
            if (uri.getHost != null) uri.getPort else uri.getAuthority.drop(1).toInt
          }.collect {
            case port if port >= 0 => port
          }
      }.toSet

    streams.value.log.info("Running ConductR...")
    for (i <- 0 until (nrOfContainers in Global).value) {
      val container = s"$ConductrNamePrefix$i"
      val containers = s"""docker ps -q -f "name="$ConductrNamePrefix$i""".!!
      if (containers.isEmpty) {
        streams.value.log.info(s"Running container $container ...")
        val cond0Ip = if (i > 0) Some(inspectCond0Ip()) else None
        runConductR(
          container,
          cond0Ip,
          (envs in Global).value,
          (image in Global).value,
          (logLevel in Global).value,
          (ports in Global).value ++ bundlePorts)
      } else
        streams.value.log.warn(s"Container $container already exists, leaving it alone")
    }
  }

  private def runConductR(
    container: String,
    cond0Ip: Option[String],
    envsValue: Map[String, String],
    imageValue: String,
    logLevelValue: String,
    portsValue: Set[Int]): Unit = {
    println(s"Container $container starting with $cond0Ip, $envsValue, $imageValue, $portsValue, $logLevelValue")
  }

  private def stopConductRs(state: State): State = {
    val containers = s"""docker ps -q -f "name="$ConductrNamePrefix""".lines_!
    if (containers.nonEmpty) {
      state.log.info(s"Stopping ConductR...")
      val containersArg = containers.mkString(" ")
      s"docker rm -f $containersArg" ! state.log
    }
    state
  }
}
