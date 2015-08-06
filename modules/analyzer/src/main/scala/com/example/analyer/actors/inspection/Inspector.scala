package com.example.analyer.actors.inspection

import akka.actor._
import com.example.analyer.actors.inspection.InspectionManager.Execute
import com.example.analyer.actors.inspection.LowerLimitCalculator.EmptyLowerLimit
import com.example.farm.api.Measurement
import com.example.{Config, analysis}
import org.joda.time.DateTime

import scala.concurrent.Future

object Inspector {

  sealed trait State
  case object Collecting extends  State
  case object Inspecting extends State

  sealed trait Data
  case class Inspection(measurements: Seq[Measurement], owner: ActorRef, receiver: ActorRef) extends Data
  val emptyInspection = Inspection(measurements = Seq(), owner = Actor.noSender, receiver = Actor.noSender)

  case class PartialSum(sum: BigDecimal, population: Int)
  case class Done(population: Int)
}

import Inspector._

class Inspector extends LoggingFSM[State, Data] with Stash {

  startWith(Collecting, emptyInspection)

  when(Collecting) {

    case Event(InspectionManager.Sample(measurement), samples: Inspection) =>
      stay() using samples.copy(measurements = samples.measurements :+ measurement)

    case Event(Execute(_, receiver), samples: Inspection) =>
      goto(Inspecting) using samples.copy(owner = sender, receiver = receiver)

    case Event(_: LowerLimitCalculator.LowerLimit, _) | Event(_: LowerLimitCalculator.EmptyLowerLimit, _) =>
      stash()
      stay()
  }

  when(Inspecting) {

    case Event(LowerLimitCalculator.EmptyLowerLimit(population), Inspection(_, owner, _)) =>
      owner ! Done(population)
      goto(Collecting) using emptyInspection

    case Event(LowerLimitCalculator.LowerLimit(lowerLimit, population), Inspection(measurements, owner, receiver)) =>

      import scala.concurrent.ExecutionContext.Implicits.global

      Future.sequence {
        measurements map { m =>
          Future {
            if (m.measuredValue < lowerLimit) {
              log.info(s"Alert: panelId ${m.panelId},  measuredDateTime ${m.measuredDateTime}, measuredValue ${m.measuredValue}, lowerLimit ${lowerLimit}, population ${population}")
              receiver ! analysis.api.Alert(m.panelId, DateTime.now(), m.measuredValue, m.measuredDateTime)
            }
          }
        }
      } onSuccess { case _ => owner ! Done(population) }

      goto(Collecting) using emptyInspection
   }

  onTransition {
    case Collecting -> Inspecting =>
      unstashAll()
  }

  initialize()
}