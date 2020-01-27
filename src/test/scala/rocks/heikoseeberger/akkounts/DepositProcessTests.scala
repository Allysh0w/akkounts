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
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.TestEntityRef
import akka.stream.scaladsl.{ Sink, Source, SourceWithContext }
import io.moia.streamee.Respondee
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import scala.concurrent.duration.DurationInt

final class DepositProcessTests extends AsyncWordSpec with Matchers with ClassicSystemSuite {
  import DepositProcess._

  private val respondee =
    system.spawnAnonymous(Behaviors.empty[Respondee.Response[Either[Error, Deposited]]])

  private val config = Config(1.second, 42)

  "DepositProcess" should {
    "respond to Deposit with an invalid amount with Error.InvalidAmount" in {
      val mockAccount               = system.spawnAnonymous(Behaviors.empty[Account.Command])
      def account(entityId: String) = new TestEntityRef(mockAccount)(system.toTyped)

      val requests = Source.single(Deposit("id", 0))
      SourceWithContext
        .fromTuples(requests.map(_ -> respondee))
        .via(DepositProcess(config, account))
        .runWith(Sink.head)
        .map(_._1 shouldBe Left(Error.InvalidAmount(0)))
    }

    "respond to Deposit with a valid amount with Error.InvalidAmount if Account replys so" in {
      val mockAccount =
        system.spawnAnonymous(Behaviors.receiveMessagePartial[Account.Command] {
          case Account.Deposit(amount, replyTo) =>
            replyTo ! Account.InvalidAmount(amount)
            Behaviors.stopped
        })
      def account(entityId: String) = new TestEntityRef(mockAccount)(system.toTyped)

      val requests = Source.single(Deposit("id", 42))
      SourceWithContext
        .fromTuples(requests.map(_ -> respondee))
        .via(DepositProcess(config, account))
        .runWith(Sink.head)
        .map(_._1 shouldBe Left(Error.InvalidAmount(42)))
    }

    "respond to Deposit with a valid amount with Deposited if Account replys so" in {
      val mockAccount =
        system.spawnAnonymous(Behaviors.receiveMessagePartial[Account.Command] {
          case Account.Deposit(amount, replyTo) =>
            replyTo ! Account.Deposited(amount)
            Behaviors.stopped
        })
      def account(entityId: String) = new TestEntityRef(mockAccount)(system.toTyped)

      val requests = Source.single(Deposit("id", 1))
      SourceWithContext
        .fromTuples(requests.map(_ -> respondee))
        .via(DepositProcess(config, account))
        .runWith(Sink.head)
        .map(_._1 shouldBe Right(Deposited(1)))
    }
  }
}
