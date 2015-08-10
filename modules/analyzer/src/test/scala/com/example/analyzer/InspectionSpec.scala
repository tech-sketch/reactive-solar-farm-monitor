package com.example.analyzer

import com.example.analyzer.actors.inspection.InspectionManager.{Execute, Sample}
import com.example.analyzer.actors.inspection.Inspector.Done
import com.example.analyzer.actors.inspection.LowerLimitCalculator.LowerLimit
import com.example.analyzer.actors.inspection.SumCalculator.PartialSum
import org.specs2.mutable._

import akka.actor._
import akka.testkit._
import com.example.analyzer.actors.{AnalysisSupervisor, Buffer}
import com.example.analyzer.actors.Channel.Packet
import com.example.analyzer.actors.inspection._
import com.example.testkit._
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.junit.runner.RunWith
import com.example.farm
import com.example.analysis
import org.specs2.runner.JUnitRunner

import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class InspectionSpec extends Specification with Akka {

  "InspectionManager" >> {
    import InspectionManager._

    "Packet を 受信したら SumCalculator へ Sample を送る" >> new AkkaTestkit {
      val sumCalculatorProbe = TestProbe()
      val meanCalculatorProbe = TestProbe()
      val inspectorProbe = TestProbe()

      val inspectionManager = TestFSMRef(new InspectionManager {
        override val sumCalculatorRouter = context.actorSelection(sumCalculatorProbe.ref.path)
        override val lowerLimitCalculator = context.actorSelection(meanCalculatorProbe.ref.path)
        override val inspectorRouter = context.actorSelection(inspectorProbe.ref.path)
      })

      val nowTime = DateTime.now()
      val measurement =
        farm.api.Measurement("test1", BigDecimal(10), nowTime)

      inspectionManager ! Packet(measurement, nowTime)

      sumCalculatorProbe.expectMsgType[Sample].measurement must be_==(measurement)
    }

    "Packet を 受信したら Inspector へ Sample を送る" >> new AkkaTestkit {
      val _sumCalculator = TestProbe()
      val _meanCalculator = TestProbe()
      val _inspector = TestProbe()

      val inspectionManager = TestFSMRef(new InspectionManager {
        override val sumCalculatorRouter = context.actorSelection(_sumCalculator.ref.path)
        override val lowerLimitCalculator = context.actorSelection(_meanCalculator.ref.path)
        override val inspectorRouter = context.actorSelection(_inspector.ref.path)
      })

      val nowTime = DateTime.now()
      val measurement =
        farm.api.Measurement("test1", BigDecimal(10), nowTime)

      inspectionManager ! Packet(measurement, nowTime)

      _inspector.expectMsgType[Sample].measurement must be_==(measurement)
    }

    "定期的に Execute が SumCalculator LowerLimitCalculator Inspector に送られる" >> new AkkaTestkitWith(ActorSystem("test", config = Some(ConfigFactory.parseString(
      """
        |inspector.alert-threshold-per = 75
        |solar-farm-analyzer.inspection.execute-initial-delay = 100 milliseconds
        |solar-farm-analyzer.inspection.execute-interval = 200 milliseconds
      """.stripMargin)))) {

      val _sumCalculator = TestProbe()
      val _meanCalculator = TestProbe()
      val _inspector = TestProbe()

      val inspectionManager = TestFSMRef(new InspectionManager {
        override val sumCalculatorRouter = context.actorSelection(_sumCalculator.ref.path)
        override val lowerLimitCalculator = context.actorSelection(_meanCalculator.ref.path)
        override val inspectorRouter = context.actorSelection(_inspector.ref.path)
      })

      within(150 milliseconds) {
        _sumCalculator.expectMsgType[Execute] must be_==(Execute(0, TestProbe().ref))
        _meanCalculator.expectMsgType[Execute] must be_==(Execute(0, TestProbe().ref))
        _inspector.expectMsgType[Execute] must be_==(Execute(0, TestProbe().ref))
      }

      // 完了後に次の Execute がスケジュールされる
      inspectionManager ! Done(0)

      within(250 milliseconds) {
        _sumCalculator.expectMsgType[Execute] must be_==(Execute(0, TestProbe().ref))
        _meanCalculator.expectMsgType[Execute] must be_==(Execute(0, TestProbe().ref))
        _inspector.expectMsgType[Execute] must be_==(Execute(0, TestProbe().ref))
      }
    }.pendingUntilFixed("は未修正")

    "Inspecting のときに全サンプル分の Done を受信すると Collecting になる" >> new AkkaTestkit {

      val inspectionManager = TestFSMRef(new InspectionManager {
        override val sumCalculatorRouter = context.actorSelection(TestProbe().ref.path)
        override val lowerLimitCalculator = context.actorSelection(TestProbe().ref.path)
        override val inspectorRouter = context.actorSelection(TestProbe().ref.path)
      })
      inspectionManager.setState(Inspecting, Inspection(0, 10, TestProbe().ref))

      inspectionManager ! Done(1)
      inspectionManager ! Done(3)
      inspectionManager ! Done(2)
      inspectionManager ! Done(4)

      inspectionManager.stateName must be_==(Collecting)
    }
  }

  "SumCalculator" >> {
    "Execute を受信したときにサンプルの合計値を LowerLimitCalculator に送る" >> new AkkaTestkit {
      val meanCalculator = TestProbe()

      val sumCalculator =
        system.actorOf(SumCalculator.props(meanCalculator.ref))

      sumCalculator ! Sample(farm.api.Measurement("test1", BigDecimal(7), DateTime.now()))
      sumCalculator ! Sample(farm.api.Measurement("test2", BigDecimal(11), DateTime.now()))
      sumCalculator ! Sample(farm.api.Measurement("test3", BigDecimal(13), DateTime.now()))

      sumCalculator ! Execute(3, TestProbe().ref)

      val PartialSum(sum, population) = meanCalculator.expectMsgType[PartialSum]

      population must be_==(3)
      sum must be_==(BigDecimal(31))
    }
  }

  "LowerLimitCalculator" >> {
    "PartialSum が Execute で指定されたサンプル数分集まったら LowerLimit を Inspector に送る" >> new AkkaTestkit {
      val inspector = TestProbe()

      val meanCalculator =
        system.actorOf(LowerLimitCalculator.props(inspector.ref))

      meanCalculator ! Execute(3, TestProbe().ref)
      meanCalculator ! PartialSum(BigDecimal(7), 1)
      meanCalculator ! PartialSum(BigDecimal(11), 2)

      val LowerLimit(mean, population) = inspector.expectMsgType[LowerLimit]

      population must be_==(3)
      mean must be_==(BigDecimal(6))
    }
  }

  "Inspector" >> {
    "受信した LowerLimit から設定した下限値より下回る Sample がある場合 Alert を receiver に送る" >> new AkkaTestkitWith(ActorSystem("test", config = Some(ConfigFactory.parseString(
      """
        |solar-farm-analyzer.inspector.alert-threshold-per = 72
      """.stripMargin)))) {

      import analysis.api.Alert

      val receiver = TestProbe()

      val inspector =
        system.actorOf(Props[Inspector])

      val nowTime = DateTime.now()

      inspector ! Sample(farm.api.Measurement("test1", BigDecimal(15), nowTime))
      inspector ! Sample(farm.api.Measurement("test2", BigDecimal(17), nowTime))
      inspector ! Sample(farm.api.Measurement("test3", BigDecimal(10), nowTime))

      inspector ! Execute(3, TestProbe().ref)

      inspector ! LowerLimit(BigDecimal(14), 3)

      val alert = receiver.expectMsgType[Alert]

      alert.panelId must be_==("test3")
      alert.measuredDateTime must be_==(nowTime)
      alert.measuredValue must be_==(BigDecimal(10))
    }.pendingUntilFixed("は未修正")
  }

  "SumCalculator -> LowerLimitCalculator" >> {
    "LowerLimitCalculator へ Execute が届く前に SumCalculator が PartialSum を送っても LowerLimit が計算できる" >> new AkkaTestkit {

      val inspector = TestProbe()

      val meanCalculator =
        system.actorOf(LowerLimitCalculator.props(inspector.ref), "mean")
      val sumCalculator =
        system.actorOf(SumCalculator.props(meanCalculator), "sum")

      import farm.api.Measurement
      sumCalculator ! Sample(Measurement("test1", BigDecimal(15), DateTime.now()))
      sumCalculator ! Sample(Measurement("test1", BigDecimal(12), DateTime.now()))
      sumCalculator ! Execute(2, TestProbe().ref)

      Thread.sleep(100)

      meanCalculator ! Execute(2, TestProbe().ref)

      inspector.expectMsgType[LowerLimit] must not beNull
    }
  }

  "LowerLimitCalculator -> Inspector" >> {
    "Inspector へ Execute が届く前に LowerLimitCalculator が LowerLimit を送っても Alert を発報できる" >> new AkkaTestkitWith(ActorSystem("test", config = Some(ConfigFactory.parseString(
      """
        |solar-farm-analyzer.inspector.alert-threshold-per = 90
      """.stripMargin)))) {
      val receiver = TestProbe()

      val inspector =
        system.actorOf(Props[Inspector])
      val meanCalculator =
        system.actorOf(LowerLimitCalculator.props(inspector))

      import farm.api.Measurement
      inspector ! Sample(Measurement("test1", BigDecimal(7), DateTime.now()))
      inspector ! Sample(Measurement("test2", BigDecimal(6), DateTime.now()))
      inspector ! Sample(Measurement("test3", BigDecimal(5), DateTime.now()))

      meanCalculator ! Execute(3, TestProbe().ref)
      meanCalculator ! PartialSum(BigDecimal(7), 1)
      meanCalculator ! PartialSum(BigDecimal(11), 2)

      Thread.sleep(100)

      inspector ! Execute(3, TestProbe().ref)

      receiver.expectMsgType[analysis.api.Alert] must not beNull
    }.pendingUntilFixed("は未修正")
  }
}
