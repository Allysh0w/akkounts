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
package proto

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.actor.typed.scaladsl.adapter.ClassicActorRefOps
import akka.actor.ExtendedActorSystem

final class AccountSerializerTests extends AnyWordSpec with Matchers with ClassicSystemSuite {
  import Account._

  private val serializer = new AccountSerializer(system.asInstanceOf[ExtendedActorSystem])

  private val replyTo = system.deadLetters

  "AccountSerializer" should {

    "serialize and deserialize Deposit" in {
      val deposit     = Deposit(42, replyTo.toTyped)
      val (bytes, mf) = serialize(deposit)
      serializer.fromBinary(bytes, mf) shouldBe deposit
    }

    "serialize and deserialize Deposited" in {
      val deposited   = Deposited(42)
      val (bytes, mf) = serialize(deposited)
      serializer.fromBinary(bytes, mf) shouldBe deposited
    }

    "serialize and deserialize Withdraw" in {
      val withdraw    = Withdraw(42, replyTo.toTyped)
      val (bytes, mf) = serialize(withdraw)
      serializer.fromBinary(bytes, mf) shouldBe withdraw
    }

    "serialize and deserialize InsufficientBalance" in {
      val insufficientBalance = InsufficientBalance(42, 7)
      val (bytes, mf)         = serialize(insufficientBalance)
      serializer.fromBinary(bytes, mf) shouldBe insufficientBalance
    }

    "serialize and deserialize Withdrawn" in {
      val withdrawn   = Withdrawn(42)
      val (bytes, mf) = serialize(withdrawn)
      serializer.fromBinary(bytes, mf) shouldBe withdrawn
    }

    "serialize and deserialize InvalidAmount" in {
      val invalidAmount = InvalidAmount(-42)
      val (bytes, mf)   = serialize(invalidAmount)
      serializer.fromBinary(bytes, mf) shouldBe invalidAmount
    }
  }

  private def serialize(o: AnyRef) = (serializer.toBinary(o), serializer.manifest(o))
}
