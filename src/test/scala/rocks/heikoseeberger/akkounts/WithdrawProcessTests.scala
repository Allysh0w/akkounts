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

final class WithdrawProcessTests extends AsyncWordSpec with Matchers with ClassicSystemSuite {
  import WithdrawProcess._

  private val respondee =
    system.spawnAnonymous(Behaviors.empty[Respondee.Response[Either[Error, Withdrawn]]])

  private val config = Config(1.second, 42)

  "WithdrawProcess" should {
    "respond to Withdraw with an invalid amount with Error.InvalidAmount" in {
      val mockAccount               = system.spawnAnonymous(Behaviors.empty[Account.Command])
      def account(entityId: String) = new TestEntityRef(mockAccount)(system.toTyped)

      val requests = Source.single(Withdraw("id", 0))
      SourceWithContext
        .fromTuples(requests.map(_ -> respondee))
        .via(WithdrawProcess(config, account))
        .runWith(Sink.head)
        .map(_._1 shouldBe Left(Error.InvalidAmount(0)))
    }

    "respond to Withdraw with a valid amount with Error.InvalidAmount if Account replys so" in {
      val mockAccount =
        system.spawnAnonymous(Behaviors.receiveMessagePartial[Account.Command] {
          case Account.Withdraw(amount, replyTo) =>
            replyTo ! Account.InvalidAmount(amount)
            Behaviors.stopped
        })
      def account(entityId: String) = new TestEntityRef(mockAccount)(system.toTyped)

      val requests = Source.single(Withdraw("id", 42))
      SourceWithContext
        .fromTuples(requests.map(_ -> respondee))
        .via(WithdrawProcess(config, account))
        .runWith(Sink.head)
        .map(_._1 shouldBe Left(Error.InvalidAmount(42)))
    }

    "respond to Withdraw with a valid amount with InsufficientBalance if Account replys so" in {
      val mockAccount =
        system.spawnAnonymous(Behaviors.receiveMessagePartial[Account.Command] {
          case Account.Withdraw(amount, replyTo) =>
            replyTo ! Account.InsufficientBalance(amount, 1)
            Behaviors.stopped
        })
      def account(entityId: String) = new TestEntityRef(mockAccount)(system.toTyped)

      val requests = Source.single(Withdraw("id", 10))
      SourceWithContext
        .fromTuples(requests.map(_ -> respondee))
        .via(WithdrawProcess(config, account))
        .runWith(Sink.head)
        .map(_._1 shouldBe Left(Error.InsufficientBalance(10, 1)))
    }

    "respond to Withdraw with a valid amount with Withdrawn if Account replys so" in {
      val mockAccount =
        system.spawnAnonymous(Behaviors.receiveMessagePartial[Account.Command] {
          case Account.Withdraw(amount, replyTo) =>
            replyTo ! Account.Withdrawn(amount)
            Behaviors.stopped
        })
      def account(entityId: String) = new TestEntityRef(mockAccount)(system.toTyped)

      val requests = Source.single(Withdraw("id", 10))
      SourceWithContext
        .fromTuples(requests.map(_ -> respondee))
        .via(WithdrawProcess(config, account))
        .runWith(Sink.head)
        .map(_._1 shouldBe Right(Withdrawn(10)))
    }
  }
}
