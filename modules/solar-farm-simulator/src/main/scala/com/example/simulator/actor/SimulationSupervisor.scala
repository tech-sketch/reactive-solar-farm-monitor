package com.example.simulator.actor

import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.routing.FromConfig

import scala.concurrent.duration._


class SimulationSupervisor extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minute) {
      case _ => Restart
    }

  def receive = Actor.emptyBehavior

  def createPanelBreaker =
    context.actorOf(SolarPanelBreaker.props(createPanelRouter))

  def createPanelRouter =
    context.actorOf(FromConfig.props(SolarPanel.props(createMeasurementChannel)), "panel-router")

  def createMeasurementChannel =
    context.actorOf(FromConfig.props(Props[MeasurementChannelUpstream]), "measurement-channel")

  override def preStart() = {
    createPanelBreaker
  }
}
