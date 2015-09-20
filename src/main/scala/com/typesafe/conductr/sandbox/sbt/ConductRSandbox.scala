package com.typesafe.conductr.sandbox.sbt

import sbt._
import sbt.Keys._
import complete.DefaultParsers._
import sbt.complete.{ FixedSetExamples, Parser }
import scala.util.Try

object Import {

  object SandboxKeys {

    val envs = SettingKey[Map[String, String]](
      "conductr-sandbox-envs",
      "A map of environment variables to be set for each ConductR container."
    )

    val image = SettingKey[String](
      "conductr-sandbox-image",
      "The Docker image to use. By default `typesafe-docker-registry-for-subscribers-only.bintray.io/conductr/conductr-dev` is used i.e. the single node version of ConductR. For the full version please [download it via our website](http://www.typesafe.com/products/conductr) and then use just `typesafe-docker-registry-for-subscribers-only.bintray.io/conductr/conductr`."
    )

    val imageVersion = SettingKey[String](
      "conductr-sandbox-version",
      "The version of the Docker image to use. Must be set. To obtain the current version and additional information, please visit the [ConductR Developer](http://www.typesafe.com/product/conductr/developer) page on Typesafe.com.")

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

    val conductrRoles = SettingKey[Seq[Set[String]]](
      "conductr-sandbox-conductrRoles",
      """Sets additional roles allowed by each ConductR node. By default this is Seq.empty. If no additional roles are set then ConductR is running only bundles configured with the role `web`. To add additional roles specify a set of roles for each node. Example: Seq(Set("megaIOPS"), Set("muchMem", "myDB")) would add the role `megaIOPS` to first ConductR node. `muchMem` and `myDB` would be added to the second node. If the `nrOfContainers` is larger than the size of the `conductrRoles` sequence then the specified roles are subsequently applied to the remaining nodes."""
    )

    val runConductRSandbox = TaskKey[Unit](
      "conductr-sandbox-run",
      "Starts the sandbox environment"
    )
  }
}

object ConductRSandbox extends AutoPlugin {

  import com.typesafe.sbt.bundle.SbtBundle.autoImport._
  import com.typesafe.conductr.sbt.ConductRPlugin.autoImport._
  import Import._
  import SandboxKeys._

  val autoImport = Import

