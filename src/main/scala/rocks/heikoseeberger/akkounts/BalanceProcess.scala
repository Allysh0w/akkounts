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

import io.moia.streamee.Process

/**
  * Streamee process (flow with context) for getting the balance for an account.
  */
object BalanceProcess {

  final case class GetBalance(accountId: String)
  final case class Balance(balance: Long)

  final case class Config(balanceDaoParallelism: Int)

  def apply(config: Config, balanceDao: BalanceDao): Process[GetBalance, Balance] =
    Process[GetBalance, Balance]
      .mapAsync(config.balanceDaoParallelism) {
        case GetBalance(accountId) => balanceDao.readBalance(accountId)
      }
      .map(balance => Balance(balance.getOrElse(0)))
}
