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

import akka.actor.typed.{ ActorRef, ActorRefResolver }
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.ExtendedActorSystem
import akka.serialization.SerializerWithStringManifest
import java.io.NotSerializableException
import rocks.heikoseeberger.akkounts.proto.account.{
  Deposit => DepositProto,
  Deposited => DepositedProto,
  InsufficientBalance => InsufficientBalanceProto,
  InvalidAmount => InvalidAmountProto,
  Withdraw => WithdrawProto,
  Withdrawn => WithdrawnProto
}

final class AccountSerializer(system: ExtendedActorSystem) extends SerializerWithStringManifest {
  import Account._

  override val identifier: Int =
    SerializerIdentifier.Account

  private val actorRefResolver = ActorRefResolver(system.toTyped)

  private val DepositMf             = "Deposit"
  private val DepositedMf           = "Deposited"
  private val WithdrawMf            = "Withdraw"
  private val InsufficientBalanceMf = "InsufficientBalance"
  private val WithdrawnMf           = "Withdrawn"
  private val InvalidAmountMf       = "InvalidAmount"

  override def manifest(o: AnyRef): String =
    o match {
      case serializable: Serializable =>
        serializable match {
          case _: Deposit             => DepositMf
          case _: Deposited           => DepositedMf
          case _: Withdraw            => WithdrawMf
          case _: InsufficientBalance => InsufficientBalanceMf
          case _: Withdrawn           => WithdrawnMf
          case _: InvalidAmount       => InvalidAmountMf
        }
      case _ =>
        throw new IllegalArgumentException(s"Unknown class: ${o.getClass}!")
    }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val proto =
      o match {
        case serializable: Serializable =>
          serializable match {
            case Deposit(a, r)             => DepositProto(a, serialize(r))
            case Deposited(a)              => DepositedProto(a)
            case Withdraw(a, r)            => WithdrawProto(a, serialize(r))
            case InsufficientBalance(a, b) => InsufficientBalanceProto(a, b)
            case Withdrawn(a)              => WithdrawnProto(a)
            case InvalidAmount(a)          => InvalidAmountProto(a)
          }
        case _ =>
          throw new IllegalArgumentException(s"Unknown class: ${o.getClass}!")
      }
    proto.toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    def deposit(proto: DepositProto) = {
      import proto._
      Deposit(amount, resolve(replyTo))
    }
    def deposited(proto: DepositedProto) = Deposited(proto.amount)
    def withdraw(proto: WithdrawProto) = {
      import proto._
      Withdraw(amount, resolve(replyTo))
    }
    def insufficientBalance(proto: InsufficientBalanceProto) = {
      import proto._
      InsufficientBalance(amount, balance)
    }
    def withdrawn(proto: WithdrawnProto)         = Withdrawn(proto.amount)
    def invalidAmount(proto: InvalidAmountProto) = InvalidAmount(proto.amount)
    manifest match {
      case DepositMf             => deposit(DepositProto.parseFrom(bytes))
      case DepositedMf           => deposited(DepositedProto.parseFrom(bytes))
      case WithdrawMf            => withdraw(WithdrawProto.parseFrom(bytes))
      case InsufficientBalanceMf => insufficientBalance(InsufficientBalanceProto.parseFrom(bytes))
      case WithdrawnMf           => withdrawn(WithdrawnProto.parseFrom(bytes))
      case InvalidAmountMf       => invalidAmount(InvalidAmountProto.parseFrom(bytes))
      case _                     => throw new NotSerializableException(manifest)
    }
  }

  private def serialize[A](ref: ActorRef[A]) = actorRefResolver.toSerializationFormat(ref)

  private def resolve[A](s: String) = actorRefResolver.resolveActorRef[A](s)
}
