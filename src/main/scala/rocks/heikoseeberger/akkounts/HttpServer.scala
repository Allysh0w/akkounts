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
import akka.http.scaladsl.model.StatusCodes.OK
import org.slf4j.LoggerFactory
import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

/**
  * [[HttpServer.run]] binds the [[HttpServer.route]] to the configured network interface and port
  * and registers termination with coordinated shutdown during service-unbind.
  */
object HttpServer {

  final case class Config(interface: String, port: Int, terminationDeadline: FiniteDuration)

  final class ReadinessCheck extends (() => Future[Boolean]) {
    override def apply(): Future[Boolean] =
      ready.future
  }

  private final object BindFailure extends CoordinatedShutdown.Reason

  private val ready = Promise[Boolean]()

  def run(config: Config)(implicit system: ClassicSystem): Unit = {
    import config._
    import system.dispatcher

    val log      = LoggerFactory.getLogger(this.getClass)
    val shutdown = CoordinatedShutdown(system)

    Http()
      .bindAndHandle(route, interface, port)
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

  def route: Route = {
    import akka.http.scaladsl.server.Directives._

    pathSingleSlash {
      get {
        complete {
          OK
        }
      }
    }
  }
}