  override def trigger = allRequirements

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ Seq(
      envs := Map.empty,
      image := ConductRDevImage,
      ports := Set.empty,
      logLevel := "info",
      nrOfContainers := 1,
      conductrRoles := Seq.empty,
      runConductRSandbox := runConductRsTask(ScopeFilter(inAnyProject, inAnyConfiguration)).value,
      commands := Seq(conductrSandbox)
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    super.projectSettings ++ Seq(
      debugPort := 5005
    )

  /**
   * Run the ConductRs
   */
  def runConductRs(
    nrOfContainers: Int,
    conductrRoles: Seq[Set[String]],
    allPorts: Set[Int],
    envs: Map[String, String],
    conductrImage: String,
    conductrImageVersion: String,
    featureNames: Set[String],
    log: ProcessLogger,
    logLevel: String): Unit = {

    log.info("Running ConductR...")
    val dockerHostIp = resolveDockerHostIp()
    for (i <- 0 until nrOfContainers) {
      val container = s"$ConductrNamePrefix$i"
      val containers = s"docker ps -q -f name=$container".!!
      if (containers.isEmpty) {
        val portsDesc = allPorts.map(port => s"$dockerHostIp:${portMapping(i, port)}").mkString(", ")
        // Display the ports on the command line. Only if the user specifies a certain feature, then
        // the corresponding port will be displayed when running 'sandbox run' or 'sandbox debug'
        log.info(s"Running container $container exposing $portsDesc...")
        val cond0Ip = if (i > 0) Some(inspectCond0Ip()) else None
        val conductrContainerRoles = conductrRolesByContainer(conductrRoles, i)
        runConductRCmd(
          i,
          container,
          cond0Ip,
          envs,
          s"$conductrImage:$conductrImageVersion",
          logLevel,
          allPorts,
          featureNames,
          conductrContainerRoles
        ).!(log)
      } else
        log.info(s"Container $container already exists, leaving it alone")
    }
  }

  private def conductrRolesByContainer(conductrRoles: Seq[Set[String]], containerNr: Int): Set[String] =
    conductrRoles match {
      case Nil                                                    => Set.empty
      case byContainerNr if containerNr + 1 <= conductrRoles.size => conductrRoles(containerNr)
      case byRemainingContainer =>
        val remainder = (containerNr + 1) % conductrRoles.size
        val container = if (remainder == 0) conductrRoles.size else remainder
        conductrRoles(container - 1)
    }

  /**
   * Stop the ConductRs
   */
  def stopConductRs(log: ProcessLogger): Unit = {
    val containers = s"docker ps -q -f name=$ConductrNamePrefix".lines_!
    if (containers.nonEmpty) {
      log.info(s"Stopping ConductR...")
      val containersArg = containers.mkString(" ")
      s"docker rm -f $containersArg".!(log)
    }
  }

  private final val ConductRDevImage = "typesafe-docker-registry-for-subscribers-only.bintray.io/conductr/conductr-dev"

  private final val ConductrNamePrefix = "cond-"

  private final val ConductrPort = 9005

  private final val ConductrSandboxControlServerUrl = new sbt.URL(s"http://${resolveDockerHostIp()}:$ConductrPort")

  private final val WithDebugAttrKey = AttributeKey[Unit]("conductr-sandbox-with-debug")

  private final val WithFeaturesAttrKey = AttributeKey[Set[Feature]]("conductr-sandbox-with-features")

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
    val conductrImage = (image in Global).value
    val conductrImageVersion = (imageVersion in Global).?.map(_.getOrElse {
      fail("imageVersion. imageVersion must be set. Please visit https://www.typesafe.com/product/conductr/developer for current version information.")
      ""
    }).value

    if (conductrImage == ConductRDevImage && s"docker images -q $ConductRDevImage".!!.isEmpty) {
      streams.value.log.info("Pulling down the development version of ConductR * * * SINGLE NODED AND NOT FOR PRODUCTION USAGE * * *")
      s"docker pull $ConductRDevImage:$conductrImageVersion".!(streams.value.log)
    }

    val features = state.value.get(WithFeaturesAttrKey).toSet.flatten

    val featurePorts = features.map(_.port)

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

    val roles = (conductrRoles in Global).value match {
      case Nil           => Seq(BundleKeys.roles.?.map(_.getOrElse(Set.empty)).all(filter).value.reduce(_ ++ _))
      case conductrRoles => conductrRoles
    }

    runConductRs(
      (nrOfContainers in Global).value,
      roles,
      bundlePorts ++ featurePorts ++ debugPorts ++ (ports in Global).value,
      (envs in Global).value,
      conductrImage,
      conductrImageVersion,
      features.map(_.name),
      streams.value.log,
      (logLevel in Global).value
    )
  }

  private def runConductRCmd(
    instance: Int,
    container: String,
    cond0Ip: Option[String],
    envsValue: Map[String, String],
    imageValue: String,
    logLevelValue: String,
    portsValue: Set[Int],
    featureNames: Set[String],
    conductrRoles: Set[String]): Seq[String] = {

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
    val conductrFeatureArgs = toMap("CONDUCTR_FEATURES", featureNames)(_.mkString(","))
    val conductrRolesEnv = toMap("CONDUCTR_ROLES", conductrRoles)(_.mkString(","))
    val envsArgs = (logLevelEnv ++ syslogEnv ++ conductrFeatureArgs ++ conductrRolesEnv).flatMap { case (k, v) => Seq("-e", s"$k=$v") }.toSeq

    // Expose always the feature related ports even if the are not specified with `--withFeatures`.
    // Therefor these ports are also exposed if only the `runConductRs` tasks is executed (e.g. in testing)
    val conductrPorts = Set(
      9004, // ConductR internal akka remoting
      9005, // ConductR controlServer
      9006, // ConductR bundleStreamServer
      9200, // conductr-elasticsearch bundle
      9999 //  visualizer bundle
    )
    val portsArgs = (portsValue ++ conductrPorts).toSeq.flatMap(port => Seq("-p", s"${portMapping(instance, port)}:$port"))

    val seedNodeArg = cond0Ip.toSeq.flatMap(ip => Seq("--seed", s"$ip:9004"))
    val positionalArgs = Seq(
      imageValue,
      "--discover-host-ip"
    ) ++ seedNodeArg

    (command ++ generalArgs ++ envsArgs ++ portsArgs ++ positionalArgs)
  }

  private def toMap[A, B](key: String, trav: Traversable[A])(f: Traversable[A] => B): Map[String, B] =
    if (trav.nonEmpty)
      Map(key -> f(trav))
    else
      Map.empty[String, B]

