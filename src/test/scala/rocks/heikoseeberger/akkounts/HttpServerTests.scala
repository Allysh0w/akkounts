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
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes.{ BadRequest, Created, OK }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.moia.streamee.{ FrontProcessor, Process }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration.DurationInt

final class HttpServerTests extends AnyWordSpec with Matchers with ScalatestRouteTest {
  import HttpServer._

  implicit private val typedSystem: ActorSystem[_] = system.toTyped

  "The route" should {
    "respond to POST /42/deposit with BadRequest" in {
      val (depositProcessor, withdrawProcessor, balanceProcessor) =
        processors(
          _ => Left(DepositProcess.Error.InvalidAmount(0)),
          _ => Left(WithdrawProcess.Error.InvalidAmount(0)),
          _ => BalanceProcess.Balance(42)
        )

      val request =
        Post("/42/deposit").withEntity(`application/json`, """{"amount":0}""")

      request ~> route(depositProcessor, withdrawProcessor, balanceProcessor) ~> check {
        status shouldBe BadRequest
        responseAs[String] should include("Invalid amount 0")
      }
    }

    "respond to POST /42/deposit with Created" in {
      val (depositProcessor, withdrawProcessor, balanceProcessor) =
        processors(
          d => Right(DepositProcess.Deposited(d.amount)),
          _ => Left(WithdrawProcess.Error.InvalidAmount(0)),
          _ => BalanceProcess.Balance(42)
        )

      val request =
        Post("/42/deposit").withEntity(`application/json`, """{"amount":42}""")

      request ~> route(depositProcessor, withdrawProcessor, balanceProcessor) ~> check {
        status shouldBe Created
        responseAs[String] should include("Deposited amount 42")
      }
    }

    "respond to POST /42/withdraw with BadRequest for an invalid amount" in {
      val (depositProcessor, withdrawProcessor, balanceProcessor) =
        processors(
          _ => Left(DepositProcess.Error.InvalidAmount(0)),
          _ => Left(WithdrawProcess.Error.InvalidAmount(0)),
          _ => BalanceProcess.Balance(42)
        )

      val request =
        Post("/42/withdraw").withEntity(`application/json`, """{"amount":0}""")

      request ~> route(depositProcessor, withdrawProcessor, balanceProcessor) ~> check {
        status shouldBe BadRequest
        responseAs[String] should include("Invalid amount 0")
      }
    }

    "respond to POST /42/withdraw with BadRequest for insufficient balance" in {
      val (depositProcessor, withdrawProcessor, balanceProcessor) =
        processors(
          _ => Left(DepositProcess.Error.InvalidAmount(0)),
          w => Left(WithdrawProcess.Error.InsufficientBalance(w.amount, 0)),
          _ => BalanceProcess.Balance(42)
        )

      val request =
        Post("/42/withdraw").withEntity(`application/json`, """{"amount":42}""")

      request ~> route(depositProcessor, withdrawProcessor, balanceProcessor) ~> check {
        status shouldBe BadRequest
        responseAs[String] should include("Insufficient balance 0 for amount 42")
      }
    }

    "respond to POST /42/withdraw with Created" in {
      val (depositProcessor, withdrawProcessor, balanceProcessor) =
        processors(
          _ => Left(DepositProcess.Error.InvalidAmount(0)),
          w => Right(WithdrawProcess.Withdrawn(w.amount)),
          _ => BalanceProcess.Balance(42)
        )

      val request =
        Post("/42/withdraw").withEntity(`application/json`, """{"amount":42}""")

      request ~> route(depositProcessor, withdrawProcessor, balanceProcessor) ~> check {
        status shouldBe Created
        responseAs[String] should include("Withdrawn amount 42")
      }
    }

    "respond to GET /42 with OK" in {
      val (depositProcessor, withdrawProcessor, balanceProcessor) =
        processors(
          _ => Left(DepositProcess.Error.InvalidAmount(0)),
          w => Right(WithdrawProcess.Withdrawn(w.amount)),
          _ => BalanceProcess.Balance(42)
        )

      val request = Get("/42")

      request ~> route(depositProcessor, withdrawProcessor, balanceProcessor) ~> check {
        status shouldBe OK
        responseAs[String] should include("Balance is 42")
      }
    }
  }

  private def processors(
      deposit: DepositProcess.Deposit => Either[DepositProcess.Error, DepositProcess.Deposited],
      withdraw: WithdrawProcess.Withdraw => Either[
        WithdrawProcess.Error,
        WithdrawProcess.Withdrawn
      ],
      balance: BalanceProcess.GetBalance => BalanceProcess.Balance
  ) = {
    val depositProcessor =
      FrontProcessor(
        Process[DepositProcess.Deposit, Either[DepositProcess.Error, DepositProcess.Deposited]]
          .map(deposit),
        1.second,
        "deposit-processor"
      )
    val withdrawProcessor =
      FrontProcessor(
        Process[WithdrawProcess.Withdraw, Either[WithdrawProcess.Error, WithdrawProcess.Withdrawn]]
          .map(withdraw),
        1.second,
        "withdraw-processor"
      )
    val balanceProcessor =
      FrontProcessor(
        Process[BalanceProcess.GetBalance, BalanceProcess.Balance].map(balance),
        1.second,
        "balance-processor"
      )
    (depositProcessor, withdrawProcessor, balanceProcessor)
  }
}
