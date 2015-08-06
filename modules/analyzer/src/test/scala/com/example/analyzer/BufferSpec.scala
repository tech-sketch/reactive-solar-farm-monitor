package com.example.analyzer

import akka.actor.ActorSystem
import akka.testkit._
import com.example.analyer.actors.Buffer
import com.example.analyer.actors.Channel._
import com.example.testkit.Akka
import com.typesafe.config.ConfigFactory
import com.example.farm
import com.example.analysis
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class BufferSpec extends Specification with Akka {

  "Buffer" >> {
    import Buffer._

    "Packetを一定期間収集してSnapshotをレシーバーに送る" >> new AkkaTestkitWith(ActorSystem("test", config = Some(ConfigFactory.parseString(
      """
        |solar-farm-analyzer.buffer.snapshot-initial-delay = 500 milliseconds
        |solar-farm-analyzer.buffer.snapshot-interval = 500 milliseconds
      """.stripMargin)))) {

      val receiver = TestProbe()

      val buffer = TestFSMRef(new Buffer)
      val nowTime = DateTime.now()

      val measurements = Seq(
        farm.api.Measurement("panel1", BigDecimal(10), nowTime),
        farm.api.Measurement("panel2", BigDecimal(11), nowTime),
        farm.api.Measurement("panel3", BigDecimal(12), nowTime)
      )

      within(500 milliseconds) {
        buffer.setState(Receiving)
        buffer ! Packet(measurements(0), nowTime)
        buffer ! Packet(measurements(1), nowTime)
        buffer ! Packet(measurements(2), nowTime)


        val expectMeasurements =
          measurements.map { m => m.panelId -> analysis.api.Measurement(m.panelId, m.measuredValue, m.measuredDateTime) }.toMap

        receiver.expectMsg(analysis.api.Snapshot(expectMeasurements))
      }
    }.pendingUntilFixed("は未修正")

    "古い測定値は消す(GhostCollection)" >> new AkkaTestkitWith(ActorSystem("test", config = Some(ConfigFactory.parseString(
      """
        |solar-farm-analyzer.buffer.snapshot-initial-delay = 400 milliseconds
        |solar-farm-analyzer.buffer.snapshot-interval = 400 milliseconds
        |solar-farm-analyzer.buffer.ghost-collection-initial-delay = 100 milliseconds
        |solar-farm-analyzer.buffer.ghost-collection-interval = 500 milliseconds
        |solar-farm-analyzer.buffer.ghost-life-span = 10 minutes
      """.stripMargin)))) {

      val receiver = TestProbe()

      val buffer = TestFSMRef(new Buffer)

      val justNow = DateTime.now()
      val tenMinutesBefore = DateTime.now().minus((10 minutes).toMillis)

      val measurements = Seq(
        farm.api.Measurement("panel1", BigDecimal(10), tenMinutesBefore),
        farm.api.Measurement("panel2", BigDecimal(11), tenMinutesBefore),
        farm.api.Measurement("panel3", BigDecimal(12), justNow)
      )

      within(500 milliseconds) {
        buffer.setState(Receiving)
        buffer ! Packet(measurements(0), tenMinutesBefore)
        buffer ! Packet(measurements(1), tenMinutesBefore)
        buffer ! Packet(measurements(2), justNow)

        val expectMeasurements =
          measurements
            .filter { m => m == measurements(2) }
            .map { m => m.panelId -> analysis.api.Measurement(m.panelId, m.measuredValue, m.measuredDateTime) }.toMap

        receiver.expectMsg(analysis.api.Snapshot(expectMeasurements))
      }
    }.pendingUntilFixed("は未修正")
  }
}
