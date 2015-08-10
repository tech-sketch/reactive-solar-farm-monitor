package com.example.analyzer.actors.inspection

import akka.actor._
import akka.routing.Broadcast
import akka.routing.ConsistentHashingRouter.ConsistentHashable
import com.example.{analysis, Config}
import com.example.analyzer.actors.inspection.InspectionManager.{AbortInspection, Execute}
import com.example.analyzer.actors.inspection.SumCalculator.PartialSum
import org.joda.time.DateTime

import scala.math.BigDecimal.RoundingMode

object LowerLimitCalculator {

  case class IllegalPopulationException(msg: String = "") extends IllegalStateException(msg)

  case class LowerLimit(value: BigDecimal, population: Int)
  case class EmptyLowerLimit(population: Int)

  sealed trait State
  case object Pending extends State
  case object Preparing extends State

  sealed trait Data
  case class Empty() extends Data
  case class ExecuteContext(population: Int) extends Data
  case class LowerLimitCalculation(sum: BigDecimal, preparedPopulation:Int, totalPopulation: Int, receiver: ActorRef) extends Data

  def props(inspectorRouter: ActorRef) = Props(new LowerLimitCalculator(inspectorRouter))
}

import LowerLimitCalculator._

class LowerLimitCalculator(inspectorRouter: ActorRef) extends LoggingFSM[State, Data] with Stash with Config {

  val config = context.system.settings.config

  startWith(Pending, Empty())

  when(Pending) {

    case Event(Execute(population, receiver), _) =>
      goto(Preparing) using LowerLimitCalculation(BigDecimal(0), 0, population, receiver)

    case Event(_: PartialSum, _) =>
      stash()
      stay()
  }

  when(Preparing) {

    case Event(p: PartialSum, c: LowerLimitCalculation) =>
      val population = c.preparedPopulation + p.population
      val sum = c.sum + p.sum

      if (population == c.totalPopulation) {
        if (population > 0) {
          val mean = sum / BigDecimal(population)
          val lowerLimit = mean * alertThresholdPer / 100
          inspectorRouter ! Broadcast(LowerLimit(lowerLimit, population))

          log.debug("LowerLimit: {}, population {}", lowerLimit.setScale(2, RoundingMode.HALF_DOWN), population)
          c.receiver ! analysis.api.LowerLimit(lowerLimit, DateTime.now())
        } else {
          inspectorRouter ! Broadcast(EmptyLowerLimit(population))
        }
        goto(Pending) using Empty()
      } else if (population < c.totalPopulation) {
        stay() using c.copy(sum, preparedPopulation = population)
      } else {
        throw new IllegalPopulationException(s"Total population: ${c.totalPopulation}, Collected population: ${population}")
      }
  }

  whenUnhandled {
    case Event(AbortInspection, _) =>
      goto(Pending) using Empty()
  }

  onTransition {
    case Pending -> Preparing =>
      unstashAll()
  }

  initialize()
}
