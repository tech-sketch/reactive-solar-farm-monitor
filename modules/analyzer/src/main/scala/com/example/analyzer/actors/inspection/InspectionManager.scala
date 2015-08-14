package com.example.analyzer.actors.inspection

import akka.actor._
import akka.event.LoggingReceive
import akka.routing.Broadcast
import akka.routing.ConsistentHashingRouter.ConsistentHashable
import com.example.Config
import com.example.analyzer.actors.Channel.Packet
import com.example.analysis
import com.example.farm.api.Measurement
import org.joda.time.DateTime
import scala.concurrent.duration._

object InspectionManager {

  sealed trait State
  case object Collecting extends State
  case object Inspecting extends State

  sealed trait Data
  case class Collection(population: Int) extends Data
  case class Inspection(progressPopulation: Int, totalPopulation: Int, receiver: ActorRef) extends Data

  val inspectionTimer = "inspection-timer"
  val inspectionTimeoutTimer = "inspection-timeout-timer"


  case class Sample(measurement: Measurement)
  case class Execute(population: Int)
  case class Inspect()
  case class AbortInspection()
  case class InspectionTimeout()
}

import InspectionManager._

class InspectionManager extends Actor with LoggingFSM[State, Data] with Stash with Config {
  import InspectionManager._

  val config = context.system.settings.config

  val sumCalculatorRouter = context.actorSelection("../calculation-supervisor/sum-calculator-router")

  val lowerLimitCalculator = context.actorSelection("../calculation-supervisor/lower-limit-calculator")

  val inspectorRouter = context.actorSelection("../calculation-supervisor/inspector-router")

  startWith(Collecting, Collection(0))

  when(Collecting) {

    case Event(Packet(measurement, _), c: Collection) =>
      sumCalculatorRouter ! Sample(measurement)
      inspectorRouter     ! Sample(measurement)
      stay() using c.copy(population = c.population + 1)

    case Event(Inspection, Collection(totalPopulation)) =>
      sumCalculatorRouter  ! Broadcast(Execute(totalPopulation))
      lowerLimitCalculator ! Execute(totalPopulation)
      inspectorRouter      ! Broadcast(Execute(totalPopulation))
      setTimer(inspectionTimeoutTimer, InspectionTimeout, inspectingTimeoutDuration milliseconds)
      goto(Inspecting) using Inspection(0, totalPopulation, sender)

    case Event(Inspector.Done(population), _) =>
      // 既に解析が終了しているはずなので、無視する
      stay()

    case Event(AbortInspection, _) =>
      // 解析中でないので、無視する
      stay()
  }

  when(Inspecting) {

    case Event(Inspector.Done(population), inspection @ Inspection(progressPopulation, totalPopulation, receiver)) =>
      val progress = progressPopulation + population
      if (progress == totalPopulation) {
        goto(Collecting) using Collection(0)
      } else {
        stay() using inspection.copy(progressPopulation = progress)
      }

    case Event(Packet(measurement, _), _) =>
      stash()
      stay()

    case Event(AbortInspection, _) =>
      log.info("Aborting inspection")
      sumCalculatorRouter  ! Broadcast(AbortInspection)
      lowerLimitCalculator ! AbortInspection
      inspectorRouter      ! Broadcast(AbortInspection)
      goto(Collecting) using Collection(0)
  }

  whenUnhandled {
    case Event(InspectionTimeout, _) =>
      self ! AbortInspection
      stay()
  }

  override def preStart() = {
    setTimer(inspectionTimer, Inspection, inspectionInterval milliseconds)
  }

  onTransition {

    case Collecting -> Inspecting =>
      setTimer(inspectionTimeoutTimer, InspectionTimeout, inspectingTimeoutDuration milliseconds)

    case Inspecting -> Collecting =>
      setTimer(inspectionTimer, Inspection, inspectionInterval milliseconds)
      cancelTimer(inspectionTimeoutTimer)
  }

  initialize()
}
