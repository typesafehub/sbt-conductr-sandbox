# ConductR Sandbox

[![Build Status](https://api.travis-ci.org/typesafehub/sbt-conductr-sandbox.png?branch=master)](https://travis-ci.org/typesafehub/sbt-conductr-sandbox)

## Introduction

sbt-conductr-sandbox aims to support the running of a Docker-based ConductR cluster in the context of a build. The cluster can then be used to assist you in order to verify that endpoint declarations and other aspects of a bundle configuration are correct. The general idea is that this plugin will also support you when building your project on CI so that you may automatically verify that it runs on ConductR.

> Note that because the sandbox uses Docker, you cannot run bundles that also use Docker - Docker cannot run within Docker! If you have a Docker bundle then we recommend that you setup a VM for development and follow [the regular installation guide of our docs](http://conductr.typesafe.com/); perhaps just for a single node to make your setup a bit easier.

## Prerequisites

* [Docker](https://www.docker.com/)
* [conductr-cli](http://conductr.lightbend.com/docs/1.1.x/CLI)

Docker is required so that you can run the ConductR cluster as if it were running on a number of machines in your network. You won't need to understand much about Docker for ConductR other than installing it as described in its "Get Started" section. If you are on Windows or Mac then you will become familiar with `docker-machine` which is a utility that controls a virtual machine for the purposes of running Docker.

The conductr-cli is used to communicate with the ConductR cluster.

## Usage

1. Add `sbt-conductr-sandbox` to the `project/plugins.sbt`: 

    ```
    addSbtPlugin("com.typesafe.conductr" % "sbt-conductr-sandbox" % "1.4.2")
    ```
2. Verify that `sbt-bundle` keys have been set. For further information refer to the [sbt-bundle](https://github.com/sbt/sbt-bundle) documentation:

    ```
    import ByteConversions._
    BundleKeys.nrOfCpus := 1.0
    BundleKeys.memory := 64.MiB
    BundleKeys.diskSpace := 10.MB
    ```    
3. Specify the ConductR Developer Sandbox version in the `build.sbt`. This version is available gratis during development with registration at lightbend.com. Please visit the [ConductR Developer](http://www.lightbend.com/product/conductr/developer) page for usage instructions.

    ```
    SandboxKeys.imageVersion in Global := "YOUR_CONDUCTR_SANDBOX_VERSION"
    ````
4. Reload the sbt session:

    ```
    reload
    ```    
       
The plugin is then enabled automatically for your entire project. Additionally it includes [sbt-conductr](https://github.com/sbt/sbt-conductr) and [sbt-bundle](https://github.com/sbt/sbt-bundle) so these plugins doesn’t need to be added separately. The conduct commands such as conduct info will automatically communicate with the Docker cluster managed by the sandbox. There is no need to set ConductR’s ip address with controlServer manually.

To start the sandbox use the `run` command:

```
sandbox run
[info] Running ConductR...
[info] Running container cond-0 exposing 192.168.59.103:9000...
```

Checkout the [commands](#Commands) section to see all available sandbox commands.

## Debugging application in ConductR sandbox

It is possible to debug your application inside of the ConductR sandbox:

1. Start ConductR sandbox in debug mode:

    ```scala
    sandbox debug
    ```
2. This exposes a debug port to Docker and the ConductR container. By default the debug port is set to `5005` which is the default port for remote debugging in IDEs such as IntelliJ. If you are happy with the default setting skip this step. Otherwise specify another port in the `build.sbt`:

    ```scala
    SandboxKeys.debugPort := 5432
    ```    
4. Load and run your bundle to the sandbox:

    ```scala
    conduct load <HIT THE TAB KEY AND THEN RETURN>
    conduct run my-app
    ```

You should be now able to remotely debug the bundle inside the ConductRs sandbox with your favorite IDE.    

## Features

> In order to use the following features you should ensure that the machine that runs Docker has enough memory, typically at least 2GB. VM configurations such as those provided via `docker-machine` and Oracle's VirtualBox can be configured like so:
> ```
> docker-machine stop default
> VBoxManage modifyvm default --memory 2048
> docker-machine start default
> ```

Features are specified with a `--with-features visualization logging` option convention. The following features are available:

Name          | ConductR ports | Docker ports | Description
--------------|----------------|--------------|------------
logging       | 9200, 5601     | 9200, 5601   | Consolidates the logging output of ConductR itself and the bundles that it executes. To view the consolidated log messsages run `conduct logs {bundle-name} or access the Kibana UI at http://{docker-host-ip}:5601.
monitoring    | N/A            | N/A          | Enables the Takipi based monitoring via Typesafe monitoring.
visualization | 9999           | 9909         | Provides a web interface to visualize the ConductR cluster together with deployed and running bundles.  After enabling the feature, access it at http://{docker-host-ip}:9909.

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
lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

BundleKeys.endpoints := Map("sample-app" -> Endpoint("http", services = Set(uri("http://:9000"))))
SandboxKeys.ports in Global := Set(1111)
SandboxKeys.debugPort := 5095
``` 

We specify that the web application should serve traffic on port 9000. Additionally we expose port 1111. The debug port gets only mapped if we start the ConductR sandbox cluster in debug mode with `sandbox debug`.

Now we create a ConductR cluster with 3 nodes:

```
sandbox run --nr-of-containers 3
```

These settings result in the following port mapping:

Docker container | ConductR port | Docker internal port | Docker public port
-----------------|---------------|----------------------|-------------------
cond-0           | 9000          | 9000                 | 9000
cond-1           | 9000          | 9000                 | 9010
cond-2           | 9000          | 9000                 | 9020
cond-0           | 1111          | 1111                 | 1111
cond-1           | 1111          | 1111                 | 1121
cond-2           | 1111          | 1111                 | 1131
cond-0           | 5095          | 5095                 | 5095
cond-1           | 5095          | 5095                 | 5005
cond-2           | 5095          | 5095                 | 5015

Each specified port is mapped to a unique public Docker port in order to allow multiple nodes within the sandbox cluster. By convention, the first node is using the ConductR port as the public Docker port, e.g. 1111 becomes 1111. The following nodes using a different public Docker port by increasing the second last digit of the ConductR port by the node number, e.g. the second node is mapping the port 1111 to 1121, the third is mapping the port 1111 to 1131. 
The web application becomes available on the Docker host IP address. The sandbox cluster is configured with a proxy and will automatically route requests to the correct instances that you have running in the cluster. Therefore any one of the addresses with the 9000, 9010 or 9020 ports will reach your application.

As a convenience, `sandbox run` and `sandbox debug` is reporting each of the above mappings along with the IP addresses:

```
[info] Starting ConductR nodes..
[info] Starting container cond-0 exposing 192.168.99.100:9000, 192.168.99.100:1111, 192.168.99.100:5095...
[info] f7f030f8b97cc375642f5a0321654594547b1e7d3a327c6f120715ba3199dda0
[info] Starting container cond-1 exposing 192.168.99.100:9010, 192.168.99.100:1121, 192.168.99.100:5005...
[info] 7f2caf1053810465589ac9710e0676a39d6828687c06020fa2d4a80ba815d80a
[info] Starting container cond-2 exposing 192.168.99.100:9020, 192.168.99.100:1131, 192.168.99.100:5015...
[info] 5644465b394617fb8d18bdb64ce488257776eea4437d7d1d205b481b775ab42a
```

## Roles

ConductR uses roles to load and scale bundles to the respective nodes. For more information about it please refer to [Managing services documentation](http://conductr.typesafe.com/docs/1.0.x/ManageServices#Roles).

To keep things simple `sbt-conductr-sandbox` ignores roles by default. Therefore, your bundles can be loaded and scaled to all ConductR nodes. 
To have more fine-grained control of the bundles it is also possible to provide a custom role configuration by specifying the `SandboxKeys.conductrRoles` setting: 

```scala
SandboxKeys.conductrRoles in Global := Seq(Set("muchMem", "myDB"))
```

This is only adding the roles `muchMem` and `myDB` to all ConductR nodes. The bundle roles are ignored in case `SandboxKeys.conductrRoles` is specified. Assigning different roles to nodes is also possible by specifying a set for each of the nodes:

```scala
SandboxKeys.conductrRoles in Global := Seq(Set("muchMem"), Set("myDB"))
```

In the above example the bundles with the role `muchMem` would only be loaded and scaled to the first node and bundles with the role `myDB` only to the second node.

If the `--nr-of-containers` startup option is larger than the size of the `conductrRoles` sequence then the specified roles are subsequently applied to the remaining nodes.

```scala
SandboxKeys.conductrRoles in Global := Seq(Set("muchMem", "frontend"), Set("myDB"))
```

```scala
sandbox run --nr-of-containers 4
```

This would load and scale all bundles with the roles `muchMem` and `frontend` to the first and third node and bundles with the role `myDB` to the second and fourth node.

## Functions

There is a `ConductRSandbox.runConductRs` function for starting the sandbox environment programmatically. This is useful for setting up test environments. Conversely there is a `ConductRSandbox.stopConductRs` function call can be made to close down the sandbox environment programmatically. The following listing shows how these functions can be used within an sbt integration test:

```scala
testOptions in IntegrationTest ++= Seq(
  Tests.Setup { () =>
    ConductRSandbox.stopConductRs(streams.value.log)
    ConductRSandbox.runConductRs(
      1,
      Seq.empty,
      Set.empty,
      Map.empty,
      (conductrImage in Global).value,
      (conductrImageVersion in Global).value,
      Set.empty,
      streams.value.log,
      "info")
  },

  Tests.Cleanup (() => ConductRSandbox.stopConductRs(streams.value.log))
)
```


## Settings

The following settings are provided under the `SandboxKeys` object:

Name              | Scope   | Description
------------------|---------|------------
envs              | Global  | A `Map[String, String]` of environment variables to be set for each ConductR container.
image             | Global  | The Docker image to use. By default `typesafe-docker-registry-for-subscribers-only.bintray.io/conductr/conductr-dev` is used i.e. the single node version of ConductR.
imageVersion      | Global  | The version of the Docker image to use. Must be set. Please visit the [ConductR Developer](http://www.typesafe.com/product/conductr/developer) page on Typesafe.com for the current version and additional information.
ports             | Global  | A `Seq[Int]` of ports to be made public by each of the ConductR containers. This will be complemented to the `endpoints` setting's service ports declared for `sbt-bundle`.
debugPort         | Project | Debug port to be made public to the ConductR containers if the sandbox gets started in [debug mode](#Commands). The debug ports of each sbt project setting will be used. If `sbt-bundle` is enabled the JVM argument `-jvm-debug $debugPort` is  additionally added to the `startCommand` of `sbt-bundle`. Default is 5005.
logLevel          | Global  | The log level of ConductR which can be one of "debug", "warning" or "info". By default this is set to "info". You can observe ConductR's logging via the `docker logs` command. For example `docker logs -f cond-0` will follow the logs of the first ConductR container.
conductrRoles     | Global  | Sets additional roles to the ConductR nodes. Defaults to `Seq.empty`. If this settings is not set the bundle roles specified with `BundleKeys.roles` are used. To provide a custom role configuration specify a set of roles for each node. Example: Seq(Set("megaIOPS"), Set("muchMem", "myDB")) would add the role `megaIOPS` to first ConductR node. `muchMem` and `myDB` would be added to the second node. If `nrOfContainers` is larger than the size of the `conductrRoles` sequence then the specified roles are subsequently applied to the remaining nodes.

## Commands

The following commands are provided:

Name          | Description
--------------|-------------
sandbox run   | Starts the ConductR sandbox cluster without remote debugging facilities.
sandbox debug | Starts the ConductR sandbox cluster and exposes the `debugPort` to docker and ConductR to enable remote debugging.
sandbox stop  | Stops the ConductR sandbox cluster.

&copy; Typesafe Inc., 2015
