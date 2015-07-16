package com.example.analyer.actors.inspection

import akka.actor._
import com.example.analyer.actors.inspection.InspectionManager.Execute
import com.example.analyer.actors.inspection.MeanCalculator.EmptyMean
import com.example.farm.api.Measurement
import com.example.{Config, analysis}
import org.joda.time.DateTime

import scala.concurrent.Future

object Inspector {

  sealed trait State
  case object Collecting extends  State
  case object Inspecting extends State

  sealed trait Data
  case class Inspection(measurements: Seq[Measurement], owner: ActorRef) extends Data
  val emptyInspection = Inspection(measurements = Seq(), owner = Actor.noSender)

  case class PartialSum(sum: BigDecimal, population: Int)
  case class Done(population: Int)

  def props(receiver: ActorRef) = Props(new Inspector(receiver))
}

import Inspector._

class Inspector(receiver: ActorRef) extends LoggingFSM[State, Data] with Stash with Config {

  val config = context.system.settings.config

  startWith(Collecting, emptyInspection)

  when(Collecting) {

    case Event(InspectionManager.Sample(measurement), samples: Inspection) =>
      stay() using samples.copy(measurements = samples.measurements :+ measurement)

    case Event(Execute(_), samples: Inspection) =>
      goto(Inspecting) using samples.copy(owner = sender)

    case Event(_: MeanCalculator.Mean, _) | Event(_: MeanCalculator.EmptyMean, _) =>
      stash()
      stay()
  }

  when(Inspecting) {

    case Event(MeanCalculator.EmptyMean(population), Inspection(_, owner)) =>
      owner ! Done(population)
      goto(Collecting) using emptyInspection

    case Event(MeanCalculator.Mean(mean, population), Inspection(measurements, owner)) =>

      val lowerLimit = mean * alertThresholdPer / 100

      import scala.concurrent.ExecutionContext.Implicits.global

      Future.sequence {
        measurements map { m =>
          Future {
            if (m.measuredValue < lowerLimit) {
              log.info(s"Alert: panelId ${m.panelId},  measuredDateTime ${m.measuredDateTime}, measuredValue ${m.measuredValue}, mean ${mean}, population ${population}")
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