package com.example.simulator.actor

import java.net.InetSocketAddress

import akka.actor._
import com.example.Config
import net.sigusr.mqtt.api._

object MeasurementChannelUpstream {

  sealed trait State
  case object NotOpen extends State
  case object Open extends State

  sealed trait Data

  case class ChannelCannotOpenException() extends IllegalStateException

  case class ChannelDisabledException() extends IllegalStateException
}

import MeasurementChannelUpstream._

class MeasurementChannelUpstream extends Actor with Stash with LoggingFSM[State, Unit] with Config {
  import com.example.farm.api._

  val config = context.system.settings.config

  lazy val mqttManager = createMqttManager

  startWith(NotOpen, ())

  when(NotOpen) {

    case Event(Connected, ()) =>
      log.info("Opened channel to {}:{}", mqttBrokerHostname, mqttBrokerPort)
      goto(Open)

    case Event(ConnectionFailure(reason), ()) =>
      log.error("Couldn't open channel: {}", reason)
      throw new ChannelCannotOpenException

    case Event(Disconnected, ()) =>
      log.error("Closed channel to {}:{}", mqttBrokerHostname, mqttBrokerPort)
      throw new ChannelDisabledException

    case Event(Error(state), ()) =>
      log.error("Error occurred: {}", state)
      throw new ChannelDisabledException

    case Event(msg, ()) =>
      stash()
      log.debug("Message stashed: {}", msg)
      stay()
  }

  when(Open) {

    case Event(measured: Measurement, ()) =>
      val topic = simulatorMqttTopicRoot + "/" + measured.panelId

      mqttManager ! Publish(topic , encode(measured), retain = true)
      log.debug("Pushing to {}: {}", topic, measured)
      stay()

    case Event(Disconnected, ()) =>
      log.error("Closed channel to {}:{}", mqttBrokerHostname, mqttBrokerPort)
      throw new ChannelDisabledException

    case Event(Error(state), ()) =>
      log.error("Error occurred: {}", state)
      throw new ChannelDisabledException
  }

  onTransition {
    case NotOpen -> Open =>
      unstashAll()
  }

  override def preStart() = {
    mqttManager ! Connect(simulatorMqttClientId + self.path.name, user = mqttBrokerUser, password = mqttBrokerPassword)
    log.info("Trying to open a channel to {}:{}", mqttBrokerHostname, mqttBrokerPort)
  }

  def createMqttManager =
    context.actorOf(Manager.props(new InetSocketAddress(mqttBrokerHostname, mqttBrokerPort)), "mqtt-manager")

  initialize()
}