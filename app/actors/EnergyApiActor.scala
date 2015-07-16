package actors

import akka.actor._
import akka.event.LoggingReceive
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration.DurationInt
import scala.util.Random

import com.example.analysis


object EnergyApiActor {

  case class Send()

  def props(out: ActorRef, analyzerProxy: ActorRef) = Props(new EnergyApiActor(out, analyzerProxy))
}

class EnergyApiActor(out: ActorRef, analyzerProxy: ActorRef) extends Actor with ActorLogging {

  import EnergyApiActor._
  import controllers.EnergyApi._

  import scala.concurrent.ExecutionContext.Implicits.global

  def receive = LoggingReceive {

    case analysis.api.Snapshot(measurements) =>
      val data = measurements.values map { m =>
        Measurement(m.panelId, m.measuredValue, m.measuredDateTime)
      }
      out ! Json.obj("measurement" -> data)

    case a: analysis.api.Alert =>
      val data = Json.arr(Alert(a.panelId, a.measuredValue, a.measuredDateTime, a.detectedDateTime))
      out ! Json.obj("alert" -> data)
  }

  override def preStart() = {
    analyzerProxy ! AnalyzerProxy.Subscribe
  }
}
