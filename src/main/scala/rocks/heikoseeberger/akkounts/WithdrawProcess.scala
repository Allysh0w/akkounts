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

import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.Timeout
import io.moia.streamee.either.{ EitherFlowWithContextOps, tapErrors }
import io.moia.streamee.Process
import scala.concurrent.duration.FiniteDuration

/**
  * Streamee process (flow with context) for withdrawing from an account including a validation step.
  */
object WithdrawProcess {

  sealed trait Error
  object Error {
    final case class InvalidAmount(amount: Int)                      extends Error
    final case class InsufficientBalance(amount: Int, balance: Long) extends Error
  }

  final case class Withdraw(accountId: String, amount: Int)
  final case class Withdrawn(amount: Int)

  final case class Config(accountTimeout: FiniteDuration, accountParallelism: Int)

  def apply(
      config: Config,
      account: String => EntityRef[Account.Command]
  ): Process[Withdraw, Either[Error, Withdrawn]] = {
    import config._

    tapErrors { errorTap =>
      Process[Withdraw, Either[Error, Withdrawn]]
        .map {
          case Withdraw(_, a) if a <= 0 => Left(Error.InvalidAmount(a))
          case valid                    => Right(valid)
        }
        .errorTo(errorTap)
        .mapAsync(accountParallelism) {
          case Withdraw(accountId, amount) =>
            implicit val timeout: Timeout = accountTimeout
            account(accountId) ? Account.withdraw(amount)
        }
        .map {
          case Account.InvalidAmount(a)          => Left(Error.InvalidAmount(a))
          case Account.InsufficientBalance(a, b) => Left(Error.InsufficientBalance(a, b))
          case Account.Withdrawn(a)              => Right(Withdrawn(a))
        }
        .errorTo(errorTap)
    }
  }
}
