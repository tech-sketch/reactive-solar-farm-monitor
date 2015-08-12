package com.example.analyer.actors.inspection

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import akka.contrib.pattern.{ClusterReceptionistExtension, ClusterReceptionist}
import com.example.analyer.actors.Channel

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
    ClusterReceptionistExtension(context.system).registerService(inspectionManager)
    inspectionManager
  }

  override def preStart() = {
    createInspectionChannel()
    createCalculationSupervisor()
  }
}
