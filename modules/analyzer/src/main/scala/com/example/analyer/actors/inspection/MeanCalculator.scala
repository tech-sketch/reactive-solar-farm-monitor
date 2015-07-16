package com.example.analyer.actors.inspection

import akka.actor._
import akka.routing.Broadcast
import com.example.analyer.actors.inspection.InspectionManager.Execute
import com.example.analyer.actors.inspection.SumCalculator.PartialSum

object MeanCalculator {

  case class IllegalPopulationException(msg: String = "") extends IllegalStateException(msg)

  case class Mean(mean: BigDecimal, population: Int)
  case class EmptyMean(population: Int)

  sealed trait State
  case object Pending extends State
  case object Preparing extends State

  sealed trait Data
  case class Empty() extends Data
  case class ExecuteContext(population: Int) extends Data
  case class SumCalculation(sum: BigDecimal, preparedPopulation:Int, totalPopulation: Int) extends Data

  def props(inspector: ActorRef) = Props(new MeanCalculator(inspector))
}

import MeanCalculator._

class MeanCalculator(inspector: ActorRef) extends LoggingFSM[State, Data] with Stash {

  startWith(Pending, Empty())

  when(Pending) {

    case Event(Execute(population), _) =>
      goto(Preparing) using SumCalculation(BigDecimal(0), 0, population)

    case Event(_: PartialSum, _) =>
      stash()
      stay()
  }

  when(Preparing) {

    case Event(p: PartialSum, c: SumCalculation) =>
      val population = c.preparedPopulation + p.population
      val sum = c.sum + p.sum

      if (population == c.totalPopulation) {
        if (population > 0) {
          val mean = sum / BigDecimal(population)
          inspector ! Mean(mean, population)
        } else {
          inspector ! EmptyMean(population)
        }
        goto(Pending) using Empty()
      } else if (population < c.totalPopulation) {
        stay() using c.copy(sum, preparedPopulation = population)
      } else {
        throw new IllegalPopulationException(s"Total population: ${c.totalPopulation}, Collected population: ${population}")
      }
  }

  onTransition {
    case Pending -> Preparing =>
      unstashAll()
  }

  initialize()
}
