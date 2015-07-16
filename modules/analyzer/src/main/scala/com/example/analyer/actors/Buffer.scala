package com.example.analyer.actors

import akka.actor._
import com.example.Config
import com.example.analyer.actors.Channel.Packet
import com.example.farm
import com.example.analysis
import com.example.analysis.api._
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.concurrent.duration._

object Buffer {

  sealed trait State
  case object UnOpen extends State
  case object Receiving extends State
  case object CollectingGhosts extends State

  sealed trait Data
  case class Chunk(measurements: Map[PanelId, analysis.api.Measurement]) extends Data

  case class Open()
  case class TakeSnapshot()
  case class CollectGhosts()
  case class GhostsCollected(measurements: Map[PanelId, analysis.api.Measurement])

  def props(receiver: ActorRef) = Props(new Buffer(receiver))
}

import Buffer._

class Buffer(receiver: ActorRef) extends LoggingFSM[State, Data] with Stash with Config {

  val config = context.system.settings.config

  import scala.concurrent.ExecutionContext.Implicits.global

  val snapshotSchedule =
    context.system.scheduler.schedule(snapshotInitialDelay milliseconds, snapshotInterval milliseconds, self, TakeSnapshot)

  val ghostCollectionSchedule =
    context.system.scheduler.schedule(ghostCollectionInitialDelay milliseconds, ghostCollectionInterval milliseconds, self, CollectGhosts)

  startWith(Receiving, Chunk(Map()))

  when(UnOpen) {
    case Event(Open, chunk: Chunk) =>

      goto(Receiving)
  }

  when(Receiving) {
    case Event(Packet(farm.api.Measurement(panelId, measuredValue, measuredDateTime), _), chunk: Chunk) =>
      val measurement = analysis.api.Measurement(panelId, measuredValue, measuredDateTime)
      stay() using (chunk.copy(measurements = (chunk.measurements + (panelId -> measurement))))

    case Event(TakeSnapshot, chunk: Chunk) =>
      receiver ! Snapshot(chunk.measurements)
      stay()

    case Event(CollectGhosts, chunk: Chunk) =>
      import scala.concurrent.ExecutionContext.Implicits.global
      Future {
        val deadLine = DateTime.now().minus(ghostLifeSpan)
        chunk.measurements.filter { e => e._2.measuredDateTime.isAfter(deadLine) }
      } onSuccess {
        case measurements =>
          self ! GhostsCollected(measurements)
      }
      goto(CollectingGhosts)
  }

  when(CollectingGhosts) {
    case Event(GhostsCollected(m), chunk: Chunk) =>
      goto(Receiving) using(chunk.copy(measurements = m))

    case Event(TakeSnapshot, _) =>
      // 何もしない
      stay()

    case Event(p: Packet, _) =>
      stash()
      stay()
  }

  onTransition {
    case CollectingGhosts -> Receiving =>
      unstashAll()
  }

  override def postStop() = {
    snapshotSchedule.cancel()
    ghostCollectionSchedule.cancel()
  }

  initialize()
}
