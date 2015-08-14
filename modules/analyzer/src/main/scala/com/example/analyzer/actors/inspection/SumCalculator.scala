package com.example.analyzer.actors.inspection

import akka.actor._
import com.example.analyzer.actors.inspection.InspectionManager.{AbortInspection, Execute}

import scala.math.BigDecimal.RoundingMode

object  SumCalculator {

  sealed trait State
  case object Collecting extends  State

  sealed trait Data
  case class SumCalculation(sum: BigDecimal, population: Int) extends Data
  val emptySum = SumCalculation(BigDecimal(0), 0)

  case class PartialSum(sum: BigDecimal, population: Int)

  def props(lowerLimitCalculator: ActorRef) = Props(new SumCalculator(lowerLimitCalculator))
}

import SumCalculator._

class SumCalculator(lowerLimitCalculator: ActorRef) extends LoggingFSM[State, Data] with Stash {

  startWith(Collecting, emptySum)

  when(Collecting) {

    case Event(InspectionManager.Sample(measurement), c: SumCalculation) =>
        stay() using SumCalculation(c.sum + measurement.measuredValue, c.population + 1)

    case Event(_: Execute, SumCalculation(sum, population)) =>
      if (population > 0) {
        log.debug("PartialSum: {}, population {}", sum.setScale(2, RoundingMode.HALF_DOWN), population)
      }
      lowerLimitCalculator !  PartialSum(sum, population)
      stay() using emptySum
  }

  whenUnhandled {

    case Event(AbortInspection, _) =>
      goto(Collecting) using emptySum
  }

  initialize()
}
