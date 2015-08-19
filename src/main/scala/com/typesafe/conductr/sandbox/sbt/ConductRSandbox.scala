package com.typesafe.conductr.sandbox.sbt

import com.typesafe.sbt.bundle.Import.BundleKeys
import com.typesafe.conductr.sbt.ConductRKeys
import sbt._
import sbt.Keys._
import complete.DefaultParsers._
import sbt.complete.Parser
import scala.util.Try

object Import {

  object SandboxKeys {

    val envs = SettingKey[Map[String, String]](
      "conductr-sandbox-envs",
      "A map of environment variables to be set for each ConductR container."
    )

    val image = SettingKey[String](
      "conductr-sandbox-image",
      "The Docker image to use. By default `typesafe-docker-internal-docker.bintray.io/conductr/conductr-dev` is used i.e. the single node version of ConductR. For the full version please [download it via our website](http://www.typesafe.com/products/conductr) and then use just `typesafe-docker-internal-docker.bintray.io/conductr/conductr`."
    )

    val ports = SettingKey[Set[Int]](
      "conductr-sandbox-ports",
      "A sequence of ports to be made public by each of the ConductR containers. This will be complemented to the `endpoints` setting's service ports declared for `sbt-bundle`."
    )

    val debugPort = SettingKey[Int](
      "conductr-sandbox-debugPort",
      "The debug port to be made public by each of the ConductR containers."
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
      "Starts the sandbox environment"
    )

    val stopConductRs = TaskKey[Unit](
      "conductr-sandbox-stop",
      "Stops the sandbox environment"
    )
  }
}

object ConductRSandbox extends AutoPlugin {

  import Import._
  import SandboxKeys._

