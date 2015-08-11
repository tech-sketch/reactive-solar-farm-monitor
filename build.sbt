import sbt.Keys._

val appName = """reactive-solar-farm-monitor"""

lazy val commonSettings = Seq(
  version := "0.2.0",
  scalaVersion := "2.11.7",
  // Specs2 dependes on bintray repository of Scalaz.
  resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
)

lazy val dockerCommonSettings = Seq(
  maintainer := "TIS Inc.",
  dockerBaseImage := "java:openjdk-8u45-jdk"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(dockerCommonSettings: _*).
  settings(
    name := appName,
    libraryDependencies ++= Seq(
        jdbc,
        cache,
        ws,
        specs2 % Test
    ),
    // Play provides two styles of routers, one expects its actions to be injected, the
    // other, legacy style, accesses its actions statically.
    routesGenerator := InjectedRoutesGenerator,
    dockerExposedPorts := Seq(2551, 9000),
    dockerEntrypoint := Seq("/bin/sh", "-c",
      "HOST_IP=`ip addr show scope global | grep 'inet' | grep -Eo '[0-9]+\\\\.[0-9]+\\\\.[0-9]+\\\\.[0-9]+'`"
        + s" bin/${name.value}" + " -Dconfig.resource=application.docker.conf $*"),
    dockerRepository := Some("crowbary"),
    dockerUpdateLatest := true
  ).
  enablePlugins(PlayScala).
  dependsOn(analysisApi, testkitAkka)

lazy val backendConfig = (project in file("modules/backend-config")).
  settings(commonSettings: _*).
  settings(
    name := appName + "-config",
    libraryDependencies += "com.typesafe" % "config" % "1.3.0"
  )

lazy val analysisApi = (project in file("modules/analysis-api")).
  settings(commonSettings: _*).
  settings(
    name := appName + "-analysis-api",
    libraryDependencies += json
  )

lazy val analyzer = (project in file("modules/analyzer")).
  enablePlugins(JavaAppPackaging).
  settings(commonSettings: _*).
  settings(dockerCommonSettings: _*).
  settings(
    name := appName + "-analyzer",
    libraryDependencies ++= Seq(
      specs2 % Test
    ) ++ akkaDependencies ++ mqttDependencies,
    mainClass := Some("com.example.analyer.Analyzer"),
    fullRunInputTask(run, Compile, "com.example.analyer.Analyzer"),
    dockerExposedPorts := Seq(2551),
    dockerEntrypoint := Seq("/bin/sh", "-c",
      "HOST_IP=`ip addr show scope global | grep 'inet' | grep -Eo '[0-9]+\\\\.[0-9]+\\\\.[0-9]+\\\\.[0-9]+'`"
        + s" bin/${name.value}" + " -Dconfig.resource=application.docker.conf $*"),
    dockerRepository := Some("crowbary"),
    dockerUpdateLatest := true
  ).
  dependsOn(backendConfig, analysisApi, solarFarmApi, testkitAkka % Test)

lazy val solarFarmApi = (project in file("modules/solar-farm-api")).
  settings(commonSettings: _*).
  settings(
    name := appName + "-solar-farm-api",
    libraryDependencies += json
  )

lazy val solarFarmSimulator = (project in file("modules/solar-farm-simulator")).
  enablePlugins(JavaAppPackaging).
  settings(commonSettings: _*).
  settings(dockerCommonSettings: _*).
  settings(
    name := appName + "-solar-farm-simulator",
    libraryDependencies ++= Seq(
      specs2 % Test
    ) ++ akkaDependencies ++ mqttDependencies,
    mainClass := Some("com.example.simulator.SolarFarmSimulator"),
    fullRunInputTask(run, Compile, "com.example.simulator.SolarFarmSimulator"),
    dockerEntrypoint := Seq("sh", "-c",
      s"bin/${name.value}" + " -Dconfig.resource=application.docker.conf $*"),
    dockerRepository := Some("crowbary"),
    dockerUpdateLatest := true
  ).
  dependsOn(backendConfig, solarFarmApi, testkitAkka % Test)

lazy val testkitAkka = (project in file("modules/testkit-akka")).
  settings(commonSettings: _*).
  settings(
    name := appName + "-testkit-akka",
    libraryDependencies ++= Seq(
      specs2,
      "com.typesafe.akka" %% "akka-testkit" % "2.3.11"
    ) ++ akkaDependencies
  )

lazy val akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.11"
)

lazy val mqttDependencies = Seq(
  "net.sigusr" %% "scala-mqtt-client" % "0.6.0",
  json
)
