package actors

import akka.actor._
import akka.cluster.{MemberStatus, Cluster}
import akka.cluster.ClusterEvent._
import com.example.analysis
import config.AppConfig

import scala.concurrent.duration._
import scala.util.Random

object AnalyzerProxy {

  sealed trait State
  case object Unreachable extends State
  case object Reachable extends State

  sealed trait Data
  case class ProxyData(subscribers: Set[ActorRef], analyzerNodeAddresses: Set[Address]) extends Data {
    def electAnalysisProxyPath(): ActorPath = {
      val nodeAddress = analyzerNodeAddresses.toIndexedSeq(Random.nextInt(analyzerNodeAddresses.size))
      RootActorPath(nodeAddress) / "user" / "analysis-proxy"
    }
  }

  val emptySubscribers = ProxyData(subscribers = Set(), analyzerNodeAddresses = Set())

  val inspectionRequestTimer = "inspection-request-timer"
  val inspectionResponseTimeoutTimer = "inspection-response-timeout-timer"
  val measurementRequestTimer = "measurement-request-timer"
  val measurementResponseTimeoutTimer = "measurement-response-timeout-timer"
  val connectionAttemptTimer = "connection-attempt-timer"
  val unreachableTimeoutTimer = "connection-attempt-timeout-timer"
  val errorNotificationTimer = "error-notification-timer"

  case class Subscribe()
  case class InspectionRequest()
  case class InspectionResponseTimeout()
  case class MeasurementRequest()
  case class MeasurementResponseTimeout()
  case class UnreachableAnalyzer()
  case class UnreachableTimeout()

  case class CannotConnectAnalyzerException() extends IllegalStateException
}

import AnalyzerProxy._

class AnalyzerProxy extends Actor with LoggingFSM[State, Data] with AppConfig {

  val config = context.system.settings.config

  Cluster(context.system).subscribe(self, classOf[MemberEvent])

  startWith(Unreachable, emptySubscribers)


  when(Unreachable, stateTimeout = unreachableTimeoutDuration milliseconds) {

    case Event(UnreachableAnalyzer, ProxyData(subscribers, associated)) =>
      if (associated.isEmpty) {
        subscribers foreach { _ ! UnreachableAnalyzer }
      }
      stay()

    case Event(UnreachableTimeout | StateTimeout, _) =>
      throw new CannotConnectAnalyzerException
  }

  when(Reachable) {

    case Event(MeasurementRequest, data: ProxyData) =>
      context.actorSelection(data.electAnalysisProxyPath()) ! analysis.api.MeasurementRequest
      setTimer(measurementResponseTimeoutTimer, MeasurementResponseTimeout, measurementResponseTimeoutDuration milliseconds)
      stay()

    case Event(MeasurementResponseTimeout, _) =>
      self ! MeasurementRequest
      stay()

    case Event(InspectionRequest, data: ProxyData) =>
      context.actorSelection(data.electAnalysisProxyPath()) ! analysis.api.InspectionRequest
      setTimer(inspectionResponseTimeoutTimer, InspectionResponseTimeout, inspectionResponseTimeoutDuration milliseconds)
      stay()

    case Event(InspectionResponseTimeout, _) =>
      self ! InspectionRequest
      stay()

    case Event(analysis.api.DoneInspection, _) =>
      cancelTimer(inspectionResponseTimeoutTimer)
      setTimer(inspectionRequestTimer,  InspectionRequest, inspectionRequestInterval milliseconds)
      stay()

    case Event(msg: analysis.api.Snapshot, ProxyData(subscribers, _)) =>
      cancelTimer(measurementResponseTimeoutTimer)
      setTimer(measurementRequestTimer,  MeasurementRequest, measurementRequestInterval milliseconds)
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
      val currentNodeAddresses =
        current.members.collect {
          case m if m.hasRole("analyzer") && m.status == MemberStatus.Up => m.address
        }
      log.info("Analyzer Nodes: " + currentNodeAddresses)
      if (currentNodeAddresses.isEmpty) {
        goto(Unreachable)
      } else {
        goto(Reachable) using data.copy(analyzerNodeAddresses = data.analyzerNodeAddresses ++ currentNodeAddresses)
      }

    case Event(MemberUp(m), data: ProxyData) if m.hasRole(analyzerClusterRole) =>
      val newData = data.copy(analyzerNodeAddresses = data.analyzerNodeAddresses + m.address)
      log.info("Analyzer Nodes: " + newData.analyzerNodeAddresses)
      goto(Reachable) using newData

    case Event(MemberRemoved(m, _), data: ProxyData) if m.hasRole(analyzerClusterRole) =>
      val newData = data.copy(analyzerNodeAddresses = data.analyzerNodeAddresses - m.address)
      log.info("Analyzer Nodes: " + newData.analyzerNodeAddresses)

      if (newData.analyzerNodeAddresses.isEmpty) {
        goto(Unreachable) using newData
      } else {
        goto(Reachable) using newData
      }

    case Event(_: MemberEvent, _) =>
      stay()

    /* Subscriber Events */

    case Event(Subscribe, data @ ProxyData(subscribers, _)) =>
      val subscriber = sender
      context.watch(subscriber)
      stay() using data.copy(subscribers = subscribers + subscriber)

    case Event(Terminated(subscriber), data @ ProxyData(subscribers, _)) =>
      stay() using data.copy(subscribers = subscribers - subscriber)
  }

  onTransition {

    case _ -> Unreachable =>
      setTimer(errorNotificationTimer, UnreachableAnalyzer, errorNotificationInterval milliseconds, repeat = true)
      setTimer(unreachableTimeoutTimer, UnreachableTimeout, unreachableTimeoutDuration milliseconds)

    case Unreachable -> Reachable =>
      self ! InspectionRequest
      self ! MeasurementRequest
      cancelTimer(errorNotificationTimer)
      cancelTimer(unreachableTimeoutTimer)
  }

  initialize()
}
