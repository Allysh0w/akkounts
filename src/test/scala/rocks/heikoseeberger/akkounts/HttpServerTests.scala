/*
 * Copyright 2020 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rocks.heikoseeberger.akkounts

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.TestEntityRef
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes.{ BadRequest, Created }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.DurationInt

final class HttpServerTests extends AnyWordSpec with Matchers with ScalatestRouteTest {
  import HttpServer._

  implicit private val typedSystem: ActorSystem[_] = system.toTyped

  "The route" should {
    "respond to POST /42/deposit with BadRequest" in {
      val account =
        system.spawnAnonymous(Behaviors.receiveMessage[Account.Command] {
          case Account.Deposit(amount, replyTo) =>
            replyTo ! Account.InvalidAmount(amount)
            Behaviors.stopped

          case _ =>
            Behaviors.stopped
        })
      def accountFor(entityId: String) = new TestEntityRef(account)

      val request =
        Post("/42/deposit").withEntity(`application/json`, """{"amount":0}""")

      request ~> route(accountFor, 1.second) ~> check {
        status shouldBe BadRequest
        responseAs[String] should include("Invalid amount 0")
      }
    }

    "respond to POST /42/deposit with Created" in {
      val account =
        system.spawnAnonymous(Behaviors.receiveMessage[Account.Command] {
          case Account.Deposit(amount, replyTo) =>
            replyTo ! Account.Deposited(amount)
            Behaviors.stopped

          case _ =>
            Behaviors.stopped
        })
      def accountFor(entityId: String) = new TestEntityRef(account)

      val request =
        Post("/42/deposit").withEntity(`application/json`, """{"amount":42}""")

      request ~> route(accountFor, 1.second) ~> check {
        status shouldBe Created
        responseAs[String] should include("Deposited amount 42")
      }
    }

    "respond to POST /42/withdraw with BadRequest for an invalid amount" in {
      val account =
        system.spawnAnonymous(Behaviors.receiveMessage[Account.Command] {
          case Account.Withdraw(amount, replyTo) =>
            replyTo ! Account.InvalidAmount(amount)
            Behaviors.stopped

          case _ =>
            Behaviors.stopped
        })
      def accountFor(entityId: String) = new TestEntityRef(account)

      val request =
        Post("/42/withdraw").withEntity(`application/json`, """{"amount":0}""")

      request ~> route(accountFor, 1.second) ~> check {
        status shouldBe BadRequest
        responseAs[String] should include("Invalid amount 0")
      }
    }

    "respond to POST /42/withdraw with BadRequest for insufficient balance" in {
      val account =
        system.spawnAnonymous(Behaviors.receiveMessage[Account.Command] {
          case Account.Withdraw(amount, replyTo) =>
            replyTo ! Account.InsufficientBalance(amount, 0)
            Behaviors.stopped

          case _ =>
            Behaviors.stopped
        })
      def accountFor(entityId: String) = new TestEntityRef(account)

      val request =
        Post("/42/withdraw").withEntity(`application/json`, """{"amount":42}""")

      request ~> route(accountFor, 1.second) ~> check {
        status shouldBe BadRequest
        responseAs[String] should include("Insufficient balance 0 for amount 42")
      }
    }

    "respond to POST /42/withdraw with Created" in {
      val account =
        system.spawnAnonymous(Behaviors.receiveMessage[Account.Command] {
          case Account.Withdraw(amount, replyTo) =>
            replyTo ! Account.Withdrawn(amount)
            Behaviors.stopped

          case _ =>
            Behaviors.stopped
        })
      def accountFor(entityId: String) = new TestEntityRef(account)

      val request =
        Post("/42/withdraw").withEntity(`application/json`, """{"amount":42}""")

      request ~> route(accountFor, 1.second) ~> check {
        status shouldBe Created
        responseAs[String] should include("Withdrawn amount 42")
      }
    }
  }
}
