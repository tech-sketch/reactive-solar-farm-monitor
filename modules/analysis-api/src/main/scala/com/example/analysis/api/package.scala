package com.example.analysis

import org.joda.time.DateTime

package object api {

  type PanelId = String

  case class Connect()

  case class Alert(panelId: PanelId, detectedDateTime: DateTime, measuredValue: BigDecimal, measuredDateTime: DateTime)

  case class Snapshot(measurements: Map[PanelId, Measurement])

  case class Measurement(panelId: PanelId, measuredValue: BigDecimal, measuredDateTime: DateTime)
}
