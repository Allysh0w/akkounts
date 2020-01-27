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

import akka.actor.typed.Scheduler
import akka.persistence.query.{ EventEnvelope, Offset }
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.stream.scaladsl.{ Sink, Source }
import java.util.UUID
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.slf4j.{ Logger, LoggerFactory }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.DurationInt

final class BalanceProjectionTests
    extends AsyncWordSpec
    with Matchers
    with ActorTestSuite
    with IdiomaticMockito {
  import BalanceProjection._
  import testKit._

  private implicit val ec: ExecutionContext = system.executionContext

  private implicit val logger: Logger = LoggerFactory.getLogger(getClass)

  private implicit val scheduler: Scheduler = system.scheduler

  private val config = Config(10.millis, 10.millis)

  private val uuid = UUID.fromString("aee5cc60-4e4f-11ea-b0ef-2f4d415b1804") // Time-based UUID

  "projection" should {
    "write to the DAO for no last sequence number" in {
      val balanceDao = mock[BalanceDao]
      balanceDao.readMaxOffset() returns Future.successful(None)
      balanceDao.readSeqNoAndBalance("1") returns Future.successful(None)
      balanceDao.write("1", uuid, 1, 42) returns Future.successful(())

      val readJournal = mock[EventsByTagQuery]
      readJournal.eventsByTag(Account.Name, Offset.noOffset) returns Source.single(
        new EventEnvelope(
          Offset.timeBasedUUID(uuid),
          s"${Account.Name}|1",
          1,
          Account.Deposited(42),
          0
        )
      )

      projection(balanceDao, readJournal)
        .runWith(Sink.ignore)
        .map(_ => succeed)
    }

    "write to the DAO for a valid sequence number" in {
      val balanceDao = mock[BalanceDao]
      balanceDao.readMaxOffset() returns Future.successful(Some(uuid))
      balanceDao.readSeqNoAndBalance("1") returns Future.successful(Some((1, 10)))
      balanceDao.write("1", uuid, 2, 9) returns Future.successful(())

      val readJournal = mock[EventsByTagQuery]
      readJournal.eventsByTag(Account.Name, Offset.timeBasedUUID(uuid)) returns Source(
        List(
          new EventEnvelope(
            Offset.timeBasedUUID(uuid),
            s"${Account.Name}|1",
            1,
            Account.Deposited(10),
            0
          ),
          new EventEnvelope(
            Offset.timeBasedUUID(uuid),
            s"${Account.Name}|1",
            2,
            Account.Withdrawn(1),
            0
          )
        )
      )

      projection(balanceDao, readJournal)
        .runWith(Sink.seq)
        .map(_ => succeed)
    }

    "not write to the DAO for an invalid sequence number" in {
      val balanceDao = mock[BalanceDao]
      balanceDao.readMaxOffset() returns Future.successful(Some(uuid))
      balanceDao.readSeqNoAndBalance("1") returns Future.successful(Some((1, 10)))

      val readJournal = mock[EventsByTagQuery]
      readJournal.eventsByTag(Account.Name, Offset.timeBasedUUID(uuid)) returns Source(
        List(
          new EventEnvelope(
            Offset.timeBasedUUID(uuid),
            s"${Account.Name}|1",
            3,
            Account.Deposited(10),
            0
          ),
          new EventEnvelope(
            Offset.timeBasedUUID(uuid),
            s"${Account.Name}|1",
            4,
            Account.Withdrawn(1),
            0
          )
        )
      )

      projection(balanceDao, readJournal)
        .runWith(Sink.ignore)
        .failed
        .map(_ shouldBe IllegalSeqNo(3, 1))
    }
  }
}
