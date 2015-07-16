package controllers

import javax.inject.Inject

import actors.{AnalyzerProxy, EnergyApiActor}
import akka.actor.{Props, ActorSystem}
import play.api.libs.json._
import play.api.mvc._

class Application @Inject() (actorSystem: ActorSystem) extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  import play.api.Play.current

  // TODO ここに書いてて大丈夫？
  lazy val analyzerProxy = actorSystem.actorOf(Props[AnalyzerProxy])

  def energySocket = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
    EnergyApiActor.props(out, analyzerProxy)
  }
}



