package com.example.simulator.actor

import akka.actor._
import com.example.Config
import com.example.farm.api._
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.util.Random

object SolarPanel {

  sealed trait State
  case object Normal extends State
  case object Broken extends State

  case class Measure()
  case class Repair()

  def props(measurementChannel: ActorRef) =
    Props(new SolarPanel(measurementChannel))
}

import SolarPanel._
class SolarPanel(measurementChannel: ActorRef) extends LoggingFSM[State, Unit] with Config {

  val config = context.system.settings.config

  import scala.concurrent.ExecutionContext.Implicits.global
  val measureSchedule =
    context.system.scheduler.schedule(simulatorMeasureInitialDelay milliseconds, simulatorMeasureInterval milliseconds, self, Measure)

  startWith(Normal, ())

  when(Normal) {
    case Event(Measure, _) =>
      measurementChannel !
        Measurement(panelId = "%x".format(hashCode), measureValue(), pickDateTime())
      stay()
    case Event(SolarPanelBreaker.Trouble, _) =>
      goto(Broken)
  }

  when(Broken) {
    case Event(Measure, _) =>
      measurementChannel !
        Measurement(panelId = "%x".format(hashCode), measureValue() * (1 - simulatorAttenuationFactor), pickDateTime())
      stay()
    case Event(Repair, _) =>
      goto(Normal)
  }

  onTransition {
    case Normal -> Broken =>
      context.system.scheduler.scheduleOnce(simulatorRepairDelay milliseconds, self, Repair)
  }

  def pickDateTime() =
    DateTime.now()

  def measureValue() =
    BigDecimal(rock(simulatorBaseMeasuredValue))

  def rock(value: Double) =
    value - (simulatorMeasuredValueAmplitude * (Random.nextDouble() - 0.5))

  override def postStop() = {
    measureSchedule.cancel()
  }

  initialize()
}