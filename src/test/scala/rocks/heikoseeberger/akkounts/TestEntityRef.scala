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

package akka.cluster.sharding.typed
package scaladsl

import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.actor.typed.internal.InternalRecipientRef
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.ActorRefProvider
import akka.util.Timeout
import scala.concurrent.Future

/**
  * Testing utility. Because of package private access of `InternalRecipientRef` this class must
  * be in the "akka" package.
  */
final class TestEntityRef[-A](delegate: ActorRef[A])(implicit system: ActorSystem[_])
    extends EntityRef[A]
    with InternalRecipientRef[A] {

  override def provider: ActorRefProvider =
    null

  override def isTerminated: Boolean =
    false

  override def tell(msg: A): Unit =
    delegate.tell(msg)

  override def ask[Res](f: ActorRef[Res] => A)(implicit timeout: Timeout): Future[Res] =
    delegate.ask(f)(timeout, system.scheduler)
}
