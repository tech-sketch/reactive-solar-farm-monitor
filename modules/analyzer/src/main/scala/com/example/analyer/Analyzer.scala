package com.example.analyer

import java.net.InetAddress

import akka.actor.{Props, ActorSystem}
import akka.event.Logging
import com.example.analyer.actors.{AnalysisSupervisor}
import com.typesafe.config.{ConfigFactory, Config}
import scala.collection.JavaConversions._

object Analyzer {

  def main(args: Array[String]) {
    optionParser.parse(args, CommandOption()).foreach { option =>

      val system = ActorSystem(option.analyzerClusterName, option.asConfig())

      system.actorOf(Props[AnalysisSupervisor], "analysis-supervisor")

      system.awaitTermination()
    }
  }

  case class CommandOption(host: Option[String] = None, port: Option[Int] = None, seedNodes: Seq[String] = Seq()) extends com.example.Config {
    import ConfigFactory._

    val config = load()

    def asConfig(): Config = {

      val hostIp = Option(InetAddress.getLocalHost.getHostAddress)
      val tcpIp   = host orElse akkaRemoteHostname orElse hostIp getOrElse "127.0.0.1"
      val tcpPort = port orElse akkaRemotePort getOrElse(0) toString
      val seedNodeProperties = seedNodes.map { node =>
        s"""akka.cluster.seed-nodes += "akka.tcp://${analyzerClusterName}@$node""""
      } mkString("\n")

      val optionConfig =
        parseMap(Map(
            "akka.remote.netty.tcp.hostname" -> tcpIp,
            "akka.remote.netty.tcp.port" -> tcpPort))
          .withFallback(parseString(seedNodeProperties))

      println(optionConfig)

      optionConfig.withFallback(config).resolve
    }
  }

  val optionParser = new scopt.OptionParser[CommandOption]("reactive-solar-farm-monitor-analyzer") {
    opt[String]('h', "host") action { (x, c) =>
      c.copy(host = Some(x))
    } text("hostname or ip address")
    opt[Int]('p', "port") action { (x, c) =>
      c.copy(port = Some(x))
    } validate { x =>
      if (0 < x && x < 65535) success else failure("Value <port> must be between 0 and 65535")
    } text("port to listen messages for actors (default: 0)")
    opt[Seq[String]]("seed-nodes") action { (x, c) =>
      c.copy(seedNodes = c.seedNodes ++ x)
    } text("give a list of seed nodes like this: <ip>:<port> <ip>:<port>")
    checkConfig {
      case CommandOption(_, _, Seq()) => failure("Cluster nodes need at least one seed node")
      case _ => success
    }
  }
}