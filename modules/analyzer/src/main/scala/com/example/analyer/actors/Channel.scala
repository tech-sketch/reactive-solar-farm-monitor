package com.example.analyer.actors

import java.net.InetSocketAddress

import akka.actor._
import com.example.Config
import com.example.farm.api._
import net.sigusr.mqtt.api._
import org.joda.time.DateTime

object Channel {

  case class Packet(measurement: Measurement, receiveDateTime: DateTime)

  sealed trait State
  case object NotOpen extends State
  case object Open extends State

  case class ChannelCannotOpenException() extends IllegalStateException

  case class ChannelDisabledException() extends IllegalStateException

  def props(measurementBuffer: ActorRef) = Props(new Channel(measurementBuffer))
}

import Channel._

class Channel(subscriber: ActorRef) extends Actor with Stash with LoggingFSM[Channel.State, Unit] with Config {

  val config = context.system.settings.config

  lazy val mqttManager = createMqttManager

  startWith(NotOpen, ())

  when(NotOpen) {

    case Event(Connected, _) =>
      log.info("Opened channel to {}:{}", mqttBrokerHostname, mqttBrokerPort)
      // 全パネルのデータを購読する
      val topics = s"$analyzerMqttTopicRoot/+"
      mqttManager ! Subscribe(Vector((topics, AtLeastOnce)), 1)
      goto(Open)

    case Event(ConnectionFailure(reason), _) =>
      log.error("Couldn't open channel: {}", reason)
      throw new ChannelCannotOpenException

    case Event(Disconnected, _) =>
      log.error("Opened channel to {}:{}", mqttBrokerHostname, mqttBrokerPort)
      throw new ChannelDisabledException

    case Event(Error(state), _) =>
      log.error("Error occurred: {}", state)
      throw new ChannelDisabledException

    case Event(msg, _) =>
      stash()
      log.debug("Message stashed: {}", msg)
      stay()
  }

  when(Open) {

    case Event(s: Subscribed, _) =>
      stay()

    case Event(Message(topic, payload), _) =>
      subscriber ! Packet(decode(payload), DateTime.now())
      stay()

    case Event(Disconnected, _) =>
      log.error("Closed channel to {}:{}", mqttBrokerHostname, mqttBrokerPort)
      throw new ChannelDisabledException

    case Event(Error(state), _) =>
      log.error("Error occurred: {}", state)
      throw new ChannelDisabledException
  }

  onTransition {
    case NotOpen -> Open =>
      unstashAll()
  }

  override def preStart() = {
    mqttManager ! Connect(analyzerMqttClientId + this.hashCode, user = mqttBrokerUser, password = mqttBrokerPassword)
    log.info("Trying to open a channel to {}:{}", mqttBrokerHostname, mqttBrokerPort)
  }

  def createMqttManager =
    context.actorOf(Manager.props(new InetSocketAddress(mqttBrokerHostname, mqttBrokerPort)), "mqtt-manager")

  initialize()
}