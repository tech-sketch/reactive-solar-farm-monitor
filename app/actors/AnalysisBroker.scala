package actors

import akka.actor._
import akka.cluster.{Member, MemberStatus, Cluster}
import akka.cluster.ClusterEvent._
import com.example.analysis
import config.AppConfig

import scala.concurrent.duration._
import scala.util.Random

object AnalysisBroker {

  sealed trait State
  case object Unreachable extends State
  case object Reachable extends State

  sealed trait Data
  case class ProxyData(subscribers: Set[ActorRef], analyzerNodes: Set[Member]) extends Data

  val emptySubscribers = ProxyData(subscribers = Set(), analyzerNodes = Set())

  val unreachableTimeoutTimer = "connection-attempt-timeout-timer"
  val errorNotificationTimer = "error-notification-timer"

  case class Subscribe()
  case class AnalyzerClusterChanged()
  case class UnreachableAnalyzer()
  case class UnreachableTimeout()

  case class CannotConnectAnalyzerException() extends IllegalStateException
}

import AnalysisBroker._

class AnalysisBroker extends Actor with LoggingFSM[State, Data] with AppConfig {

  val config = context.system.settings.config

  Cluster(context.system).subscribe(self, classOf[MemberEvent])

  startWith(Unreachable, emptySubscribers)

  when(Unreachable) {

    case Event(UnreachableAnalyzer, ProxyData(subscribers, associated)) =>
      if (associated.isEmpty) {
        subscribers foreach { _ ! UnreachableAnalyzer }
      }
      stay()

    case Event(msg: analysis.api.ApiData, ProxyData(subscribers, _)) =>
      // Cluster Event が起きる前にAnalyzerからデータが届くことがあるが、
      // Cluster Event が起きて Node が確実に参加したことがわかってからデータを処理する(ここでは無視)
      stay()

    case Event(UnreachableTimeout, _) =>
      throw new CannotConnectAnalyzerException
  }

  when(Reachable) {

    case Event(msg: analysis.api.Snapshot, ProxyData(subscribers, _)) =>
      subscribers foreach { _ forward msg }
      stay()

    case Event(msg: analysis.api.LowerLimit, ProxyData(subscribers, _)) =>
      subscribers foreach { _ forward msg }
      stay()

    case Event(msg: analysis.api.Alert, ProxyData(subscribers, _)) =>
      subscribers foreach { _ forward msg }
      stay()
  }

  whenUnhandled {

    /* Cluster Events */

    case Event(current: CurrentClusterState, data: ProxyData) =>
      val currentNodes =
        current.members.collect {
          case m if m.hasRole("analyzer") && m.status == MemberStatus.Up => m
        }
      self ! AnalyzerClusterChanged
      stay() using data.copy(analyzerNodes = data.analyzerNodes ++ currentNodes)

    case Event(MemberUp(m), data: ProxyData) if m.hasRole(analyzerClusterRole) =>
      self ! AnalyzerClusterChanged
      stay() using data.copy(analyzerNodes = data.analyzerNodes + m)

    case Event(MemberRemoved(m, _), data: ProxyData) if m.hasRole(analyzerClusterRole) =>
      self ! AnalyzerClusterChanged
      stay() using data.copy(analyzerNodes = data.analyzerNodes - m)

    case Event(_: MemberEvent, _) =>
      stay()

    case Event(AnalyzerClusterChanged, d: ProxyData) =>
      log.info("Analyzer Nodes: " + d.analyzerNodes)
      if (d.analyzerNodes.isEmpty) {
        goto(Unreachable)
      } else {
        goto(Reachable)
      }

    /* Subscriber Events */

    case Event(Subscribe, data @ ProxyData(subscribers, _)) =>
      val subscriber = sender
      context.watch(subscriber)
      stay() using data.copy(subscribers = subscribers + subscriber)

    case Event(Terminated(subscriber), data @ ProxyData(subscribers, _)) =>
      stay() using data.copy(subscribers = subscribers - subscriber)
  }

  override def preStart() = {
    setTimer(errorNotificationTimer, UnreachableAnalyzer, errorNotificationInterval milliseconds, repeat = true)
    setTimer(unreachableTimeoutTimer, UnreachableTimeout, unreachableTimeoutDuration milliseconds)
  }

  onTransition {

    case _ -> Unreachable =>
      setTimer(errorNotificationTimer, UnreachableAnalyzer, errorNotificationInterval milliseconds, repeat = true)
      setTimer(unreachableTimeoutTimer, UnreachableTimeout, unreachableTimeoutDuration milliseconds)

    case Unreachable -> Reachable =>
      cancelTimer(errorNotificationTimer)
      cancelTimer(unreachableTimeoutTimer)
  }

  initialize()
}
