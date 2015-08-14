package com.example.analyzer.actors

import akka.actor.{ActorPath, RootActorPath, Address, LoggingFSM}
import akka.cluster.{MemberStatus, Cluster}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp, MemberEvent}
import com.example.Config
import com.example.analysis

object MonitorContact {

  sealed trait State
  case object NotContacted extends State
  case object Contacted extends State

  sealed trait Data
  case class Contacts(monitorNodeAddresses: Set[Address]) extends Data {
    def foreach (f: ActorPath => Unit) = {
      monitorNodeAddresses.foreach { address =>
        f(RootActorPath(address) / "user" / "monitor-supervisor" / "analysis-broker")
      }
    }
  }

  val emptyContact = Contacts(Set())

  private case class ContactChanged()
}

import MonitorContact._

class MonitorContact extends LoggingFSM[State, Data] with Config {

  val config = context.system.settings.config

  Cluster(context.system).subscribe(self, classOf[MemberEvent])

  startWith(NotContacted, emptyContact)

  when(NotContacted) {

    case Event(alert: analysis.api.Alert, _) =>
      stay()

    case Event(snapshot: analysis.api.Snapshot, _) =>
      stay()

    case Event(lowerLimit: analysis.api.LowerLimit, _) =>
      stay()
  }

  when(Contacted) {
    case Event(alert: analysis.api.Alert, c: Contacts) =>
      c.foreach { path =>
        context.actorSelection(path) forward alert
      }
      stay()


    case Event(snapshot: analysis.api.Snapshot, c: Contacts) =>
      c.foreach { path =>
        context.actorSelection(path) forward snapshot
      }
      stay()


    case Event(lowerLimit: analysis.api.LowerLimit, c: Contacts) =>
      c.foreach { path =>
        context.actorSelection(path) forward lowerLimit
      }
      stay()
  }

  whenUnhandled {

    /* Cluster Events */

    case Event(current: CurrentClusterState, c: Contacts) =>
      val currentNodeAddresses =
        current.members.collect {
          case m if m.hasRole(monitorClusterRole) && m.status == MemberStatus.Up => m.address
        }
      self ! ContactChanged
      stay() using c.copy(monitorNodeAddresses = c.monitorNodeAddresses ++ currentNodeAddresses)

    case Event(MemberUp(m), c: Contacts) if m.hasRole(monitorClusterRole) =>
      self ! ContactChanged
      stay() using c.copy(monitorNodeAddresses = c.monitorNodeAddresses + m.address)

    case Event(MemberRemoved(m, _), c: Contacts) if m.hasRole(monitorClusterRole) =>
      self ! ContactChanged
      stay() using c.copy(monitorNodeAddresses = c.monitorNodeAddresses - m.address)

    case Event(_: MemberEvent, _) =>
      stay()

    case Event(ContactChanged, c: Contacts) =>
      if (c.monitorNodeAddresses.isEmpty) {
        log.info("Cannot Contact Monitor: " + c.monitorNodeAddresses)
        goto(NotContacted)
      } else {
        log.info("Contacted Monitor: " + c.monitorNodeAddresses)
        goto(Contacted)
      }
  }


  initialize()
}
