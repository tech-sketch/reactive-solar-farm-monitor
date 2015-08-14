package com.example.analyzer

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import com.example.analyzer.actors.MonitorContact
import scala.concurrent.duration._

object MonitorContactSupervisor {
  val monitorContactAbsolutePath = "/user/monitor-contact-supervisor/monitor-contact"
}

class MonitorContactSupervisor extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minute) {
      case _ => Restart
    }

  def receive = Actor.emptyBehavior

  override def preStart() = {
    createMonitorContact()
  }

  def createMonitorContact() =
    context.actorOf(Props[MonitorContact], "monitor-contact")
}
