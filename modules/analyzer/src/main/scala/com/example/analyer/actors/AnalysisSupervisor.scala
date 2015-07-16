package com.example.analyer.actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import akka.event.LoggingReceive
import akka.routing.FromConfig
import com.example.analyer.actors.inspection.InspectionSupervisor

import scala.concurrent.duration._
import com.example.analysis

class AnalysisSupervisor extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minute) {
      case _ => Restart
    }

  def receive = LoggingReceive {
    case analysis.api.Connect =>
      log.info("Connected from {}", sender)
      val receiver = sender
      val buffer = createBuffer(receiver)
      createChannel(buffer)
      createInspectionSupervisor(receiver)
  }

  def createInspectionSupervisor(receiver: ActorRef) =
    context.actorOf(InspectionSupervisor.props(receiver), "inspection-supervisor")

  def createChannel(buffer: ActorRef) =
    context.actorOf(Channel.props(buffer), "buffer-channel")

  def createBuffer(receiver: ActorRef) =
    context.actorOf(Buffer.props(receiver), "buffer")
}
