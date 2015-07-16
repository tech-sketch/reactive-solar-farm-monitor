package com.example.simulator

import akka.actor._
import com.example.simulator.actor.SimulationSupervisor

/**
 * Created by tie211059 on 2015/06/17.
 */
object SolarFarmSimulator {

  def main(args: Array[String]) {
    val system = ActorSystem("simulator")
    system.actorOf(Props[SimulationSupervisor], "simulator")
    system.awaitTermination()
  }
}
