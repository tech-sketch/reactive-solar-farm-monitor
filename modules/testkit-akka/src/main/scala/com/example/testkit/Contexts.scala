package com.example.testkit

import org.joda.time.{DateTimeUtils, DateTime}
import org.specs2.mutable.BeforeAfter


object Contexts {

  case class withFixedSystemDateTime(dateTime: DateTime) extends BeforeAfter {
    def before = DateTimeUtils.setCurrentMillisFixed(dateTime.getMillis)
    def after = DateTimeUtils.setCurrentMillisSystem()
  }
}
