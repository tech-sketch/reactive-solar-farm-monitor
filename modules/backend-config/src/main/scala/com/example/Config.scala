package com.example

import scala.concurrent.duration._

trait Config {

  val config: com.typesafe.config.Config

  // Akka

  lazy val akkaRemoteHostname = optionConfig("akka.remote.netty.tcp.hostname", _.getString)

  lazy val akkaRemotePort = optionConfig("akka.remote.netty.tcp.port", _.getInt)

  // MQTT

  lazy val mqttBrokerHostname = config.getString("mqtt.broker.hostname")

  lazy val mqttBrokerPort = config.getInt("mqtt.broker.port")

  lazy val mqttBrokerUser = optionConfig("mqtt.broker.user", _.getString)

  lazy val mqttBrokerPassword = optionConfig("mqtt.broker.password", _.getString)

  // Solar Farm Simulator

  lazy val simulatorNrOfPanels = config.getInt("solar-farm-simulator.nr-of-panels")

  lazy val simulatorMeasureInterval = config.getDuration("solar-farm-simulator.panel.measure-interval", MILLISECONDS)

  lazy val simulatorMeasureInitialDelay = config.getDuration("solar-farm-simulator.panel.measure-initial-delay", MILLISECONDS)

  lazy val simulatorBaseMeasuredValue = config.getDouble("solar-farm-simulator.panel.base-measured-value")

  lazy val simulatorMeasuredValueAmplitude = config.getDouble("solar-farm-simulator.panel.measured-value-amplitude")

  lazy val simulatorAttenuationFactor = config.getDouble("solar-farm-simulator.panel.trouble.attenuation-factor")

  lazy val simulatorRepairDelay = config.getDuration("solar-farm-simulator.panel.trouble.repair-delay", MILLISECONDS)

  lazy val simulatorBreakInitialDelay = config.getDuration("solar-farm-simulator.breaker.break-initial-delay", MILLISECONDS)

  lazy val simulatorBreakInterval = config.getDuration("solar-farm-simulator.breaker.break-interval", MILLISECONDS)

  lazy val simulatorMqttClientId = config.getString("solar-farm-simulator.mqtt.client.id-prefix")

  lazy val simulatorMqttTopicRoot = config.getString("solar-farm-simulator.mqtt.topic.root")

  // Analyzer

  lazy val analyzerClusterName = config.getString("solar-farm-analyzer.cluster.name")

  lazy val monitorClusterRole = config.getString("solar-farm-analyzer.cluster.monitor-role")

  lazy val inspectionInterval = config.getDuration("solar-farm-analyzer.inspection.interval", MILLISECONDS)

  lazy val alertThresholdPer = config.getInt("solar-farm-analyzer.inspection.alert-threshold-per")

  lazy val inspectingTimeoutDuration = config.getDuration("solar-farm-analyzer.inspection.timeout", MILLISECONDS)

  lazy val snapshotInterval = config.getDuration("solar-farm-analyzer.buffer.snapshot-interval", MILLISECONDS)

  lazy val ghostCollectionInitialDelay = config.getDuration("solar-farm-analyzer.buffer.ghost-collection-initial-delay", MILLISECONDS)

  lazy val ghostCollectionInterval = config.getDuration("solar-farm-analyzer.buffer.ghost-collection-interval", MILLISECONDS)

  lazy val ghostLifeSpan = config.getDuration("solar-farm-analyzer.buffer.ghost-life-span", MILLISECONDS)

  lazy val analyzerMqttClientId = config.getString("solar-farm-analyzer.mqtt.client.id-prefix")

  lazy val analyzerMqttTopicRoot = config.getString("solar-farm-simulator.mqtt.topic.root")

  def optionConfig[T](path: String, by: com.typesafe.config.Config => String => T): Option[T] =
    if (config.hasPath(path)) Some(by(config)(path)) else None
}
