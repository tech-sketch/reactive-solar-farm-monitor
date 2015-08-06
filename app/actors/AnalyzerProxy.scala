package actors

import akka.actor._
import akka.remote._
import com.example.analysis
import config.AppConfig

import scala.concurrent.duration._

object AnalyzerProxy {

  sealed trait State
  case object Unreachable extends State
  case object Reachable extends State
  case object AttemptingConnect extends State

  sealed trait Data
  case class Subscribers(subscribers: Set[ActorRef]) extends Data
  val emptySubscribers = Subscribers(subscribers = Set())

  val inspectionRequestTimer = "inspection-request-timer"
  val measurementRequestTimer = "measurement-request-timer"
  val connectionAttemptTimer = "connection-attempt-timer"
  val connectionAttemptTimeoutTimer = "connection-attempt-timeout-timer"
  val errorNotificationTimer = "error-notification-timer"

  case class Subscribe()
  case class AttemptConnect()
  case class InspectionRequest()
  case class MeasurementRequest()
  case class UnreachableAnalyzer()
  case class ConnectionAttemptTimeout()

  case class CannotConnectAnalyzerException() extends IllegalStateException
}

import AnalyzerProxy._

class AnalyzerProxy extends Actor with LoggingFSM[State, Data] with AppConfig {

  val config = context.system.settings.config

  val inspector = context.actorSelection(inspectorEntryActorPath)

  val buffer = context.actorSelection(bufferEntryActorPath)

  context.system.eventStream.subscribe(self, classOf[RemotingLifecycleEvent])

  startWith(Unreachable, emptySubscribers)

  when(Unreachable, stateTimeout = initialConnectionAttemptTimeout milliseconds) {

    case Event(_: AssociatedEvent, _) =>
      goto(Reachable)

    case Event(_: DisassociatedEvent | StateTimeout, _) =>
      goto(AttemptingConnect)
  }

  when(Reachable) {

    case Event(msg: analysis.api.Alert, Subscribers(subscribers)) =>
      subscribers foreach { _ forward msg }
      stay()

    case Event(msg: analysis.api.LowerLimit, Subscribers(subscribers)) =>
      subscribers foreach { _ forward msg }
      stay()

    case Event(analysis.api.DoneInspection, _) =>
      setTimer(inspectionRequestTimer,  InspectionRequest, inspectionRequestInterval milliseconds)
      stay()

    case Event(InspectionRequest, _) =>
      inspector ! analysis.api.InspectionRequest
      stay()

    case Event(msg: analysis.api.Snapshot, Subscribers(subscribers)) =>
      setTimer(measurementRequestTimer,  MeasurementRequest, measurementRequestInterval milliseconds)
      subscribers foreach { _ forward msg }
      stay()

    case Event(MeasurementRequest, _) =>
      buffer ! analysis.api.MeasurementRequest
      stay()

    case Event(_: DisassociatedEvent, _) =>
      goto(Unreachable)
  }

  when(AttemptingConnect) {

    case Event(_: AssociatedEvent, _) =>
      goto(Reachable)

    case Event(_: DisassociatedEvent, _) =>
      stay()

    case Event(ConnectionAttemptTimeout, _) =>
      throw new CannotConnectAnalyzerException
  }

  whenUnhandled {

    case Event(AttemptConnect, _) =>
      buffer ! analysis.api.MeasurementRequest
      inspector ! analysis.api.InspectionRequest
      stay()

    case Event(Subscribe, data @ Subscribers(subscribers)) =>
      val subscriber = sender
      context.watch(subscriber)
      stay() using data.copy(subscribers = subscribers + subscriber)

    case Event(UnreachableAnalyzer, Subscribers(subscribers)) =>
      subscribers foreach { _ ! UnreachableAnalyzer}
      stay()

    case Event(Terminated(subscriber), data @ Subscribers(subscribers)) =>
      stay() using data.copy(subscribers = subscribers - subscriber)

    case Event(error: AssociationErrorEvent, _) =>
      log.error(error.cause, "Error detected in Remote Analyzer")
      stay()
  }

  override def preStart() = {
    self ! AttemptConnect
  }

  onTransition {

    case _ -> AttemptingConnect =>
      setTimer(connectionAttemptTimer, AttemptConnect, connectionAttemptInterval milliseconds, repeat = true)
      setTimer(errorNotificationTimer, UnreachableAnalyzer, errorNotificationInterval milliseconds, repeat = true)
      setTimer(connectionAttemptTimeoutTimer, ConnectionAttemptTimeout, connectionAttemptTimeout milliseconds)

    case AttemptingConnect -> Reachable =>
      cancelTimer(connectionAttemptTimer)
      cancelTimer(errorNotificationTimer)
      cancelTimer(connectionAttemptTimeoutTimer)
  }

  initialize()
}
