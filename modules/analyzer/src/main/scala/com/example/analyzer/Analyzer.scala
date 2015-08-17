package com.example.analyzer

import java.net.InetAddress

import akka.actor.{PoisonPill, Props, ActorSystem}
import akka.contrib.pattern.{ClusterSingletonProxy, ClusterSingletonManager}
import akka.event.Logging
import com.example.analyzer.actors.AnalysisSupervisor.Destroy
import com.example.analyzer.actors.{MonitorContact, AnalysisSupervisor}
import com.typesafe.config.{ConfigFactory, Config}
import org.slf4j.MDC
import scala.collection.JavaConversions._
import scala.concurrent.duration._

object Analyzer {

  def main(args: Array[String]) {
    optionParser.parse(args, CommandOption()).foreach { option =>

      MDC.put("system", "Analyzer")

      val system = ActorSystem(option.analyzerClusterName, option.asConfig())

      system.actorOf(ClusterSingletonManager.props(
        singletonProps = Props[AnalysisSupervisor],
        singletonName = "analysis-supervisor",
        terminationMessage = Destroy,
        role = Some("analyzer"),
        maxTakeOverRetries = 1,
        retryInterval = 10 seconds
      ), name = "singleton")

      system.actorOf(ClusterSingletonProxy.props(
        singletonPath = "user/singleton/analysis-supervisor",
        role = Some("analyzer")
      ), name = "analysis-proxy")

      system.actorOf(Props[MonitorContactSupervisor], name = "monitor-contact-supervisor")

      system.awaitTermination()
    }
  }

  case class CommandOption(host: Option[String] = None, port: Option[Int] = None, master: Boolean = false, seedNodes: Seq[String] = Seq()) extends com.example.Config {
    import ConfigFactory._

    val config = load()

    def asConfig(): Config = {

      val hostIp = Option(InetAddress.getLocalHost.getHostAddress)
      val tcpIp   = host orElse akkaRemoteHostname orElse hostIp getOrElse "127.0.0.1"
      val tcpPort = port orElse akkaRemotePort getOrElse(0) toString
      val seedNodeProperties = seedNodes.map { node =>
        s"""akka.cluster.seed-nodes += "akka.tcp://${analyzerClusterName}@$node""""
      } mkString("\n")
      val roleConfig = if (master) "master.conf" else "worker.conf"

      val optionConfig =
        parseMap(Map(
            "akka.remote.netty.tcp.hostname" -> tcpIp,
            "akka.remote.netty.tcp.port" -> tcpPort))
          .withFallback(parseString(seedNodeProperties))
          .withFallback(parseResources(roleConfig))

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
    opt[Unit]('m', "master") action { (_, c) =>
      c.copy(master = true)
    } text("run as master")
    opt[Seq[String]]("seed-nodes") action { (x, c) =>
      c.copy(seedNodes = c.seedNodes ++ x)
    } text("give a list of seed nodes like this: <ip>:<port> <ip>:<port>")
    checkConfig {
      case CommandOption(_, _, _, Seq()) => failure("Cluster nodes need at least one seed node")
      case _ => success
    }
  }
}