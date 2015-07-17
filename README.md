# ConductR Sandbox

[![Build Status](https://api.travis-ci.org/sbt/sbt-conductr-sandbox.png?branch=master)](https://travis-ci.org/sbt/sbt-conductr-sandbox)

## Introduction

sbt-conductr-sandbox aims to support the running of a Docker-based ConductR cluster in the context of a build. The cluster can then be used to assist you in order to verify that endpoint declarations and other aspects of a bundle configuration are correct. The general idea is that this plugin will also support you when building your project on CI so that you may automatically verify that it runs on ConductR.

## Usage

If you have not done so already, then please [install Docker](https://www.docker.com/).

Declare the plugin in addition to [`sbt-conductr`](https://github.com/sbt/sbt-conductr#sbt-conductr) (typically in a `plugins.sbt`):

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-conductr-sandbox" % "0.1.0")
```

Then enable the `ConductRSandbox` plugin for your module. For example:

```scala
lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, ConductRSandbox)
```

Given the above you will then have a ConductR process running in the background (there will be an initial download cost for Docker to download the `conductr/conductr-dev` image from the public Docker registry).

`sbt-conductr-sandbox` has a requirement for the `sbt-conductr` plugin to be enabled for your project. Given the `JavaAppPackaging`, `sbt-conductr` will become enabled. `sbt-conductr-sandbox` will automatically override the `sbt-conductr` `conductrControlServerUrl` setting so that `conduct info` and other `conduct` commands will communicate with the Docker cluster managed by the sandbox.

> Note that the ConductR cluster will take a few seconds to become available and so any initial command that you send to it may not work immediately.

The sandbox cluster will automatically stop when your sbt module becomes out of scope e.g. when your sbt session terminates.

# Docker Container Naming

Each node of the Docker cluster managed by the sandbox is given a name of the form `cond-n` where `n` is the node number starting at zero. Thus `cond-0` is the first node (and the only node given default settings).

## Settings

The following settings are provided under the `SandboxKeys` object:

Name              | Description
------------------|-------------
envs              | A `Map[String, String]` of environment variables to be set for each ConductR container.
image             | The Docker image to use. By default `conductr/conductr-dev` is used i.e. the single node version of ConductR. For the full version please [download it via our website](http://www.typesafe.com/products/conductr) and then use just `conductr/conductr`.
ports             | A `Set[Int]` ports to be made public by each of the ConductR containers. By default, this will be initialized to the `endpoints` setting's service ports declared for `sbt-bundle`.
logLevel          | The log level of ConductR which can be one of "debug", "warning" or "info". By default this is set to "info". You can observe ConductR's logging via the `docker logs` command. For example `docker logs -f cond-0` will follow the logs of the first ConductR container.
nrOfContainers    | Sets the number of ConductR containers to run in the background. By default 1 is run. Note that by default you may only have more than one if the image being used is *not* conductr/conductr-dev (the default, single node version of ConductR for general development).

&copy; Typesafe Inc., 2015
