package controllers

import javax.inject.Inject

import actors.{AnalyzerProxySupervisor, AnalyzerProxy, EnergyApiActor}
import akka.actor.{Props, ActorSystem}
import play.api.libs.json._
import play.api.mvc._

class Application @Inject() (actorSystem: ActorSystem) extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  import play.api.Play.current

  lazy val supervisor = actorSystem.actorOf(Props[AnalyzerProxySupervisor], "supervisor")

  def energySocket = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
    EnergyApiActor.props(out, supervisor)
  }
}



