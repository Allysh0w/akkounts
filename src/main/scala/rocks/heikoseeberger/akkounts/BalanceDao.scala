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

import akka.persistence.cassandra.ListenableFutureConverter
import com.datastax.driver.core.Session
import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }

/**
  * DAO for balance table.
  *
  * @param cassandraSession Cassandra session
  */
final class BalanceDao(cassandraSession: Future[Session])(implicit ec: ExecutionContext) {

  private val readSeqNoAndBalanceStmt = {
    val stmt = "SELECT seq_no, balance FROM akkounts.balance WHERE account_id=?"
    cassandraSession.flatMap(_.prepareAsync(stmt).asScala)
  }

  private val readMaxOffsetStmt = {
    val stmt = "SELECT max(offset) FROM akkounts.balance"
    cassandraSession.flatMap(_.prepareAsync(stmt).asScala)
  }

  private val writeStmt = {
    val stmt = "UPDATE akkounts.balance SET offset=?, seq_no=?, balance=? WHERE account_id=?"
    cassandraSession.flatMap(_.prepareAsync(stmt).asScala)
  }

  /**
    * Read balance from balance table for given accountId.
    */
  def readBalance(accountId: String): Future[Option[Long]] =
    readSeqNoAndBalance(accountId).map(_.map(_._2))

  /**
    * Read (seqNo, balance) from balance table for given accountId.
    */
  def readSeqNoAndBalance(accountId: String): Future[Option[(Long, Long)]] =
    for {
      session <- cassandraSession
      stmt    <- readSeqNoAndBalanceStmt
      result  <- session.executeAsync(stmt.bind(accountId)).asScala
    } yield Option(result.one()).map(row => (row.getLong(0), row.getLong(1)))

  /**
    * Read max offset from balance table. Expensive operation, should not be used frequently!
    */
  def readMaxOffset(): Future[Option[UUID]] =
    for {
      session <- cassandraSession
      stmt    <- readMaxOffsetStmt
      result  <- session.executeAsync(stmt.bind()).asScala
    } yield Option(result.one()).flatMap(row => Option(row.getUUID(0)))

  /**
    * Write (offset, seqNo, balance) to balance table for given accountId.
    */
  def write(accountId: String, offset: UUID, seqNo: Long, balance: Long): Future[Unit] =
    for {
      session <- cassandraSession
      stmt    <- writeStmt
      _       <- session.executeAsync(stmt.bind(offset, seqNo, balance, accountId)).asScala
    } yield ()
}
