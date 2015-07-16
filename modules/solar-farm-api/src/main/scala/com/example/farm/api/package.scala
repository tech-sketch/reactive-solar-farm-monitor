package com.example.farm

import org.joda.time.DateTime
import play.api.libs.json.Json

package object api {

  type PanelId = String

  case class Measurement(panelId: PanelId, measuredValue: BigDecimal, measuredDateTime: DateTime)
  implicit val pushFormat = Json.format[Measurement]

  val encoding = "UTF-8"

  def encode(message: Measurement) =
    Json.toJson(message).toString.getBytes(encoding).to[Vector]

  def decode(payload: Vector[Byte]) =
    Json.parse(new String(payload.to[Array], encoding)).as[Measurement]
}
