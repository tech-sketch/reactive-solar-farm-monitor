package com.example.simulator


import akka.actor._
import akka.testkit._
import com.example.farm.api.Measurement
import com.example.simulator.actor.{SolarPanelBreaker, SolarPanel}
import com.example.simulator.actor.SolarPanelBreaker.Trouble
import com.example.testkit.Akka
import com.example.testkit.Contexts._
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class SolarPanelSpec extends Specification with Akka {

  "SolarPanel" >> {

    "solar-farm-simulator.panel.measure-interval に設定した時間ごとに測定値を push する" >> new AkkaTestkitWith(
      ActorSystem("test", ConfigFactory.parseString(
        """
          |solar-farm-simulator.panel.measure-initial-delay = 150 milliseconds
          |solar-farm-simulator.panel.measure-interval = 100 milliseconds
        """.stripMargin).toOption)) {

      val channelProbe = TestProbe()
      system.actorOf(SolarPanel.props(channelProbe.ref))

      channelProbe.receiveWhile(1 seconds) { case m: Measurement => m } must have size(9)
    }

    "ランダムな測定値を push する" >> new AkkaTestkit {
      val channelProbe = TestProbe()
      system.actorOf(SolarPanel.props(channelProbe.ref))

      val receivedValues =
        channelProbe.receiveWhile(500 milliseconds, messages = 3) { case Measurement(_, value, _) => value }

      receivedValues must have size be_>=(3)
      receivedValues(0) must not equalTo receivedValues(1)
      receivedValues(0) must not equalTo receivedValues(2)
      receivedValues(1) must not equalTo receivedValues(2)
    }

    "現在時刻を push する" >> new AkkaTestkit {

      val systemDateTime = new DateTime(2015, 6, 23, 16, 20)

      withFixedSystemDateTime(systemDateTime) {
        val channelProbe = TestProbe()
        system.actorOf(SolarPanel.props(channelProbe.ref))

        val receivedValues =
          channelProbe.receiveWhile(500 milliseconds, messages = 2) { case Measurement(_, _, time) => time }

        receivedValues must have size be_>(0)
        receivedValues must contain(allOf(systemDateTime))
      }
    }

    "Trouble を受けると故障し、solar-farm-simulator.panel.trouble.attenuation-factor に設定した値だけ測定値が減衰する" >> new AkkaTestkitWith(
      ActorSystem("test", ConfigFactory.parseString(
        """
          |solar-farm-simulator.panel.trouble.attenuation-factor = 0.9
          |
          |solar-farm-simulator.panel.base-measured-value = 200.0
          |solar-farm-simulator.panel.measured-value-amplitude = 0.0
          |solar-farm-simulator.panel.measure-initial-delay = 100 milliseconds
          |solar-farm-simulator.panel.measure-interval = 100 milliseconds
        """.stripMargin).toOption)) {

      val channelProbe = TestProbe()
      val panel = system.actorOf(SolarPanel.props(channelProbe.ref))

      within(200 milliseconds) {
        channelProbe.expectMsgType[Measurement].measuredValue must be_==(BigDecimal(200.0))
      }

      panel ! Trouble

      within(200 milliseconds) {
        channelProbe.expectMsgType[Measurement].measuredValue must be_==(BigDecimal(200.0) * (1 - 0.9))
      }
    }

    "故障しても solar-farm-simulator.panel.trouble.repair-delay に設定した時間後に修復される" >> new AkkaTestkitWith(
      ActorSystem("test", ConfigFactory.parseString(
        """
          |solar-farm-simulator.panel.trouble.attenuation-factor = 0.9
          |solar-farm-simulator.panel.trouble.repair-delay = 100 milliseconds
          |
          |solar-farm-simulator.panel.base-measured-value = 200.0
          |solar-farm-simulator.panel.measured-value-amplitude = 0.0
          |solar-farm-simulator.panel.measure-initial-delay = 100 milliseconds
          |solar-farm-simulator.panel.measure-interval = 100 milliseconds
        """.stripMargin).toOption)) {

      val channelProbe = TestProbe()
      val panel = system.actorOf(SolarPanel.props(channelProbe.ref))

      within(200 milliseconds) {
        channelProbe.expectMsgType[Measurement].measuredValue must be_==(BigDecimal(200.0))
      }

      panel ! Trouble

      within(300 milliseconds) {
        channelProbe.expectMsgType[Measurement].measuredValue must be_==(BigDecimal(200.0) * (1 - 0.9))
        channelProbe.expectMsgType[Measurement].measuredValue must be_==(BigDecimal(200.0))
      }
    }
  }

  "SolarPanelBreaker" >> {

    "solar-farm-simulator.breaker.break-interval に設定した時間間隔でパネルへ Trouble を送る" >> new AkkaTestkitWith(
      ActorSystem("test", ConfigFactory.parseString(
        """
          |solar-farm-simulator.breaker.break-initial-delay = 100 milliseconds
          |solar-farm-simulator.breaker.break-interval = 100 milliseconds
        """.stripMargin).toOption)) {

      val panelProbe = TestProbe()
      system.actorOf(SolarPanelBreaker.props(panelProbe.ref))

      panelProbe.receiveWhile(1 second, messages = 2) { case Trouble => true } must have size(2)
    }
  }
}
