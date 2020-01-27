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
import io.moia.streamee.Process
import io.moia.streamee.either.{ EitherFlowWithContextOps, tapErrors }
import scala.concurrent.duration.FiniteDuration

/**
  * Streamee process (flow with context) for depositing to an account including a validation step.
  */
object DepositProcess {

  sealed trait Error
  object Error {
    final case class InvalidAmount(amount: Int) extends Error
  }

  final case class Deposit(accountId: String, amount: Int)
  final case class Deposited(amount: Int)

  final case class Config(accountTimeout: FiniteDuration, accountParallelism: Int)

  def apply(
      config: Config,
      account: String => EntityRef[Account.Command]
  ): Process[Deposit, Either[Error, Deposited]] = {
    import config._

    tapErrors { errorTap =>
      Process[Deposit, Either[Error, Deposited]]
        .map {
          case Deposit(_, a) if a <= 0 => Left(Error.InvalidAmount(a))
          case valid                   => Right(valid)
        }
        .errorTo(errorTap)
        .mapAsync(accountParallelism) {
          case Deposit(accountId, amount) =>
            implicit val timeout: Timeout = accountTimeout
            account(accountId) ? Account.deposit(amount)
        }
        .map {
          case Account.InvalidAmount(a) => Left(Error.InvalidAmount(a))
          case Account.Deposited(a)     => Right(Deposited(a))
        }
        .errorTo(errorTap)
    }
  }
}
