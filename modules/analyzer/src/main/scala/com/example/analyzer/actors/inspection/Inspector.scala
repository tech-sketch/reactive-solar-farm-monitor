package com.example.analyzer.actors.inspection

import akka.actor._
import com.example.analyzer.MonitorContactSupervisor
import com.example.analyzer.actors.inspection.InspectionManager.{AbortInspection, Execute}
import com.example.analyzer.actors.inspection.LowerLimitCalculator.{Empty, EmptyLowerLimit}
import com.example.farm.api.Measurement
import com.example.{Config, analysis}
import org.joda.time.DateTime
import org.slf4j.MDC

import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode

object Inspector {

  sealed trait State
  case object Collecting extends  State
  case object Inspecting extends State

  sealed trait Data
  case class Inspection(measurements: Seq[Measurement], owner: ActorRef) extends Data
  val emptyInspection = Inspection(measurements = Seq(), owner = Actor.noSender)

  case class PartialSum(sum: BigDecimal, population: Int)
  case class Done(population: Int)
}

import Inspector._

class Inspector extends LoggingFSM[State, Data] with Stash {

  val monitorContact = context.actorSelection(MonitorContactSupervisor.monitorContactAbsolutePath)

  startWith(Collecting, emptyInspection)

  when(Collecting) {

    case Event(InspectionManager.Sample(measurement), samples: Inspection) =>
      stay() using samples.copy(measurements = samples.measurements :+ measurement)

    case Event(_: Execute, samples: Inspection) =>
      goto(Inspecting) using samples.copy(owner = sender)

    case Event(_: LowerLimitCalculator.LowerLimit, _) | Event(_: LowerLimitCalculator.EmptyLowerLimit, _) =>
      stash()
      stay()
  }

  when(Inspecting) {

    case Event(LowerLimitCalculator.EmptyLowerLimit(population), Inspection(_, owner)) =>
      owner ! Done(population)
      goto(Collecting) using emptyInspection

    case Event(LowerLimitCalculator.LowerLimit(lowerLimit, population), Inspection(measurements, owner)) =>

      import scala.concurrent.ExecutionContext.Implicits.global

      Future.sequence {
        MDC.put("role", "Worker")
        measurements map { m =>
          Future {
            MDC.put("role", "Worker")
            if (m.measuredValue < lowerLimit) {
              log.debug(s"Alert: panelId ${m.panelId},  measuredDateTime ${m.measuredDateTime.toString("HH:mm:ss:SSS")}, measuredValue ${m.measuredValue.setScale(2, RoundingMode.HALF_DOWN)}, lowerLimit ${lowerLimit.setScale(2, RoundingMode.HALF_DOWN)}, population ${population}")
              monitorContact ! analysis.api.Alert(m.panelId, DateTime.now(), m.measuredValue, m.measuredDateTime)
            } else {
              log.debug(s"OK: panelId ${m.panelId},  measuredDateTime ${m.measuredDateTime.toString("HH:mm:ss:SSS")}, measuredValue ${m.measuredValue.setScale(2, RoundingMode.HALF_DOWN)}, lowerLimit ${lowerLimit.setScale(2, RoundingMode.HALF_DOWN)}, population ${population}")
            }
          }
        }
      } onSuccess { case _ => owner ! Done(population) }

      goto(Collecting) using emptyInspection
   }

  override def preStart() = {
    MDC.put("role", "Worker")
  }

  whenUnhandled {

    case Event(AbortInspection, _) =>
      goto(Collecting) using emptyInspection
  }

  onTransition {
    case Collecting -> Inspecting =>
      unstashAll()
  }

  initialize()
}