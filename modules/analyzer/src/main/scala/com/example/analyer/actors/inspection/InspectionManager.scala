package com.example.analyer.actors.inspection

import akka.actor._
import akka.event.LoggingReceive
import akka.routing.Broadcast
import com.example.Config
import com.example.analyer.actors.Channel.Packet
import com.example.analysis
import com.example.farm.api.Measurement
import scala.concurrent.duration._

object InspectionManager {

  sealed trait State
  case object Collecting extends State
  case object Inspecting extends State

  sealed trait Data
  case class Collection(population: Int) extends Data
  case class Inspection(progressPopulation: Int, totalPopulation: Int, receiver: ActorRef) extends Data

  case class Sample(measurement: Measurement)

  case class Execute(population: Int, receiver: ActorRef)
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

    case Event(analysis.api.InspectionRequest, Collection(totalPopulation)) =>
      sumCalculator  ! Execute(totalPopulation, receiver = sender)
      meanCalculator ! Execute(totalPopulation, receiver = sender)
      inspector      ! Execute(totalPopulation, receiver = sender)
      goto(Inspecting) using Inspection(0, totalPopulation, sender)
  }

  when(Inspecting) {

    case Event(Inspector.Done(population), inspection @ Inspection(progressPopulation, totalPopulation, receiver)) =>
      val progress = progressPopulation + population
      if (progress == totalPopulation) {
        receiver ! analysis.api.DoneInspection
        goto(Collecting) using Collection(0)
      } else {
        stay() using inspection.copy(progressPopulation = progress)
      }

    case Event(analysis.api.InspectionRequest, _) =>
      sender ! analysis.api.DoneInspection
      stay()

    case Event(Packet(measurement, _), _) =>
      stash()
      stay()
  }

  initialize()
}
