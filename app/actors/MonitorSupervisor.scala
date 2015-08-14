package actors

import akka.actor.SupervisorStrategy._
import akka.actor._
import scala.concurrent.duration._

class MonitorSupervisor extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minutes) {
      case _ => Stop
    }

  val analysisBroker = createAnalysisBroker()

  def receive = {
    case msg: Any =>
      analysisBroker forward msg
  }

  def createAnalysisBroker() = context.actorOf(Props[AnalysisBroker], "analysis-broker")
}
