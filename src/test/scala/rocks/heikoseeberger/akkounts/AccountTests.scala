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

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.persistence.typed.PersistenceId
import java.util.UUID
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class AccountTests extends AnyWordSpec with Matchers with ActorTestSuite {
  import Account._
  import testKit._

  "Sending a Deposit command with an invalid amount to an Account" should {
    "result in an InvalidAmount reply" in {
      val replyTo = TestProbe[DepositReply]()
      val account = spawn(Account(randomPersistenceId()))
      account ! Deposit(0, replyTo.ref)
      replyTo.expectMessage(InvalidAmount(0))
      account ! Deposit(-42, replyTo.ref)
      replyTo.expectMessage(InvalidAmount(-42))
    }
  }

  "Sending a Withdraw command with an invalid amount to an Account" should {
    "result in an InvalidAmount reply" in {
      val replyTo = TestProbe[WithdrawReply]()
      val account = spawn(Account(randomPersistenceId()))
      account ! Withdraw(0, replyTo.ref)
      replyTo.expectMessage(InvalidAmount(0))
      account ! Withdraw(-42, replyTo.ref)
      replyTo.expectMessage(InvalidAmount(-42))
    }
  }

  "Sending Deposit and Withdraw commands to an Account" should {
    "maintain the invariant that the balance must not be negative" in {
      val account        = spawn(Account(randomPersistenceId()))
      val depositSender  = TestProbe[DepositReply]()
      val withdrawSender = TestProbe[WithdrawReply]()

      account ! Withdraw(42, withdrawSender.ref)
      withdrawSender.expectMessage(InsufficientBalance(42, 0))

      account ! Deposit(42, depositSender.ref)
      depositSender.expectMessage(Deposited(42))

      account ! Withdraw(21, withdrawSender.ref)
      withdrawSender.expectMessage(Withdrawn(21))

      account ! Withdraw(21, withdrawSender.ref)
      withdrawSender.expectMessage(Withdrawn(21))

      account ! Withdraw(42, withdrawSender.ref)
      withdrawSender.expectMessage(InsufficientBalance(42, 0))
    }
  }

  private def randomPersistenceId() = PersistenceId.ofUniqueId(UUID.randomUUID().toString)
}
