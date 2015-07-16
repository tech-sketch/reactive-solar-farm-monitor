package com.example.testkit

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.specs2.mutable._
import org.specs2.specification.Scope

/**
 * === Example ===
 * {{{
 *   import org.specs2.mutable._
 *   import akka.actor._
 *   import com.example.testkit.Akka
 *
 *   import scala.concurrent.duration._
 *
 *  class HelloSpec extends Specification with Akka {
 *
 *    "Hello should have tests" >> {
 *      "Test" in new AkkaTestkit {
 *        within(1 second) {
 *          system.actorOf(Props(new Actor {
 *            def receive = { case x => sender ! x }
 *          })) ! "hello"
 *
 *          expectMsgType[String] must be equalTo "hello"
 *        }
 *      }
 *    }
 * }}}
 *
 */
trait Akka { self: Specification =>
  self.sequential

  abstract class AkkaTestkitWith(actorSystem: ActorSystem)
    extends TestKit(actorSystem) with ImplicitSender with BeforeAfter {

    def before = {}
    // make sure we shut down the actor system after all testsgit have run
    def after = system.shutdown()
  }

  abstract class AkkaTestkit extends AkkaTestkitWith(ActorSystem())
}
