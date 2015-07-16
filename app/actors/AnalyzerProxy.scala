package actors

import akka.actor._
import com.example.analysis

object AnalyzerProxy {
  case class Subscribe()
}

class AnalyzerProxy extends Actor with ActorLogging {

  import AnalyzerProxy._

  lazy val analyzerEntryActorPath = context.system.settings.config.getString("solar-farm-monitor.analyzer.entry-actor-path")

  val analyzer = context.actorSelection(analyzerEntryActorPath)

  var receivers: Set[ActorRef] = Set()

  def receive = {

    case Subscribe =>
      val receiver = sender

      context.watch(receiver)
      receivers += receiver

    case Terminated(receiver) =>

      context.unwatch(receiver)
      receivers -= receiver

    case msg =>
      receivers foreach { _ forward msg }
  }

  override def preStart() = {
    // TODO 接続が成功したという応答を受けるようにしないといけない
    analyzer ! analysis.api.Connect
    log.info("Connecting to {} ...", analyzerEntryActorPath)
  }
}
