package com.example.simulator

import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

import akka.testkit._
import com.example.farm.api._
import com.example.simulator.actor.MeasurementChannelUpstream
import com.example.simulator.actor.MeasurementChannelUpstream._
import com.example.testkit.Akka
import net.sigusr.mqtt.api._
import org.joda.time.DateTime
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class MeasurementChannelSpec extends Specification with Akka {

  "MeasurementChannelUpstream" >> {

    "開始時に Manager を作って Connect を送る" >> new AkkaTestkit {

      val managerProbe = TestProbe()

      TestFSMRef(new MeasurementChannelUpstream {
        override def createMqttManager = managerProbe.ref
      })

      managerProbe.expectMsgClass(classOf[Connect])
    }

    "コネクションが開くと OPEN になる" >> new AkkaTestkit {

      val managerProbe = TestProbe()

      val channel = TestFSMRef(new MeasurementChannelUpstream {
        override def createMqttManager = managerProbe.ref
      })

      channel ! Connected

      channel.stateName must be equalTo Open
    }

    "コネクションが開けなかったら ChannelCannotOpenException" >> new AkkaTestkit {
      val managerProbe = TestProbe()

      val channel = TestFSMRef(new MeasurementChannelUpstream {
        override def createMqttManager = managerProbe.ref
      })

      channel.receive(ConnectionFailure(ServerNotResponding)) must throwA[ChannelCannotOpenException]
    }

    "コネクションでエラーが起きたら ChannelDisabledException" >> new AkkaTestkit {
      val managerProbe = TestProbe()

      val channel = TestFSMRef(new MeasurementChannelUpstream {
        override def createMqttManager = managerProbe.ref
      })

      channel.receive(Error(NotConnected)) must throwA[ChannelDisabledException]
    }

    "コネクションが閉じられたら ChannelDisabledException" >> new AkkaTestkit {
      val managerProbe = TestProbe()

      val channel = TestFSMRef(new MeasurementChannelUpstream {
        override def createMqttManager = managerProbe.ref
      })

      channel.receive(Disconnected) must throwA[ChannelDisabledException]
    }

    "コネクションが OPEN の状態で測定データを受けたら測定データを Publish する" >> new AkkaTestkit {

      val managerProbe = TestProbe()

      val channel = TestFSMRef(new MeasurementChannelUpstream {
        override def createMqttManager = managerProbe.ref
      })
      managerProbe.expectMsgType[Connect]

      channel ! Connected

      val measurement = Measurement("test", BigDecimal(0), DateTime.now())

      channel ! measurement

      val published = managerProbe.expectMsgType[Publish]
      decode(published.payload) must beEqualTo(measurement)
    }

    "コネクションが OPEN になっていない時に受けたメッセージは OPEN になってから測定データを Publish する" >> new AkkaTestkit {

      val managerProbe = TestProbe()

      val channel = TestFSMRef(new MeasurementChannelUpstream {
        override def createMqttManager = managerProbe.ref
      })
      managerProbe.expectMsgType[Connect]

      val measurement = Measurement("test", BigDecimal(0), DateTime.now())

      channel ! measurement

      channel ! Connected

      val published = managerProbe.expectMsgType[Publish]
      decode(published.payload) must beEqualTo(measurement)
    }

    "コネクションが OPEN の状態でコネクションが閉じられたら ChannelDisabledException" >> new AkkaTestkit {

      val managerProbe = TestProbe()

      val channel = TestFSMRef(new MeasurementChannelUpstream {
        override def createMqttManager = managerProbe.ref
      })

      channel.setState(Open)
      channel.receive(Disconnected) must throwA[ChannelDisabledException]
    }

    "コネクションが OPEN の状態でコネクションのエラーが起きたら ChannelDisabledException" >> new AkkaTestkit {

      val managerProbe = TestProbe()

      val channel = TestFSMRef(new MeasurementChannelUpstream {
        override def createMqttManager = managerProbe.ref
      })

      channel.setState(Open)
      channel.receive(Error(NotConnected)) must throwA[ChannelDisabledException]
    }
  }
}
