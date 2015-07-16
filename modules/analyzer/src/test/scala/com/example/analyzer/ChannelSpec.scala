package com.example.analyzer

import akka.testkit._
import com.example.analyer.actors.Channel
import com.example.analyer.actors.Channel._
import com.example.testkit.Akka
import net.sigusr.mqtt.api._
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ChannelSpec extends Specification with Akka {

  "MeasurementBuffer" >> {

    "開始時に Manager を作って Connect を送る" >> new AkkaTestkit {

      val managerProbe = TestProbe()
      val measurementBuffer = TestProbe()

      TestFSMRef(new Channel(measurementBuffer.ref) {
        override def createMqttManager = managerProbe.ref
      })

      managerProbe.expectMsgClass(classOf[Connect])
    }

    "コネクションが開くと OPEN になる" >> new AkkaTestkit {

      val managerProbe = TestProbe()
      val measurementBuffer = TestProbe()

      val channel = TestFSMRef(new Channel(measurementBuffer.ref) {
        override def createMqttManager = managerProbe.ref
      })

      channel ! Connected

      channel.stateName must be equalTo Open
    }

    "コネクションが開いた時に Subscribe する" >> new AkkaTestkit {

      val managerProbe = TestProbe()
      val measurementBuffer = TestProbe()

      val channel = TestFSMRef(new Channel(measurementBuffer.ref) {
        override def createMqttManager = managerProbe.ref
      })

      managerProbe.expectMsgType[Connect] must not beNull

      channel ! Connected

      managerProbe.expectMsgType[Subscribe] must not beNull
    }

    "コネクションが開けなかったら ChannelCannotOpenException" >> new AkkaTestkit {

      val managerProbe = TestProbe()
      val measurementBuffer = TestProbe()

      val channel = TestFSMRef(new Channel(measurementBuffer.ref) {
        override def createMqttManager = managerProbe.ref
      })

      channel.receive(ConnectionFailure(ServerNotResponding)) must throwA[ChannelCannotOpenException]
    }

    "コネクションでエラーが起きたら ChannelDisabledException" >> new AkkaTestkit {

      val managerProbe = TestProbe()
      val measurementBuffer = TestProbe()

      val channel = TestFSMRef(new Channel(measurementBuffer.ref) {
        override def createMqttManager = managerProbe.ref
      })

      channel.receive(Error(NotConnected)) must throwA[ChannelDisabledException]
    }

    "コネクションが閉じられたら ChannelDisabledException" >> new AkkaTestkit {

      val managerProbe = TestProbe()
      val measurementBuffer = TestProbe()

      val channel = TestFSMRef(new Channel(measurementBuffer.ref) {
        override def createMqttManager = managerProbe.ref
      })

      channel.receive(Disconnected) must throwA[ChannelDisabledException]
    }


    "コネクションが OPEN の状態でコネクションが閉じられたら ChannelDisabledException" >> new AkkaTestkit {

      val managerProbe = TestProbe()
      val measurementBuffer = TestProbe()

      val channel = TestFSMRef(new Channel(measurementBuffer.ref) {
        override def createMqttManager = managerProbe.ref
      })

      channel.setState(Open)
      channel.receive(Disconnected) must throwA[ChannelDisabledException]
    }

    "コネクションが OPEN の状態でコネクションのエラーが起きたら ChannelDisabledException" >> new AkkaTestkit {

      val managerProbe = TestProbe()
      val measurementBuffer = TestProbe()

      val channel = TestFSMRef(new Channel(measurementBuffer.ref) {
        override def createMqttManager = managerProbe.ref
      })

      channel.setState(Open)
      channel.receive(Error(NotConnected)) must throwA[ChannelDisabledException]
    }
  }
}
