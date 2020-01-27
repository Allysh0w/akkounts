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

import akka.actor.{ ActorSystem => ClassicSystem }
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.{ ClassicActorSystemOps, TypedActorSystemOps }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity }
import akka.cluster.typed.{ Cluster, SelfUp, Subscribe, Unsubscribe }
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
import pureconfig.generic.auto.exportReader
import pureconfig.ConfigSource

/**
  * Main entry point into the application. The main actor (guardian) defers initialization of the
  * application (creating streams, actors, HTTP server, etc.) until after becoming a cluster member.
  */
object Main {

  private final val Name = "akkounts"

  final case class Config(
      httpServer: HttpServer.Config,
      depositProcess: DepositProcess.Config,
      withdrawProcess: WithdrawProcess.Config
  )

  def main(args: Array[String]): Unit = {
    // Always use async logging!
    sys.props += "log4j2.contextSelector" -> classOf[AsyncLoggerContextSelector].getName

    // Must happen before creating the actor system to fail fast if configuration cannot be loaded
    val config = ConfigSource.default.at(Name).loadOrThrow[Config]

    // Always start with a classic system, because some libraries still rely on it
    val classicSystem = ClassicSystem(Name)

    // Start Akka Management Cluster Bootstrap early for health probes to become available quickly
    AkkaManagement(classicSystem).start()
    ClusterBootstrap(classicSystem).start()

    // Spawn main/guardian actor
    classicSystem.spawn(Main(config), "main")
  }

  def apply(config: Config): Behavior[SelfUp] =
    Behaviors.setup { context =>
      import context.log

      if (log.isInfoEnabled)
        log.info(s"${context.system.name} started and ready to join cluster")
      Cluster(context.system).subscriptions ! Subscribe(context.self, classOf[SelfUp])

      Behaviors.receive { (context, _) =>
        if (log.isInfoEnabled)
          log.info(s"${context.system.name} joined cluster and is up")
        Cluster(context.system).subscriptions ! Unsubscribe(context.self)

        implicit val classicSystem: ClassicSystem = context.system.toClassic

        val sharding = ClusterSharding(context.system)
        sharding.init(Entity(Account.typeKey)(Account(_)))

        val depositProcess =
          DepositProcess(config.depositProcess, sharding.entityRefFor(Account.typeKey, _))

        val withdrawProcess =
          WithdrawProcess(config.withdrawProcess, sharding.entityRefFor(Account.typeKey, _))

        HttpServer.run(config.httpServer, depositProcess, withdrawProcess)

        Behaviors.empty
      }
    }
}
