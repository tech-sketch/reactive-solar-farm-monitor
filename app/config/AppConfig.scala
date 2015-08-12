package config

import scala.concurrent.duration._

trait AppConfig {

  val config: com.typesafe.config.Config

  lazy val inspectorEntryActorPath = config.getString("solar-farm-monitor.analyzer.inspector-actor-path")

  lazy val bufferEntryActorPath = config.getString("solar-farm-monitor.analyzer.buffer-actor-path")

  lazy val inspectionRequestInterval = config.getDuration("solar-farm-monitor.analyzer.inspection-interval", MILLISECONDS)

  lazy val inspectionResponseTimeoutDuration = config.getDuration("solar-farm-monitor.analyzer.inspection-response-timeout", MILLISECONDS)

  lazy val measurementRequestInterval = config.getDuration("solar-farm-monitor.analyzer.measurement-interval", MILLISECONDS)

  lazy val measurementResponseTimeoutDuration = config.getDuration("solar-farm-monitor.analyzer.measurement-response-timeout", MILLISECONDS)

  lazy val connectionAttemptInterval = config.getDuration("solar-farm-monitor.analyzer.connection-attempt-interval", MILLISECONDS)

  lazy val errorNotificationInterval = config.getDuration("solar-farm-monitor.analyzer.error-notification-interval", MILLISECONDS)

  lazy val unstableTimeoutDuration = config.getDuration("solar-farm-monitor.analyzer.unstable-timeout", MILLISECONDS)

  lazy val initialConnectionAttemptTimeout = config.getDuration("solar-farm-monitor.analyzer.initial-connection-attempt-timeout", MILLISECONDS)

  lazy val contactPoints = config.getStringList("solar-farm-monitor.analyzer.contact-points")
}
