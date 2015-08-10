package com.example.analyzer.actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import com.example.analyzer.actors.AnalysisSupervisor.Destroy
import com.example.analyzer.actors.inspection.{InspectionManager, InspectionSupervisor}
import com.example.analysis

import scala.concurrent.duration._

object AnalysisSupervisor {

  case class Destroy()
}

class AnalysisSupervisor extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minute) {
      case _ => Restart
    }

  val inspectionManager = context.actorSelection("inspection-supervisor/inspection-manager")

  val buffer = context.actorSelection("buffer")

  def receive = {
    case analysis.api.InspectionRequest =>
      inspectionManager forward analysis.api.InspectionRequest
    case analysis.api.MeasurementRequest =>
      buffer forward analysis.api.MeasurementRequest
    case Destroy =>
      inspectionManager ! InspectionManager.AbortInspection
  }

  def createInspectionSupervisor() =
    context.actorOf(Props[InspectionSupervisor], "inspection-supervisor")

  def createChannel() =
    context.actorOf(Channel.props(createBuffer()), "buffer-channel")

  def createBuffer() =
    context.actorOf(Props[Buffer], "buffer")

  override def preStart() = {
    createChannel()
    createInspectionSupervisor()
  }
}
