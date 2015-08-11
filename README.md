# ConductR Sandbox

[![Build Status](https://api.travis-ci.org/typesafehub/sbt-conductr-sandbox.png?branch=master)](https://travis-ci.org/typesafehub/sbt-conductr-sandbox)

## Introduction

sbt-conductr-sandbox aims to support the running of a Docker-based ConductR cluster in the context of a build. The cluster can then be used to assist you in order to verify that endpoint declarations and other aspects of a bundle configuration are correct. The general idea is that this plugin will also support you when building your project on CI so that you may automatically verify that it runs on ConductR.

## Usage

If you have not done so already, then please [install Docker](https://www.docker.com/).

Declare the plugin (typically in a `plugins.sbt`):

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-conductr-sandbox" % "0.2.0")
```

Then enable the `ConductRSandbox` plugin for your module. For example:

```scala
lazy val root = (project in file(".")).enablePlugins(ConductRSandbox)
```

To run the sandbox environment use the following task:

```scala
conductr-sandbox-run
```

> Note that the ConductR cluster will take a few seconds to become available and so any initial command that you send to it may not work immediately.

Given the above you will then have a ConductR process running in the background (there will be an initial download cost for Docker to download the `conductr/conductr-dev` image from the public Docker registry).

To stop the cluster use the `conductr-sandbox-stop` task.

If the `sbt-conductr` plugin is enabled for your project then the `conduct info` and other `conduct` commands can communicate with the Docker cluster managed by the sandbox. To set this up type the following command within the sbt console:

```scala
sandboxControlServer
```

## Docker Container Naming

Each node of the Docker cluster managed by the sandbox is given a name of the form `cond-n` where `n` is the node number starting at zero. Thus `cond-0` is the first node (and the only node given default settings).

## Port Mapping Convention

If your application or service exposes a port via `sbt-bundle`'s `endpoints` declaration then these ports will be automatically exposed. For example, if your web application serves traffic on port 9000 then it will become available on the IP addresses of the Docker containers that host each ConductR process. By convention, the first container will expose port 9000, the second will be 9010 and the third will be 9020. The sandbox cluster is configured with a proxy and will automatically route requests to the correct instances that you have running in the cluster. Therefore any one of the addresses with the 9000, 9010 or 9020 ports will reach your application.

As a convenience, `sbt-conductr-sandbox` reports each of the above mappings along with IP addresses when you use the `runConductRs` task.

## Settings

The following settings are provided under the `SandboxKeys` object:

Name              | Description
------------------|-------------
envs              | A `Map[String, String]` of environment variables to be set for each ConductR container.
image             | The Docker image to use. By default `conductr/conductr-dev` is used i.e. the single node version of ConductR. For the full version please [download it via our website](http://www.typesafe.com/products/conductr) and then use just `conductr/conductr`.
ports             | A `Seq[Int]` of ports to be made public by each of the ConductR containers. This will be complemented to the `endpoints` setting's service ports declared for `sbt-bundle`.
logLevel          | The log level of ConductR which can be one of "debug", "warning" or "info". By default this is set to "info". You can observe ConductR's logging via the `docker logs` command. For example `docker logs -f cond-0` will follow the logs of the first ConductR container.
nrOfContainers    | Sets the number of ConductR containers to run in the background. By default 1 is run. Note that by default you may only have more than one if the image being used is *not* conductr/conductr-dev (the default, single node version of ConductR for general development).
runConductRs      | Starts the sandbox environment.
stopConductRs     | Stops the sandbox environment.

## Commands

The following command is provided:

Name                 | Description
---------------------|-------------
sandboxControlServer | Invokes the `controlServer` command of sbt-conductr with the url of the sandbox. This then enables the regular "conduct info" and other commands to be used with the sandbox. Requires the `sbt-conductr` plugin.

&copy; Typesafe Inc., 2015
