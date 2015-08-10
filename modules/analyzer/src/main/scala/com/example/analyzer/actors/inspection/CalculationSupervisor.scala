package com.example.analyzer.actors.inspection

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import akka.routing.FromConfig

import scala.concurrent.duration._

class CalculationSupervisor extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minute) {
      case _ => Restart
    }

  def receive = Actor.emptyBehavior

  def createSumCalculator() =
    context.actorOf(FromConfig.props(SumCalculator.props(createLowerLimitCalculator())), "sum-calculator-router")

  def createLowerLimitCalculator() =
    context.actorOf(LowerLimitCalculator.props(createInspector()), "lower-limit-calculator")

  def createInspector() =
    context.actorOf(FromConfig.props(Props[Inspector]), "inspector-router")

  override def preStart() = {
    createSumCalculator()
  }

}
