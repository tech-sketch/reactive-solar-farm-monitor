package com.example.analyer.actors.inspection

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import com.example.analyer.actors.Channel

import scala.concurrent.duration._

object InspectionSupervisor {
  def props(receiver: ActorRef) = Props(new InspectionSupervisor(receiver))
}

class InspectionSupervisor(receiver: ActorRef) extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minute) {
      case _ => Restart
    }

  def receive = Actor.emptyBehavior

  def createCalculationSupervisor =
    context.actorOf(CalculationSupervisor.props(receiver), "calculation-supervisor")

  def createInspectionChannel =
    context.actorOf(Channel.props(createInspectionManager), "inspection-channel")

  def createInspectionManager =
    context.actorOf(Props[InspectionManager], "inspection-manager")

  override def preStart() = {
    createInspectionChannel
    createCalculationSupervisor
  }
}
