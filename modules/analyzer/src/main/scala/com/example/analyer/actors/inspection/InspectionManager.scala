package com.example.analyer.actors.inspection

import akka.actor._
import akka.event.LoggingReceive
import akka.routing.Broadcast
import com.example.Config
import com.example.analyer.actors.Channel.Packet
import com.example.farm.api.Measurement
import scala.concurrent.duration._

object InspectionManager {

  sealed trait State
  case object Collecting extends State
  case object Inspecting extends State

  sealed trait Data
  case class Collection(population: Int) extends Data
  case class Inspection(progressPopulation: Int, totalPopulation: Int) extends Data

  case class Sample(measurement: Measurement)

  case class Inspect()
  case class Execute(population: Int)
}

import InspectionManager._

class InspectionManager extends Actor with LoggingFSM[State, Data] with Stash with Config {
  import InspectionManager._

  import scala.concurrent.ExecutionContext.Implicits.global

  val config = context.system.settings.config

  val sumCalculator = context.actorSelection("/user/analysis-supervisor/inspection-supervisor/calculation-supervisor/sum-calculator")

  val meanCalculator = context.actorSelection("/user/analysis-supervisor/inspection-supervisor/calculation-supervisor/mean-calculator")

  val inspector = context.actorSelection("/user/analysis-supervisor/inspection-supervisor/calculation-supervisor/inspector")

  startWith(Collecting, Collection(0))

  when(Collecting) {

    case Event(Packet(measurement, _), c: Collection) =>
      sumCalculator ! Sample(measurement)
      inspector     ! Sample(measurement)
      stay() using c.copy(population = c.population + 1)

    case Event(Inspect, Collection(totalPopulation)) =>
      sumCalculator  ! Execute(totalPopulation)
      meanCalculator ! Execute(totalPopulation)
      inspector      ! Execute(totalPopulation)
      goto(Inspecting) using Inspection(0, totalPopulation)
  }

  when(Inspecting) {

    case Event(Inspector.Done(population), inspection @ Inspection(progressPopulation, totalPopulation)) =>
      val progress = progressPopulation + population
      if (progress == totalPopulation) {
        goto(Collecting) using Collection(0)
      } else {
        stay() using inspection.copy(progressPopulation = progress)
      }

    case Event(Packet(measurement, _), _) =>
      stash()
      stay()
  }

  var inspectionSchedule =
    context.system.scheduler.scheduleOnce(inspectionInitialDelay milliseconds, self, Inspect)

  onTransition {

    case Inspecting -> Collecting =>
      inspectionSchedule =
        context.system.scheduler.scheduleOnce(inspectionInterval milliseconds, self, Inspect)
      unstashAll()
  }

  override def postStop() = {
    inspectionSchedule.cancel()
  }

  initialize()
}
