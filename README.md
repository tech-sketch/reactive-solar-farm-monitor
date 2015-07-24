Reactive Solar Farm Monitor
===========================

(English/[Japanese](README.ja.md))

Reactive Solar Farm Monitor is a sample application which is implemented on [Typesafe Reactive Platform](http://www.typesafe.com/products/typesafe-reactive-platform).

What is Reactive Systems?
--------------------------
Reactive Systems satisfy all of the following requirements:
* Keep the system response quick and provide high usability
* Uptime close to 100% as possible
* Scale-out and Scale-in are easy when workload fluctuates

Please refer to [The Reactive Manifesto](http://www.reactivemanifesto.org/) for details.

What is Typesafe Reactive Platform?
---------------------------------

Typesafe Reactive Platform is a Platform for Building Message-Driven, Elastic, Resilient
and Responsive Applications Leveraging the Java Virtual Machine.

To help developers build Reactive applications on the JVM,
Typesafe has brought together the [Play Framework](https://playframework.com/), a runtime
called [Akka](http://akka.io/), and the [Scala](http://www.scala-lang.org/) language under a unified platform.

Please refer to [Typesafe Reactive Platform](http://www.typesafe.com/products/typesafe-reactive-platform) for details.

What is Reactive Solar Farm Monitor?
----
This sample application is a failure detection system of solar panels in the "Solar Farm" (photovoltaic power plant).

It is assumed that Solar farm has tens of thousands of solar panels, and each panel has the measuring device which successively measures and send the amount of power generation. This application calculates the mean value of amounts of power generation of all solar panels, and compares the each amount of power generation with the mean value. If the value has fallen below the mean significantly, the application regards the solar panel as failure.


![abstract](img/reactive-solar-farm-monitor_abstract.png)

Also, the system has the following requirements.

* I want to detect failures of solar panels with in 1 second after failure, to improve generation efficiency.
* I want to achieve 100% uptime to no time lag after detect failures.
* I can scale-out the system, if solar panels increase

Architecture
--------------
This sample application uses [Typesafe Reactive Platform](http://www.typesafe.com/products/typesafe-reactive-platform), adopts Message-driven architecture.

![architecture](img/reactive-solar-farm-monitor_architecture.png)

Screenshot
------------------

![screenshot](img/reactive-solar-farm-monitor_screenshot.png)

Get Started
---------

### Use Docker

Execute the following commands on the PC which has already been installed [Docker](https://www.docker.com/).

~~~
docker run -d --name=broker   -p 61613:61613                        crowbary/apache-apollo
docker run -d --name=solar_farm_simulator  --link=broker:broker     crowbary/reactive-solar-farm-monitor-solar-farm-simulator
docker run -d --name=analyzer -p 2551:2551 --link=broker:broker     crowbary/reactive-solar-farm-monitor-analyzer
docker run -d --name=monitor  -p 9000:9000 --link=analyzer:analyzer crowbary/reactive-solar-farm-monitor
~~~

Access to http://[DOCKER_HOST]:9000/ from Web browser

* DOCKER_HOST: The IP address of a host on which you executed "docker run" commands.

### Use Typesafe Activator

How to install via [Typesafe Activator](https://www.typesafe.com/get-started) is currently being prepared.

# Contact

Please send feedback to us.

[TIS Inc.](http://www.tis.com/)
System Development Technology R&D Office
Reactive Systems consulting team

* <go-reactive@tis.co.jp>.

TIS provides a consulting service about Typesafe Reactive Platform. Please refer to the [our site](http://www.tis.jp/service_solution/goreactive/)(Japanese site) for details.

# License

This application is released under the Apache License version2.0.
The Apache License version2.0 official full text is published at this [link](http://www.apache.org/licenses/LICENSE-2.0.html).

---------

* All company names and product mentioned are trademarks or registered of the respective companies.
※ Icon made by [Freepik](http://www.freepik.com) from [www.flaticon.com](http://www.flaticon.com) is licensed under [CC BY 3.0](http://creativecommons.org/licenses/by/3.0/)

Copyright © 2015 TIS Inc.
