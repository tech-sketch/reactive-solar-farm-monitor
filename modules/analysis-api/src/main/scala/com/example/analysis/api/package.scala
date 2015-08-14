package com.example.analysis

import org.joda.time.DateTime

package object api {

  type PanelId = String

  case class Alert(panelId: PanelId, detectedDateTime: DateTime, measuredValue: BigDecimal, measuredDateTime: DateTime)

  sealed trait ApiData

  case class Snapshot(measurements: Map[PanelId, Measurement]) extends ApiData

  case class Measurement(panelId: PanelId, measuredValue: BigDecimal, measuredDateTime: DateTime) extends ApiData

  case class LowerLimit(value: BigDecimal, detectedDateTime: DateTime) extends ApiData
}
