package com.typesafe.sbt.conductrsandbox

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

    val runConductRs = TaskKey[Unit](
      "conductr-sandbox-run",
      "Starts the sandbox environment."
    )

    val stopConductRs = TaskKey[Unit](
      "conductr-sandbox-stop",
      "Stops the sandbox environment."
    )
  }
}

object ConductRSandbox extends AutoPlugin {

  import Import._
  import SandboxKeys._

  val autoImport = Import

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      envs := Map.empty,
      image := ConductRDevImage,
      ports := Set.empty,
      logLevel := "info",
      nrOfContainers := 1,

      runConductRs := runConductRsTask(ScopeFilter(inAnyProject, inAnyConfiguration)).value,
      stopConductRs := stopConductRsTask().value,

      commands ++= Seq(sandboxControlServer)
    )

  private final val ConductRDevImage = "typesafe-docker-internal-docker.bintray.io/conductr/conductr-dev"

  private final val ConductrNamePrefix = "cond-"

  private final val ConductrPort = 9005

  private def inspectCond0Ip(): String =
    s"""docker inspect --format="{{.NetworkSettings.IPAddress}}" ${ConductrNamePrefix}0""".!!.trim

  private def portMapping(instance: Int, port: Int): String = {
    val portStrRev = port.toString.reverse
    (portStrRev.take(1) + instance.toString + portStrRev.drop(2)).reverse
  }

  private def resolveDockerHostIp(): String =
    Try("boot2docker ip".!!.trim.reverse.takeWhile(_ != ' ').reverse).getOrElse("hostname".!!.trim)

  // FIXME: The filter must be passed in presently: https://github.com/sbt/sbt/issues/1095
  private def runConductRsTask(filter: ScopeFilter): Def.Initialize[Task[Unit]] = Def.task {

    if ((image in Global).value == ConductRDevImage && s"docker images -q $ConductRDevImage".!!.isEmpty) {
      streams.value.log.info("Pulling down the development version of ConductR * * * SINGLE NODED AND NOT FOR PRODUCTION USAGE * * *")
      s"docker pull $ConductRDevImage".!(streams.value.log)
    }

    val bundlePorts = BundleKeys.endpoints.?.map(_.getOrElse(Map.empty)).all(filter).value.reduce(_ ++ _)
      .flatMap {
        case (_, endpoint) =>
          endpoint.services.map { uri =>
            if (uri.getHost != null) uri.getPort else uri.getAuthority.drop(1).toInt
          }.collect {
            case port if port >= 0 => port
          }
      }.toSet

    streams.value.log.info("Running ConductR...")
    val dockerHostIp = resolveDockerHostIp()
    for (i <- 0 until (nrOfContainers in Global).value) {
      val container = s"$ConductrNamePrefix$i"
      val containers = s"docker ps -q -f name=$container".!!
      if (containers.isEmpty) {
        val portsDesc = (ports in Global).value.map(port => s"$dockerHostIp:${portMapping(i, port)}").mkString(", ")
        streams.value.log.info(s"Running container $container exposing $portsDesc...")
        val cond0Ip = if (i > 0) Some(inspectCond0Ip()) else None
        runConductRCmd(
          i,
          container,
          cond0Ip,
          (envs in Global).value,
          (image in Global).value,
          (logLevel in Global).value,
          (ports in Global).value ++ bundlePorts).!(streams.value.log)
      } else
        streams.value.log.warn(s"Container $container already exists, leaving it alone")
    }
  }

  private def runConductRCmd(
    instance: Int,
    container: String,
    cond0Ip: Option[String],
    envsValue: Map[String, String],
    imageValue: String,
    logLevelValue: String,
    portsValue: Set[Int]): String = {

    val command = Seq(
      "docker",
      "run"
    )

    val generalArgs = Seq(
      "-d",
      "--name", container
    )

    val logLevelEnv = Map("AKKA_LOGLEVEL" -> logLevelValue)
    val syslogEnv = cond0Ip.map(ip => Map("SYSLOG_IP" -> ip)).getOrElse(Map.empty)
    val envsArgs = (logLevelEnv ++ syslogEnv ++ envsValue).map { case (k, v) => s"-e $k=$v" }.toSeq

    val conductrPorts = Set(
      9004,
      9005,
      9006
    )
    val portsArgs = (portsValue ++ conductrPorts).toSeq.flatMap(port => Seq("-p", s"${portMapping(instance, port)}:$port"))

    val seedNodeArg = cond0Ip.toSeq.flatMap(ip => Seq("--seed", s"$ip:9004"))
    val positionalArgs = Seq(
      imageValue,
      "--discover-host-ip"
    ) ++ seedNodeArg

    (command ++ generalArgs ++ envsArgs ++ portsArgs ++ positionalArgs).mkString(" ")
  }

  private def sandboxControlServer: Command = Command.command("sandboxControlServer") { prevState =>
    Command.process(s"controlServer http://${resolveDockerHostIp()}:$ConductrPort", prevState)
  }

  private def stopConductRsTask(): Def.Initialize[Task[Unit]] = Def.task {
    val containers = s"docker ps -q -f name=$ConductrNamePrefix".lines_!
    if (containers.nonEmpty) {
      streams.value.log.info(s"Stopping ConductR...")
      val containersArg = containers.mkString(" ")
      s"docker rm -f $containersArg".!(streams.value.log)
    }
  }
}
