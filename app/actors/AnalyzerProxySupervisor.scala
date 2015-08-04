package actors

import akka.actor.SupervisorStrategy._
import akka.actor._
import scala.concurrent.duration._

class AnalyzerProxySupervisor extends Actor with ActorLogging {

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 1 minutes) {
      case _ => Stop
    }

  val analyzerProxy = createAnalyzerProxy()

  def receive = {
    case msg: Any =>
      analyzerProxy forward msg
  }

  def createAnalyzerProxy() = context.actorOf(Props[AnalyzerProxy], "analyzer-proxy")
}
