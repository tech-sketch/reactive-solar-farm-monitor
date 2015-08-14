package com.example.analyzer.actors.inspection

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import com.example.analyzer.actors.Channel

import scala.concurrent.duration._

class InspectionSupervisor extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minute) {
      case _ => Restart
    }

  def receive = Actor.emptyBehavior

  def createCalculationSupervisor() =
    context.actorOf(Props[CalculationSupervisor], "calculation-supervisor")

  def createInspectionChannel() =
    context.actorOf(Channel.props(createInspectionManager()), "inspection-channel")

  def createInspectionManager() = {
    val inspectionManager = context.actorOf(Props[InspectionManager], "inspection-manager")
    inspectionManager
  }

  override def preStart() = {
    createInspectionChannel()
    createCalculationSupervisor()
  }
}
