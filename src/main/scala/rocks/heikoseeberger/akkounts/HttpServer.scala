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

import akka.actor.{ CoordinatedShutdown, ActorSystem => ClassicSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.Done
import akka.http.scaladsl.model.StatusCodes.{ BadRequest, Created }
import io.bullet.borer.Codec
import io.bullet.borer.derivation.MapBasedCodecs.deriveCodec
import io.moia.streamee.Process
import io.moia.streamee.FrontProcessor
import org.slf4j.LoggerFactory
import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

/**
  * [[HttpServer.run]] binds the [[HttpServer.route]] to the configured network interface and port
  * and registers termination with coordinated shutdown during service-unbind.
  */
object HttpServer {

  final case class Config(
      interface: String,
      port: Int,
      terminationDeadline: FiniteDuration,
      depositProcessorTimeout: FiniteDuration,
      withdrawProcessorTimeout: FiniteDuration
  )

  final class ReadinessCheck extends (() => Future[Boolean]) {
    override def apply(): Future[Boolean] =
      ready.future
  }

  private final object BindFailure extends CoordinatedShutdown.Reason

  private final case class Deposit(amount: Int)
  private final case class Withdraw(amount: Int)

  private implicit val depositCodec: Codec[Deposit]   = deriveCodec
  private implicit val withdrawCodec: Codec[Withdraw] = deriveCodec

  private val ready = Promise[Boolean]()

  def run(
      config: Config,
      depositProcess: Process[
        DepositProcess.Deposit,
        Either[DepositProcess.Error, DepositProcess.Deposited]
      ],
      withdrawProcess: Process[
        WithdrawProcess.Withdraw,
        Either[WithdrawProcess.Error, WithdrawProcess.Withdrawn]
      ]
  )(implicit system: ClassicSystem): Unit = {
    import config._
    import system.dispatcher

    val log      = LoggerFactory.getLogger(this.getClass)
    val shutdown = CoordinatedShutdown(system)

    val depositProcessor =
      FrontProcessor(depositProcess, depositProcessorTimeout, "deposit-processor")
    val withdrawProcessor =
      FrontProcessor(withdrawProcess, withdrawProcessorTimeout, "withdraw-processor")

    Http()
      .bindAndHandle(route(depositProcessor, withdrawProcessor), interface, port)
      .onComplete {
        case Failure(cause) =>
          log.error(s"Shutting down, because cannot bind to $interface:$port!", cause)
          shutdown.run(BindFailure)

        case Success(binding) =>
          if (log.isInfoEnabled)
            log.info(s"Listening for HTTP connections on ${binding.localAddress}")
          ready.success(true)
          shutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, "terminate-http-server") { () =>
            binding.terminate(terminationDeadline).map(_ => Done)
          }
      }
  }

  def route(
      depositProcessor: FrontProcessor[
        DepositProcess.Deposit,
        Either[DepositProcess.Error, DepositProcess.Deposited]
      ],
      withdrawProcessor: FrontProcessor[
        WithdrawProcess.Withdraw,
        Either[WithdrawProcess.Error, WithdrawProcess.Withdrawn]
      ]
  ): Route = {
    import akka.http.scaladsl.server.Directives._
    import io.bullet.borer.compat.akkaHttp.{ borerFromEntityUnmarshaller, borerToEntityMarshaller }

    pathPrefix(Segment) { id =>
      path("deposit") {
        post {
          entity(as[Deposit]) {
            case Deposit(amount) =>
              onSuccess(depositProcessor.offer(DepositProcess.Deposit(id, amount))) {
                case Left(DepositProcess.Error.InvalidAmount(amount)) =>
                  complete(BadRequest -> s"Invalid amount $amount!")
                case Right(DepositProcess.Deposited(amount)) =>
                  complete(Created -> s"Deposited amount $amount")
              }
          }
        }
      } ~
      path("withdraw") {
        post {
          entity(as[Withdraw]) {
            case Withdraw(amount) =>
              onSuccess(withdrawProcessor.offer(WithdrawProcess.Withdraw(id, amount))) {
                case Left(WithdrawProcess.Error.InvalidAmount(amount)) =>
                  complete(BadRequest -> s"Invalid amount $amount!")
                case Left(WithdrawProcess.Error.InsufficientBalance(amount, balance)) =>
                  complete(BadRequest -> s"Insufficient balance $balance for amount $amount!")
                case Right(WithdrawProcess.Withdrawn(amount)) =>
                  complete(Created -> s"Withdrawn amount $amount")
              }
          }
        }
      }
    }
  }
}
