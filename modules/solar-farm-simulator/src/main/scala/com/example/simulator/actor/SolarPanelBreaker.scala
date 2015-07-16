package com.example.simulator.actor

import akka.actor._
import com.example.Config
import scala.concurrent.duration._

object SolarPanelBreaker {
  case class Break()
  case class Trouble()

  def props(panelRouter: ActorRef) = Props(new SolarPanelBreaker(panelRouter))
}

class SolarPanelBreaker(panelRouter: ActorRef) extends Actor with ActorLogging with Config {

  import SolarPanelBreaker._

  val config = context.system.settings.config

  import scala.concurrent.ExecutionContext.Implicits.global
  val breakSchedule =
    context.system.scheduler.schedule(simulatorBreakInitialDelay millisecond, simulatorBreakInterval millisecond, self, Break)

  def receive = {
    case Break =>
      panelRouter ! Trouble
  }

  override def postStop() = {
    breakSchedule.cancel()
  }
}