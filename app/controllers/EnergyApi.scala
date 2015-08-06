package controllers

import org.joda.time.DateTime
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter

/**
 * Created by tie211059 on 2015/06/16.
 */
object EnergyApi {

  case class Measurement(panelId: String, watt: BigDecimal, measuredTime: DateTime)
  implicit val measurementFormat = Json.format[Measurement]
  implicit val measurementFrameFormatter = FrameFormatter.jsonFrame[Measurement]

  case class Alert(panelId: String, watt: BigDecimal, measuredTime: DateTime, detectedTime: DateTime)
  implicit val alertFormat = Json.format[Alert]
  implicit val alertFrameFormatter = FrameFormatter.jsonFrame[Alert]

  case class LowerLimit(value: BigDecimal, detectedDateTime: DateTime)
  implicit val lowerLimitFormat = Json.format[LowerLimit]
  implicit val lowerLimitFrameFormatter = FrameFormatter.jsonFrame[LowerLimit]

  case class Error(message: String)
  implicit val errorFormat = Json.format[Error]
  implicit val errorFrameFormatter = FrameFormatter.jsonFrame[Error]
}
