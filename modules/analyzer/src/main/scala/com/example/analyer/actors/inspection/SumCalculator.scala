package com.example.analyer.actors.inspection

import akka.actor._
import com.example.analyer.actors.inspection.InspectionManager.Execute
object  SumCalculator {

  sealed trait State
  case object Collecting extends  State

  sealed trait Data
  case class SumCalculation(sum: BigDecimal, population: Int) extends Data
  val emptySum = SumCalculation(BigDecimal(0), 0)

  case class PartialSum(sum: BigDecimal, population: Int)

  def props(meanCalculator: ActorRef) = Props(new SumCalculator(meanCalculator))
}

import SumCalculator._

class SumCalculator(meanCalculator: ActorRef) extends LoggingFSM[State, Data] with Stash {

  startWith(Collecting, emptySum)

  when(Collecting) {

    case Event(InspectionManager.Sample(measurement), c: SumCalculation) =>
        stay() using SumCalculation(c.sum + measurement.measuredValue, c.population + 1)

    case Event(Execute(_), SumCalculation(sum, population)) =>
      meanCalculator !  PartialSum(sum, population)
      stay() using emptySum
  }

  initialize()
}
