package com.typesafe.conductr.sandbox.sbt

import com.typesafe.conductr.sbt.ConductRPlugin
import sbt._
import sbt.Keys._
import complete.DefaultParsers._
import sbt.complete.{ FixedSetExamples, Parser }

object Import {

  object SandboxKeys {

    val envs = SettingKey[Map[String, String]](
      "conductr-sandbox-envs",
      "A map of environment variables to be set for each ConductR container."
    )

    val image = SettingKey[String](
      "conductr-sandbox-image",
      "The Docker image to use. By default `typesafe-docker-registry-for-subscribers-only.bintray.io/conductr/conductr` is used."
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

    val conductrRoles = SettingKey[Seq[Set[String]]](
      "conductr-sandbox-conductrRoles",
      """Sets additional roles allowed by each ConductR node. By default this is Seq.empty. If no additional roles are set then ConductR is running only bundles configured with the role `web`. To add additional roles specify a set of roles for each node. Example: Seq(Set("megaIOPS"), Set("muchMem", "myDB")) would add the role `megaIOPS` to first ConductR node. `muchMem` and `myDB` would be added to the second node. If the `nrOfContainers` is larger than the size of the `conductrRoles` sequence then the specified roles are subsequently applied to the remaining nodes."""
    )

    val runConductRSandbox = TaskKey[Unit](
      "conductr-sandbox-run",
      "Starts the sandbox environment"
    )

    val hasRpLicense = SettingKey[Boolean](
      "conductr-has-rp-license",
      "Checks that the project has a reactive platform license"
    )

    val isSbtBuild = SettingKey[Boolean](
      "conductr-is-sbt-build",
      "True if the project is THE sbt build project."
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

  override def requires = ConductRPlugin

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ Seq(
      envs := Map.empty,
      image := ConductRImage,
      ports := Set.empty,
      logLevel := "info",
      conductrRoles := Seq.empty,
      runConductRSandbox := runConductRsTask(ScopeFilter(inAnyProject, inAnyConfiguration)).value,
      commands := Seq(conductrSandbox),
      hasRpLicense := {
        // Same logic as in https://github.com/typesafehub/reactive-platform
        // Doesn't take reactive-platform as a dependency because it is not public.
        val isMeta = (isSbtBuild in LocalRootProject).value
        val base = (Keys.baseDirectory in LocalRootProject).value
        val propFile = if (isMeta) base / TypesafePropertiesName else base / "project" / TypesafePropertiesName
        propFile.exists
      },

      // Internal keys
      useDebugPort := false
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    super.projectSettings ++ Seq(
      debugPort := 5005,
      // Here we try to detect what binary universe we exist inside, so we can
      // accurately grab artifact revisions.
      isSbtBuild := Keys.sbtPlugin.?.value.getOrElse(false) && (Keys.baseDirectory in ThisProject).value.getName == "project",

      // We redefine the start command here given that we want it to pick up debug options automatically
      // when debug is required. The logic below takes care of inserting the debug option. Note that we
      // use the dependent executableScriptPath setting in order to detect whether a startCommand has been declared
      // for the current project i.e. we have to test a setting and not try to run a task (startCommand is a task).
      // If we run the startCommand task then we'll just get recursion here.
      BundleKeys.startCommand in Bundle := Def.taskDyn {
        if (Project.extract(state.value).getOpt(BundleKeys.executableScriptPath in (thisProjectRef.value, Bundle)).isDefined) {
          Def.task[Seq[String]] {
            val startCommand = (BundleKeys.startCommand in Bundle).value
            val jvmDebugOptions =
              if ((useDebugPort in Global).value)
                Seq("-jvm-debug", SandboxKeys.debugPort.value.toString)
              else
                Seq.empty
            startCommand.headOption.toList ++ jvmDebugOptions ++ startCommand.tail
          }
        } else
          Def.task(Seq.empty[String])
      }.value
    )

  /**
   * Run the ConductRs
   */
  def runConductRs(
    conductrHostIp: String,
    nrOfContainers: Int,
    conductrRoles: Seq[Set[String]],
    allPorts: Set[Int],
    envs: Map[String, String],
    conductrImage: String,
    conductrImageVersion: String,
    featureNames: Set[String],
    log: ProcessLogger,
    logLevel: String): Unit = {

    val runningContainers = resolveRunningDockerContainers()

    def startNodes(): Unit = {
      log.info("Starting ConductR nodes..")
      for (i <- 0 until nrOfContainers) {
        val containerName = s"$ConductrNamePrefix$i"
        val containerId = s"docker ps --quiet --filter name=$containerName".!!
        if (containerId.isEmpty) {
          val portsDesc = allPorts.map(port => s"$conductrHostIp:${portMapping(i, port)}").mkString(", ")
          // Display the ports on the command line. Only if the user specifies a certain feature, then
          // the corresponding port will be displayed when running 'sandbox run' or 'sandbox debug'
          log.info(s"Starting container $containerName exposing $portsDesc...")
          val cond0Ip = if (i > 0) Some(inspectCond0Ip()) else None
          val conductrContainerRoles = conductrRolesByContainer(conductrRoles, i)
          runConductRCmd(
            i,
            containerName,
            cond0Ip,
            envs,
            s"$conductrImage:$conductrImageVersion",
            logLevel,
            allPorts,
            featureNames,
            conductrContainerRoles
          ).!(log)
        } else
          log.info(s"ConductR node $containerName already exists, leaving it alone.")
      }
    }
    def stopNodes(): Unit = {
      log.info("Stopping ConductR nodes..")
      val containersToBeStopped = runningContainers.takeRight(runningContainers.size - nrOfContainers)
      removeDockerContainers(containersToBeStopped, log)
    }

    val nrOfRunningContainers = runningContainers.size
    nrOfRunningContainers match {
      case start if nrOfContainers > nrOfRunningContainers => startNodes()
      case stop if nrOfContainers < nrOfRunningContainers  => stopNodes()
      case keep => log.info(s"ConductR nodes ${runningContainers.mkString(", ")} already exists, leaving them alone.")
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
    val containers = resolveRunningDockerContainers()
    if (containers.nonEmpty) {
      log.info(s"Stopping ConductR..")
      removeDockerContainers(containers, log)
    }
  }

  /**
   * Resolve running docker containers.
   * @return the running container names (e.g. cond-0) in ascending order
   * TODO: Use the following command if Docker 1.8 or above is supported only:
   *       s"""docker ps --quiet --format "{{.Names}}" --filter name=$ConductrNamePrefix"""
   */
  private def resolveRunningDockerContainers(): Seq[String] = {
    val containerIds = s"docker ps --quiet --filter name=$ConductrNamePrefix".lines_!
    containerIds
      .map(id => s"docker inspect --format {{.Name}} $id".!!.trim.drop(1)) // map to container names
      .sortWith(_ < _) // sort names in ascending order
  }


  private def removeDockerContainers(containers: Seq[String], log: ProcessLogger): Unit =
    s"docker rm -f ${containers.mkString(" ")}".!(log)

  private final val ConductRImage = "typesafe-docker-registry-for-subscribers-only.bintray.io/conductr/conductr"

  private final val LatestConductRVersion = "1.0.12"

  private final val DefaultNrOfContainers = 1

  private final val TypesafePropertiesName = "typesafe.properties"

  private final val ConductrNamePrefix = "cond-"

  private final val WithDebugAttrKey = AttributeKey[Unit]("conductr-sandbox-with-debug")

  private final val WithFeaturesAttrKey = AttributeKey[Set[Feature]]("conductr-sandbox-with-features")

  private final val NrOfContainersAttrKey = AttributeKey[Int]("conductr-sandbox-nr-of-containers")

  private val useDebugPort = SettingKey[Boolean]("conductr-sandbox-use-debug-port", "")

  private def inspectCond0Ip(): String =
    s"""docker inspect --format="{{.NetworkSettings.IPAddress}}" ${ConductrNamePrefix}0""".!!.trim

  def portMapping(instance: Int, port: Int): String = {
    val portStrRev = port.toString.reverse
    val currentSecondLastNr = portStrRev.drop(1).take(1).toInt
    val newSecondLastNr = if(instance == 0) currentSecondLastNr else (currentSecondLastNr + instance) % 10
    (portStrRev.take(1) + newSecondLastNr + portStrRev.drop(2)).reverse
  }

  // FIXME: The filter must be passed in presently: https://github.com/sbt/sbt/issues/1095
  private def runConductRsTask(filter: ScopeFilter): Def.Initialize[Task[Unit]] = Def.task {
    val conductrImage = (image in Global).value
    val conductrImageVersion = (imageVersion in Global).?.value match {
      case Some(versionValue)               => versionValue
      case hasLicense if hasRpLicense.value => LatestConductRVersion
      case None =>
        fail("imageVersion. imageVersion must be set. Please visit https://www.typesafe.com/product/conductr/developer for current version information.")
        ""
    }

    if (conductrImage == ConductRImage && s"docker images -q $ConductRImage".!!.isEmpty) {
      streams.value.log.info("Pulling down the ConductR development image..")
      s"docker pull $ConductRImage:$conductrImageVersion".!(streams.value.log)
    }

    val nrOfContainers = state.value.get(NrOfContainersAttrKey).getOrElse(DefaultNrOfContainers)

    val features = state.value.get(WithFeaturesAttrKey).toSet.flatten

    val featurePorts = features.flatMap(_.port)

    val bundlePorts = (BundleKeys.endpoints in Bundle).?.map(_.getOrElse(Map.empty)).all(filter).value
      .flatten
      .map(_._2)
      .toSet
      .flatMap { endpoint: Endpoint =>
        endpoint.services.map { uri =>
          if (uri.getHost != null) uri.getPort else uri.getAuthority.drop(1).toInt
        }.collect {
          case port if port >= 0 => port
        }
      }

    val debugPorts = state.value.get(WithDebugAttrKey).fold(Set.empty[Int])(_ => debugPort.?.all(filter).value.flatten.toSet)

    val roles = (conductrRoles in Global).value match {
      case Nil                => Seq((BundleKeys.roles in Bundle).?.map(_.getOrElse(Set.empty)).all(filter).value.reduce(_ ++ _))
      case conductrRolesValue => conductrRolesValue
    }

    runConductRs(
      (ConductRKeys.conductrControlServerUrl in Global).value.getHost,
      nrOfContainers,
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
    val envsArgs = (envsValue ++ logLevelEnv ++ syslogEnv ++ conductrFeatureArgs ++ conductrRolesEnv).flatMap { case (k, v) => Seq("-e", s"$k=$v") }.toSeq

    // Expose always the feature related ports even if the are not specified with `--with-features`.
    // Therefor these ports are also exposed if only the `runConductRs` tasks is executed (e.g. in testing)
    val conductrPorts = Set(
      5601, // conductr-kibana bundle
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

    command ++ generalArgs ++ envsArgs ++ portsArgs ++ positionalArgs
  }

  private def toMap[A, B](key: String, trav: Traversable[A])(f: Traversable[A] => B): Map[String, B] =
    if (trav.nonEmpty)
      Map(key -> f(trav))
    else
      Map.empty[String, B]

  private def conductrSandbox: Command = Command("sandbox", ConductrSandboxHelp)(_ => Parsers.conductrSandboxParser) {
    case (prevState, RunSubtask(nrOfContainers, featureNames)) =>
      prevState.get(WithDebugAttrKey) match {
        case Some(_) =>
          stopConductRs(prevState.log)
          val withoutDebugState = disableDebugMode(prevState)
          runSandbox(withoutDebugState, nrOfContainers, featureNames)

        case None =>
          runSandbox(prevState, nrOfContainers, featureNames)
      }

    case (prevState, DebugSubtask(nrOfContainers, featureNames)) =>
      prevState.get(WithDebugAttrKey) match {
        case Some(_) =>
          runSandbox(prevState, nrOfContainers, featureNames, debugMode = true)

        case None =>
          stopConductRs(prevState.log)
          val debugState = enableDebugMode(prevState)
          runSandbox(debugState, nrOfContainers, featureNames, debugMode = true)
      }

    case (prevState, StopSubtask) =>
      stopSandbox(prevState)
  }

  private def runSandbox(state: State, nrOfContainers: Int, features: Set[String], debugMode: Boolean = false): State = {
    // Modify attributes based on command input
    val state1 = if (features.isEmpty)
      state.remove(WithFeaturesAttrKey)
    else
      state.put(WithFeaturesAttrKey, features.map(Feature(_)))
    val state2 = state1.put(NrOfContainersAttrKey, nrOfContainers)

    // Run task
    val extracted = Project.extract(state2)
    val (state3, _) = extracted.runTask(runConductRSandbox, state2)

    // Create and append new settings
    val newSettings = Seq(
      useDebugPort in Global := debugMode
    )
    extracted.append(newSettings, state3)
  }

  private def stopSandbox(state: State): State = {
    val newSettings = Seq(
      useDebugPort in Global := false
    )
    val s1 = Project.extract(state).append(newSettings, state)
    stopConductRs(s1.log)
    s1
  }

  private def enableDebugMode(state: State): State =
    state.put(WithDebugAttrKey, ())

  private def disableDebugMode(state: State): State =
    state.remove(WithDebugAttrKey)

  private final val ConductrSandboxHelp = Help.briefOnly(Seq(
    "run" -> "Run the ConductR Cluster in sandbox mode",
    "debug" -> "Run the ConductR Cluster in sandbox mode with remote debugging enabled.",
    "stop" -> "Stop the ConductR cluster."
  ))

  private object Parsers {
    lazy val conductrSandboxParser = Space ~> (runSubtask | debugSubtask | stopSubtask)
    def runSubtask: Parser[RunSubtask] =
      (token("run") ~> startOptions)
        .map { case (nrOfContainers, features) => RunSubtask(nrOfContainers.getOrElse(DefaultNrOfContainers), features.toSet.flatten) }
    def debugSubtask: Parser[DebugSubtask] =
      (token("debug") ~> startOptions)
        .map { case (nrOfContainers, features) => DebugSubtask(nrOfContainers.getOrElse(DefaultNrOfContainers), features.toSet.flatten) }
    def stopSubtask: Parser[StopSubtask.type] =
      token("stop") map { case _ => StopSubtask }

    def startOptions: Parser[(Option[Int], Option[Set[String]])] = OptSpace ~> nrOfContainers.? ~ (OptSpace ~> withFeatures).?
    def nrOfContainers: Parser[Int] = token("--nr-of-containers") ~> Space ~> token(NatBasic.examples((1 to 9).map(_.toString).toSet))
    def withFeatures: Parser[Set[String]] = token("--with-features") ~> Space ~> token(features)
    def features: Parser[Set[String]] =
      repeatDep((remainingFeatures: Seq[String]) =>
        StringBasic.examples(FixedSetExamples(ConductrFeatures diff remainingFeatures.toSet), 1, removeInvalidExamples = true), Space).map(_.toSet)

    final val ConductrFeatures = Set("visualization", "logging", "monitoring")
  }

  private sealed trait ConductrSandboxSubtask
  private case class RunSubtask(nrOfContainers: Int, features: Set[String]) extends ConductrSandboxSubtask
  private case class DebugSubtask(nrOfContainers: Int, features: Set[String]) extends ConductrSandboxSubtask
  private case object StopSubtask extends ConductrSandboxSubtask

  private case class Feature(name: String, port: Set[Int])
  private object Feature {
    def apply(name: String): Feature =
      name match {
        case "visualization" => Feature(name, Set(9999))
        case "logging"       => Feature(name, Set(5601, 9200))
        case "monitoring"    => Feature(name, Set.empty)
      }
  }
}
