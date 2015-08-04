package com.example.analyer.actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import akka.contrib.pattern.ClusterReceptionistExtension
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

  def receive = Actor.emptyBehavior

  def createInspectionSupervisor() =
    context.actorOf(Props[InspectionSupervisor], "inspection-supervisor")

  def createChannel() =
    context.actorOf(Channel.props(createBuffer()), "buffer-channel")

  def createBuffer() = {
    val buffer = context.actorOf(Props[Buffer], "buffer")
    ClusterReceptionistExtension(context.system).registerService(buffer)
    buffer
  }

  override def preStart() = {
    createChannel()
    createInspectionSupervisor()
  }
}
