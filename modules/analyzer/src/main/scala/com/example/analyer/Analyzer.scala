package com.example.analyer

import akka.actor.{Props, ActorSystem}
import com.example.analyer.actors.{AnalysisSupervisor, AnalysisSupervisor$}

object Analyzer {

  def main(args: Array[String]) {
    val system = ActorSystem("application")
    system.actorOf(Props[AnalysisSupervisor], "analysis-supervisor")
    system.awaitTermination()
  }
}