  private def conductrSandbox: Command = Command("sandbox", ConductrSandboxHelp)(_ => Parsers.conductrSandboxParser) {
    case (prevState, RunSubtask(featureNames)) =>
      implicit val extracted = Project.extract(prevState)
      prevState.get(WithDebugAttrKey) match {
        case Some(_) =>
          stopConductRs(prevState.log)
          val withoutDebugState = disableDebugMode(prevState)
          runSandboxAndSetSettings(withoutDebugState, controlServerSetting, featureNames)

        case None =>
          runSandboxAndSetSettings(prevState, controlServerSetting, featureNames)
      }

    case (prevState, DebugSubtask(featureNames)) =>
      implicit val extracted = Project.extract(prevState)
      prevState.get(WithDebugAttrKey) match {
        case Some(_) =>
          runSandboxAndSetSettings(prevState, controlServerSetting ++ debugSetting, featureNames)

        case None =>
          stopConductRs(prevState.log)
          val debugState = enableDebugMode(prevState)
          runSandboxAndSetSettings(debugState, controlServerSetting ++ debugSetting, featureNames)
      }

    case (prevState, StopSubtask) =>
      // Stop sandbox cluster
      stopConductRs(prevState.log)
      prevState
  }

  private def runSandboxAndSetSettings(state: State, settings: Seq[Def.Setting[_]], features: Set[String])(implicit extracted: Extracted): State = {
    val featuresState =
      if (features.isEmpty)
        state.remove(WithFeaturesAttrKey)
      else
        state.put(WithFeaturesAttrKey, features.map(Feature(_)))
    extracted.runTask(runConductRSandbox, featuresState)
    extracted.append(settings, featuresState)
  }

  private def enableDebugMode(state: State): State =
    state.put(WithDebugAttrKey, ())

  private def disableDebugMode(state: State): State =
    state.remove(WithDebugAttrKey)

  private def debugSetting(implicit extracted: Extracted): Seq[Def.Setting[_]] = {
    extracted
      .getOpt(BundleKeys.startCommand)
      .map(_ => BundleKeys.startCommand += s"-jvm-debug ${SandboxKeys.debugPort.value}")
      .toList
  }

  private def controlServerSetting(implicit extracted: Extracted): Seq[Def.Setting[_]] =
    extracted
      .getOpt(ConductRKeys.conductrControlServerUrl in Global)
      .map(_ => ConductRKeys.conductrControlServerUrl in Global := ConductrSandboxControlServerUrl)
      .toList

  private final val ConductrSandboxHelp = Help.briefOnly(Seq(
    "run" -> "Run the ConductR Cluster in sandbox mode",
    "debug" -> "Run the ConductR Cluster in sandbox mode with remote debugging enabled.",
    "stop" -> "Stop the ConductR cluster."
  ))

  private object Parsers {
    lazy val conductrSandboxParser = Space ~> (runSubtask | debugSubtask | stopSubtask)
    def runSubtask: Parser[RunSubtask] =
      (token("run") ~> OptSpace ~> withFeatures.?) map { case features => RunSubtask(features.toSet.flatten) }
    def debugSubtask: Parser[DebugSubtask] =
      (token("debug") ~> OptSpace ~> withFeatures.?) map { case features => DebugSubtask(features.toSet.flatten) }
    def stopSubtask: Parser[StopSubtask.type] = token("stop") map { case _ => StopSubtask }

    def withFeatures: Parser[Set[String]] = "--withFeatures" ~> Space ~> features
    def features: Parser[Set[String]] =
      repeatDep((remainingFeatures: Seq[String]) =>
        StringBasic.examples(FixedSetExamples(ConductrFeatures diff remainingFeatures.toSet), 1, removeInvalidExamples = true), Space).map(_.toSet)

    final val ConductrFeatures = Set("visualization", "logging")
  }

  private sealed trait ConductrSandboxSubtask
  private case class RunSubtask(features: Set[String]) extends ConductrSandboxSubtask
  private case class DebugSubtask(features: Set[String]) extends ConductrSandboxSubtask
  private case object StopSubtask extends ConductrSandboxSubtask

  private case class Feature(name: String, port: Int)
  private object Feature {
    def apply(name: String): Feature =
      name match {
        case "visualization" => Feature(name, 9999)
        case "logging"       => Feature(name, 9200)
      }
  }
}
