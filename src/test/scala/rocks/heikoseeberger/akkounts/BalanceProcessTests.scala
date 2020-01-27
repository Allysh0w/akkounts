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
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.{ Sink, Source, SourceWithContext }
import io.moia.streamee.Respondee
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import scala.concurrent.Future

final class BalanceProcessTests
    extends AsyncWordSpec
    with Matchers
    with ClassicSystemSuite
    with IdiomaticMockito {
  import BalanceProcess._

  private val respondee =
    system.spawnAnonymous(Behaviors.empty[Respondee.Response[Balance]])

  "BalanceProcess" should {
    "respond to GetBalance with Balance(0) if there is no entry in the balance table" in {
      val balanceDao = mock[BalanceDao]
      balanceDao.readBalance("42") returns Future.successful(None)

      val requests = Source.single(GetBalance("42"))
      SourceWithContext
        .fromTuples(requests.map(_ -> respondee))
        .via(BalanceProcess(Config(42), balanceDao))
        .runWith(Sink.head)
        .map(_._1 shouldBe Balance(0))
    }

    "respond to GetBalance with Balance(42) if 42 is the value in the balance table" in {
      val balanceDao = mock[BalanceDao]
      balanceDao.readBalance("42") returns Future.successful(Some(42))

      val requests = Source.single(GetBalance("42"))
      SourceWithContext
        .fromTuples(requests.map(_ -> respondee))
        .via(BalanceProcess(Config(42), balanceDao))
        .runWith(Sink.head)
        .map(_._1 shouldBe Balance(42))
    }
  }
}
