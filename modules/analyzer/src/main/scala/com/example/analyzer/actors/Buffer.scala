package com.example.analyzer.actors

import akka.actor._
import com.example.Config
import com.example.analyzer.MonitorContactSupervisor
import com.example.analyzer.actors.Channel.Packet
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

  val snapshotTimer = "snapshot-timer"
  val ghostCollectionTimer = "ghost-collection-timer"

  case class TakeSnapshot()
  case class CollectGhosts()
  case class GhostsCollected(measurements: Map[PanelId, analysis.api.Measurement])
}

import Buffer._

class Buffer extends LoggingFSM[State, Data] with Stash with Config {

  val config = context.system.settings.config

  val monitorContact = context.actorSelection(MonitorContactSupervisor.monitorContactAbsolutePath)

  startWith(Receiving, Chunk(Map()))

  when(Receiving) {

    case Event(TakeSnapshot, chunk: Chunk) =>
      monitorContact ! Snapshot(chunk.measurements)
      stay()

    case Event(Packet(farm.api.Measurement(panelId, measuredValue, measuredDateTime), _), chunk: Chunk) =>
      val measurement = analysis.api.Measurement(panelId, measuredValue, measuredDateTime)
      stay() using (chunk.copy(measurements = (chunk.measurements + (panelId -> measurement))))

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
      stash()
      stay()

    case Event(p: Packet, _) =>
      stash()
      stay()
  }

  override def preStart() = {
    setTimer(snapshotTimer, TakeSnapshot, snapshotInterval milliseconds, repeat = true)
    setTimer(ghostCollectionTimer, CollectGhosts, ghostCollectionInterval milliseconds, repeat = true)
  }

  onTransition {

    case CollectingGhosts -> Receiving =>
      unstashAll()
  }

  initialize()
}
