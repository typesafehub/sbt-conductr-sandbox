# ConductR Sandbox

[![Build Status](https://api.travis-ci.org/typesafehub/sbt-conductr-sandbox.png?branch=master)](https://travis-ci.org/typesafehub/sbt-conductr-sandbox)

## Introduction

sbt-conductr-sandbox aims to support the running of a Docker-based ConductR cluster in the context of a build. The cluster can then be used to assist you in order to verify that endpoint declarations and other aspects of a bundle configuration are correct. The general idea is that this plugin will also support you when building your project on CI so that you may automatically verify that it runs on ConductR.

## Usage

The single node version of the ConductR Developer Sandbox is available gratis with registration at Typesafe.com. Please visit the [ConductR Developer](http://www.typesafe.com/product/conductr/developer) page on Typesafe.com for the current image version and licensing information. Use of the ConductR in multi-node mode or for production purposes requires the purchase of Typesafe ConductR. You <strong>must</strong> specify the current imageVersion which you can obtain from the [ConductR Developer](http://www.typesafe.com/product/conductr/developer) page.

If you have not done so already, then please [install Docker](https://www.docker.com/).

Declare the plugin (typically in a `plugins.sbt`):

```scala
addSbtPlugin("com.typesafe.conductr" % "sbt-conductr-sandbox" % "1.0.3")
```

Then enable the `ConductRSandbox` plugin for your module. For example:

```scala
lazy val root = (project in file(".")).enablePlugins(ConductRSandbox)
```

### Starting ConductR sandbox

To run the sandbox environment use the following command:

```scala
sandbox run
```

> Note that the ConductR cluster will take a few seconds to become available and so any initial command that you send to it may not work immediately.

Given the above you will then have a ConductR process running in the background (there will be an initial download cost for Docker to download the image from the public Docker registry).

If the `sbt-conductr` plugin is enabled for your project then the `conduct info` and other `conduct` commands will automatically communicate with the Docker cluster managed by the sandbox.

#### Starting with ConductR features

To enable optional ConductR features for your sandbox specify the `--withFeatures` option during startup, e.g.:
    
```scala
[my-app] sandbox run --withFeatures visualization logging
[info] Running ConductR...
[info] Running container cond-0 exposing 192.168.59.103:9000 192.168.59.103:9909...
```

Check out the [Features](#features) section for more information.

### Debugging application in ConductR sandbox

It is possible to debug your application inside of the ConductR sandbox:

1. Start ConductR sandbox in debug mode:

    ```scala
    sandbox debug
    ```
2. This exposes a debug port to docker and the ConductR container. By default the debug port is set to `5005` which is the default port for remote debugging in IDEs such as IntelliJ. If you are happy with the default setting skip this step. Otherwise specify another port in the `build.sbt`:

    ```scala
    SandboxKeys.debugPort := 5432
    ```    
4. Load and run your bundle to the sandbox, e.g. by using [sbt-conductr](https://github.com/sbt/sbt-conductr):

    ```scala
    conduct load <HIT THE TAB KEY AND THEN RETURN>
    conduct run my-app
    ```

You should be now able to remotely debug the bundle inside the ConductRs sandbox with your favorite IDE.    

### Stopping ConductR sandbox

To stop the sandbox use: 

```scala
sandbox stop
```

## Features

> In order to use the following features then you should ensure that the machine that runs Docker has enough memory, typically at least 2GB. VM configurations such as those provided via `boot2docker` and Oracle's VirtualBox can be configured like so:
> ```
> boot2docker down
> VBoxManage modifyvm boot2docker-vm --memory 2048
> boot2docker up
> ```

ConductR provides additional features which can be optionally enabled:

Name          | CondutR port | Docker port | Description
--------------|--------------|-------------|------------
visualization | 9999         | 9909        | Provides a web interface to visualize the ConductR cluster together with deployed and running bundles.  After enabling the feature, access it at http://{docker-host-ip}:9909.
logging       | 9200         | 9200        | Consolidates the logging output of ConductR itself and the bundles that it executes. To view the consolidated log messsages enable [sbt-conductr](https://github.com/sbt/sbt-conductr) and then `conduct logs conductr-elasticsearch`.

## Docker Container Naming

Each node of the Docker cluster managed by the sandbox is given a name of the form `cond-n` where `n` is the node number starting at zero. Thus `cond-0` is the first node (and the only node given default settings).

## Port Mapping Convention

The following ports are exposed to the ConductR and Docker containers:
- `BundleKeys.endpoints` of `sbt-bundle`
- `SandboxKeys.ports`
- `SandboxKeys.debugPort` if sandbox is started in debug mode

### Example
Your application defines these settings in the `build.sbt`:

```
lazy val root = (project in file(".")).enablePlugins(ConductRPlugin, ConductRSandbox)

BundleKeys.endpoints := Map("sample-app" -> Endpoint("http", services = Set(uri("http://:9000"))))
SandboxKeys.image in Global := "conductr/conductr"
SandboxKeys.nrOfContainers in Global := 3
SandboxKeys.ports in Global := Set(1111)
SandboxKeys.debugPort := 5095
```

In this case we want to create a ConductR sandbox cluster with 3 nodes. 

> Note that a cluster with more than one node is only possible with the full version of ConductR. If `SandboxKeys.image` is not overridden the single node version will be used. 

We also specify that the web application should serve traffic on port 9000. Additionally we expose port 1111. The debug port gets only mapped if we start the ConductR sandbox cluster in debug mode with `sandbox debug`.

These settings result in the following port mapping:

Docker container | ConductR port | Docker internal port | Docker public port
-----------------|---------------|----------------------|-------------------
cond-0           | 9000          | 9000                 | 9000
cond-1           | 9000          | 9000                 | 9010
cond-2           | 9000          | 9000                 | 9020
cond-0           | 1111          | 1111                 | 1101
cond-1           | 1111          | 1111                 | 1111
cond-2           | 1111          | 1111                 | 1121
cond-0           | 5095          | 5095                 | 5005
cond-1           | 5095          | 5095                 | 5015
cond-2           | 5095          | 5095                 | 5025

Each specified port is mapped to a unique public Docker port in order to allow multiple nodes within the sandbox cluster. By convention, the port is mapped for the first Docker container to XX0X, the second will be XX1X, the third will be XX2X. 
The web application becomes available on the IP addresses of the Docker containers that host each ConductR process. The sandbox cluster is configured with a proxy and will automatically route requests to the correct instances that you have running in the cluster. Therefore any one of the addresses with the 9000, 9010 or 9020 ports will reach your application.

As a convenience, `sandbox run` and `sandbox debug` reports each of the above mappings along with IP addresses.

## Settings

The following settings are provided under the `SandboxKeys` object:

Name              | Scope   | Description
------------------|---------|------------
envs              | Global  | A `Map[String, String]` of environment variables to be set for each ConductR container.
image             | Global  | The Docker image to use. By default `typesafe-docker-internal-docker.bintray.io/conductr/conductr-dev` is used i.e. the single node version of ConductR. For the full version please [download it via our website](http://www.typesafe.com/products/conductr) and then use just `typesafe-docker-internal-docker.bintray.io/conductr/conductr`.
imageVersion      | Global  | The version of the Docker image to use. Must be set. Please visit the [ConductR Developer](http://www.typesafe.com/product/conductr/developer) page on Typesafe.com for the current version and additional information.
ports             | Global  | A `Seq[Int]` of ports to be made public by each of the ConductR containers. This will be complemented to the `endpoints` setting's service ports declared for `sbt-bundle`.
debugPort         | Project | Debug port to be made public to the ConductR containers if the sandbox gets started in [debug mode](#Commands). The debug ports of each sbt project setting will be used. If `sbt-bundle` is enabled the JVM argument `-jvm-debug $debugPort` is  additionally added to the `startCommand` of `sbt-bundle`. Default is 5005.
logLevel          | Global  | The log level of ConductR which can be one of "debug", "warning" or "info". By default this is set to "info". You can observe ConductR's logging via the `docker logs` command. For example `docker logs -f cond-0` will follow the logs of the first ConductR container.
nrOfContainers    | Global  | Sets the number of ConductR containers to run in the background. By default 1 is run. Note that by default you may only have more than one if the image being used is *not* conductr/conductr-dev (the default, single node version of ConductR for general development).
runConductRs      | Global  | Starts the sandbox environment as a task - useful for setting up test environments within sbt. Note that a `ConductRSandbox.stopConductRs(logger)` function call can be made to close down the sandbox environment programmatically. `logger` is of sbt's type `ProcessLogger`. Therefore a `streams.value.log` can be provided.

## Commands

The following commands are provided:

Name          | Description
--------------|-------------
sandbox run   | Starts the ConductR sandbox cluster without remote debugging facilities.
sandbox debug | Starts the ConductR sandbox cluster and exposes the `debugPort` to docker and ConductR to enable remote debugging.
sandbox stop  | Stops the ConductR sandbox cluster.

&copy; Typesafe Inc., 2015
