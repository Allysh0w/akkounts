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

import akka.actor.typed.{ Behavior, PostStop }
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.query.{ EventEnvelope, Offset, TimeBasedUUID }
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.stream.{ KillSwitches, Materializer }
import akka.stream.scaladsl.{ Keep, RestartSource, Sink, Source }
import java.util.UUID
import org.slf4j.Logger
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

/**
  * Projection – based upon Akka Persistence Query – of [[Account]] events to balance table. It is
  * an actor, because it should be run as a cluster singleton.
  */
object BalanceProjection {

  sealed trait Command

  final case class Config(minBackoff: FiniteDuration, maxBackoff: FiniteDuration)

  private[akkounts] final case class IllegalSeqNo(seqNo: Long, lastSeqNo: Long)
      extends IllegalStateException(s"Expected last seqNo ${seqNo - 1}, but was $lastSeqNo!")

  def apply(
      config: Config,
      balanceDao: BalanceDao,
      readJournal: EventsByTagQuery
  ): Behavior[Command] =
    Behaviors.setup { context =>
      import config._
      import context.executionContext
      implicit val mat: Materializer = Materializer(context) // Life cycle of this actor!
      implicit val logger: Logger    = context.log

      val switch =
        RestartSource
          .withBackoff(minBackoff, maxBackoff, 0)(() => projection(balanceDao, readJournal))
          .viaMat(KillSwitches.single)(Keep.right)
          .to(Sink.ignore)
          .run()

      Behaviors
        .receiveSignal {
          case (_, PostStop) =>
            switch.shutdown()
            Behaviors.stopped
        }
    }

  private[akkounts] def projection(
      balanceDao: BalanceDao,
      readJournal: EventsByTagQuery
  )(implicit ec: ExecutionContext, logger: Logger) =
    Source
      .future(balanceDao.readMaxOffset())
      .flatMapConcat { offset =>
        val o = offset.map(TimeBasedUUID).getOrElse(Offset.noOffset)
        readJournal.eventsByTag(Account.Name, o)
      }
      .collect {
        case EventEnvelope(TimeBasedUUID(o), AccountId(i), n, Account.Deposited(a)) =>
          (i, o, n, a)
        case EventEnvelope(TimeBasedUUID(o), AccountId(i), n, Account.Withdrawn(a)) =>
          (i, o, n, -a)
      }
      // Parallelism must be 1 to avoid races btw. read, check, write and updating the offset!
      // Alternatively we could modify the schema and use INSERT ... IF NOT EXISTS,
      // but that also comes with a performance hit.
      .mapAsync(1) { case (i, o, n, a) => updateBalance(balanceDao, i, o, n, a) }

  private def updateBalance(
      balanceDao: BalanceDao,
      accountId: String,
      offset: UUID,
      seqNo: Long,
      amount: Int
  )(implicit ec: ExecutionContext, logger: Logger) = {
    def checkAndWrite(lastSeqNo: Long, balance: Long) =
      if (seqNo <= lastSeqNo) {
        logger.debug(s"Skipping outdated event for accountId $accountId with seqNo $seqNo")
        Future.successful(())
      } else if (seqNo > lastSeqNo + 1) {
        val illegalSeqNo = IllegalSeqNo(seqNo, lastSeqNo)
        logger.warn(illegalSeqNo.getMessage)
        Future.failed(illegalSeqNo)
      } else {
        val newBalance = balance + amount
        logger.debug(s"Writing new balance $newBalance with seqNo $seqNo")
        balanceDao.write(accountId, offset, seqNo, newBalance)
      }

    for {
      (lastSeqNo, balance) <- balanceDao.readSeqNoAndBalance(accountId).map(_.getOrElse(0L, 0L))
      _                    <- checkAndWrite(lastSeqNo, balance)
    } yield ()
  }
}
