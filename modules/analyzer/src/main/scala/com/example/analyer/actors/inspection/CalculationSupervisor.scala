package com.example.analyer.actors.inspection

import akka.actor.SupervisorStrategy.Restart
import akka.actor._

import scala.concurrent.duration._

class CalculationSupervisor extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minute) {
      case _ => Restart
    }

  def receive = Actor.emptyBehavior

  def createSumCalculator() =
    context.actorOf(SumCalculator.props(createMeanCalculator()), "sum-calculator")

  def createMeanCalculator() =
    context.actorOf(MeanCalculator.props(createInspector()), "mean-calculator")

  def createInspector() =
    context.actorOf(Props[Inspector], "inspector")

  override def preStart() = {
    createSumCalculator()
  }

}