  val autoImport = Import

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ Seq(
      envs := Map.empty,
      image := ConductRDevImage,
      ports := Set.empty,
      logLevel := "info",
      nrOfContainers := 1,
      runConductRs := runConductRsTask(ScopeFilter(inAnyProject, inAnyConfiguration)).value,
      stopConductRs := stopConductRsTask.value,
      commands := Seq(conductrSandbox)
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    super.projectSettings ++ Seq(
      debugPort := 5005
    )

  private final val ConductRDevImage = "typesafe-docker-internal-docker.bintray.io/conductr/conductr-dev"

  private final val ConductrNamePrefix = "cond-"

  private final val ConductrPort = 9005

  private final val ConductrSandboxControlServerUrl = new sbt.URL(s"http://${resolveDockerHostIp()}:$ConductrPort")

  private final val WithDebugAttrKey = AttributeKey[Unit]("conductr-sandbox-with-debug")

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

    val debugPorts = state.value.get(WithDebugAttrKey).fold(Set.empty[Int])(_ => debugPort.?.all(filter).value.flatten.toSet)

    streams.value.log.info("Running ConductR...")
    val dockerHostIp = resolveDockerHostIp()
    for (i <- 0 until (nrOfContainers in Global).value) {
      val container = s"$ConductrNamePrefix$i"
      val containers = s"docker ps -q -f name=$container".!!
      if (containers.isEmpty) {
        val allPorts = bundlePorts ++ debugPorts ++ (ports in Global).value
        val portsDesc = allPorts.map(port => s"$dockerHostIp:${portMapping(i, port)}").mkString(", ")
        streams.value.log.info(s"Running container $container exposing $portsDesc...")
        val cond0Ip = if (i > 0) Some(inspectCond0Ip()) else None
        runConductRCmd(
          i,
          container,
          cond0Ip,
          (envs in Global).value,
          (image in Global).value,
          (logLevel in Global).value,
          allPorts
        ).!(streams.value.log)
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
      9006,
      9200
    )
    val portsArgs = (portsValue ++ conductrPorts).toSeq.flatMap(port => Seq("-p", s"${portMapping(instance, port)}:$port"))

    val seedNodeArg = cond0Ip.toSeq.flatMap(ip => Seq("--seed", s"$ip:9004"))
    val positionalArgs = Seq(
      imageValue,
      "--discover-host-ip"
    ) ++ seedNodeArg

    (command ++ generalArgs ++ envsArgs ++ portsArgs ++ positionalArgs).mkString(" ")
  }

  private def stopConductRsTask(): Def.Initialize[Task[Unit]] = Def.task {
    val containers = s"docker ps -q -f name=$ConductrNamePrefix".lines_!
    if (containers.nonEmpty) {
      streams.value.log.info(s"Stopping ConductR...")
      val containersArg = containers.mkString(" ")
      s"docker rm -f $containersArg".!(streams.value.log)
    }
  }

  private def conductrSandbox: Command = Command("sandbox", ConductrSandboxHelp)(_ => Parsers.conductrSandboxParser) {
    case (prevState, RunSubtask) =>
      implicit val extracted = Project.extract(prevState)
      prevState.get(WithDebugAttrKey) match {
        case Some(_) =>
          extracted.runTask(stopConductRs, prevState)
          val withoutDebugState = disableDebugMode(prevState)
          runSandboxAndSetSettings(withoutDebugState, controlServerSetting)

        case None =>
          runSandboxAndSetSettings(prevState, controlServerSetting)
      }

    case (prevState, DebugSubtask) =>
      implicit val extracted = Project.extract(prevState)
      prevState.get(WithDebugAttrKey) match {
        case Some(_) =>
          runSandboxAndSetSettings(prevState, controlServerSetting ++ debugSetting)

        case None =>
          extracted.runTask(stopConductRs, prevState)
          val debugState = enableDebugMode(prevState)
          runSandboxAndSetSettings(debugState, controlServerSetting ++ debugSetting)
      }

    case (prevState, StopSubtask) =>
      // Stop sandbox cluster
      Project.runTask(stopConductRs, prevState)
      prevState
  }

  private def runSandboxAndSetSettings(state: State, settings: Seq[Def.Setting[_]])(implicit extracted: Extracted): State = {
    extracted.runTask(runConductRs, state)
    extracted.append(settings, state)
  }

  private def enableDebugMode(state: State): State =
    state.put(WithDebugAttrKey, ())

  private def disableDebugMode(state: State): State =
    state.remove(WithDebugAttrKey)

  private def debugSetting(implicit extracted: Extracted): Seq[Def.Setting[_]] = {
    extracted
      .getOpt(BundleKeys.startCommand)
      .map(_ => BundleKeys.startCommand += s"-jvm-debug ${SandboxKeys.debugPort.value}")
      .toSeq
  }

  private def controlServerSetting(implicit extracted: Extracted): Seq[Def.Setting[_]] =
    extracted
      .getOpt(ConductRKeys.conductrControlServerUrl in Global)
      .map(_ => ConductRKeys.conductrControlServerUrl in Global := ConductrSandboxControlServerUrl)
      .toSeq

  private final val ConductrSandboxHelp = Help.briefOnly(Seq(
    "run" -> "Run the ConductR Cluster in sandbox mode",
    "debug" -> "Run the ConductR Cluster in sandbox mode with remote debugging enabled.",
    "stop" -> "Stop the ConductR cluster."
  ))

  private object Parsers {
    lazy val conductrSandboxParser = (Space ~> (runSubtask | debugSubtask | stopSubtask))
    def runSubtask: Parser[RunSubtask.type] = token("run") map { case _ => RunSubtask }
    def debugSubtask: Parser[DebugSubtask.type] = token("debug") map { case _ => DebugSubtask }
    def stopSubtask: Parser[StopSubtask.type] = token("stop") map { case _ => StopSubtask }
  }

  private sealed trait ConductrSandboxSubtask
  private case object RunSubtask extends ConductrSandboxSubtask
  private case object DebugSubtask extends ConductrSandboxSubtask
  private case object StopSubtask extends ConductrSandboxSubtask
}