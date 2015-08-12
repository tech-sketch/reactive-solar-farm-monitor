package actors

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterClient
import akka.remote._
import com.example.analysis
import config.AppConfig

import scala.concurrent.duration._
import scala.collection.JavaConversions._

object AnalyzerProxy {

  sealed trait State
  case object Init extends State
  case object Stable extends State
  case object Unstable extends State

  sealed trait Data
  case class ProxyData(subscribers: Set[ActorRef], associated: Set[Address]) extends Data
  val emptySubscribers = ProxyData(subscribers = Set(), associated = Set())

  val inspectionRequestTimer = "inspection-request-timer"
  val inspectionResponseTimeoutTimer = "inspection-response-timeout-timer"
  val measurementRequestTimer = "measurement-request-timer"
  val measurementResponseTimeoutTimer = "measurement-response-timeout-timer"
  val connectionAttemptTimer = "connection-attempt-timer"
  val unstableTimeoutTimer = "connection-attempt-timeout-timer"
  val errorNotificationTimer = "error-notification-timer"

  case class Subscribe()
  case class InspectionRequest()
  case class InspectionResponseTimeout()
  case class MeasurementRequest()
  case class MeasurementResponseTimeout()
  case class UnreachableAnalyzer()
  case class UnstableTimeout()

  case class CannotConnectAnalyzerException() extends IllegalStateException
}

import AnalyzerProxy._

class AnalyzerProxy extends Actor with LoggingFSM[State, Data] with AppConfig {

  val config = context.system.settings.config

  val initialContacts = contactPoints.map {
    case AddressFromURIString(address) =>
      context.actorSelection(RootActorPath(address) / "user" / "receptionist")
  }.toSet

  val clusterClient = context.actorOf(ClusterClient.props(initialContacts), "cluster-client")

  context.system.eventStream.subscribe(self, classOf[RemotingLifecycleEvent])

  startWith(Init, emptySubscribers)

  when(Init, stateTimeout = initialConnectionAttemptTimeout milliseconds) {

    case Event(StateTimeout, _) =>
      goto(Unstable)
  }

  when(Stable) {

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

  when(Unstable) {

    case Event(UnreachableAnalyzer, ProxyData(subscribers, associated)) =>
      if (associated.isEmpty) {
        subscribers foreach { _ ! UnreachableAnalyzer }
      }
      stay()

    case Event(`unstableTimeoutDuration`, _) =>
      throw new CannotConnectAnalyzerException
  }

  whenUnhandled {

    case Event(InspectionRequest, _) =>
      clusterClient ! ClusterClient.Send("/user/analysis-supervisor/inspection-supervisor/inspection-manager", analysis.api.InspectionRequest, false)
      setTimer(inspectionResponseTimeoutTimer, InspectionResponseTimeout, inspectionResponseTimeoutDuration milliseconds)
      stay()

    case Event(InspectionResponseTimeout, _) =>
      self ! InspectionRequest
      goto(Unstable)

    case Event(MeasurementRequest, _) =>
      clusterClient ! ClusterClient.Send("/user/analysis-supervisor/buffer", analysis.api.MeasurementRequest, false)
      setTimer(measurementResponseTimeoutTimer, MeasurementResponseTimeout, measurementResponseTimeoutDuration milliseconds)
      stay()

    case Event(MeasurementResponseTimeout, _) =>
      self ! MeasurementRequest
      goto(Unstable)

    case Event(analysis.api.DoneInspection, _) =>
      cancelTimer(inspectionResponseTimeoutTimer)
      setTimer(inspectionRequestTimer,  InspectionRequest, inspectionRequestInterval milliseconds)
      goto(Stable)

    case Event(msg: analysis.api.Snapshot, ProxyData(subscribers, _)) =>
      cancelTimer(measurementResponseTimeoutTimer)
      setTimer(measurementRequestTimer,  MeasurementRequest, measurementRequestInterval milliseconds)
      goto(Stable)

    case Event(msg: analysis.api.Alert, ProxyData(subscribers, _)) =>
      goto(Stable)

    case Event(msg: analysis.api.LowerLimit, ProxyData(subscribers, _)) =>
      goto(Stable)

    case Event(e @ AssociatedEvent(_, remoteAddress, false), data: ProxyData) =>
      log.info(e.toString)
      log.info(data.toString)
      stay() using data.copy(associated = data.associated + remoteAddress)

    case Event(e @ DisassociatedEvent(_, remoteAddress, false), data: ProxyData) =>
      log.info(e.toString)
      log.info(data.toString)
      goto(Unstable) using data.copy(associated = data.associated - remoteAddress)

    case Event(error: AssociationErrorEvent, _) =>
      log.error(error.cause, "Error detected in Remote Analyzer")
      goto(Unstable)

    case Event(Subscribe, data @ ProxyData(subscribers, _)) =>
      val subscriber = sender
      context.watch(subscriber)
      stay() using data.copy(subscribers = subscribers + subscriber)

    case Event(Terminated(subscriber), data @ ProxyData(subscribers, _)) =>
      stay() using data.copy(subscribers = subscribers - subscriber)
  }

  override def preStart() = {
    self ! InspectionRequest
    self ! MeasurementRequest
  }

  onTransition {

    case _ -> Unstable =>
      setTimer(errorNotificationTimer, UnreachableAnalyzer, errorNotificationInterval milliseconds, repeat = true)
      setTimer(unstableTimeoutTimer, UnstableTimeout, unstableTimeoutDuration milliseconds)

    case Unstable -> Stable =>
      cancelTimer(errorNotificationTimer)
      cancelTimer(unstableTimeoutTimer)
  }

  initialize()
}
