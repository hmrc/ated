/*
 * Copyright 2019 HM Revenue & Customs
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

package builders

import models.{Reliefs, ReliefsTaxAvoidance, TaxAvoidance}
import org.joda.time.LocalDate


object ReliefBuilder {

  def reliefTaxAvoidance(atedRefNo: String, periodKey: Int): ReliefsTaxAvoidance = {
    reliefTaxAvoidance(atedRefNo, periodKey, Reliefs(periodKey), TaxAvoidance())
  }

  def reliefTaxAvoidance(atedRefNo: String,
                         periodKey: Int,
                         reliefs: Reliefs,
                         taxAvoidance: TaxAvoidance = TaxAvoidance()): ReliefsTaxAvoidance = {

    def periodStartDate(periodKey: Int): LocalDate = {
      new LocalDate(s"$periodKey-04-01")
    }

    def periodEndDate(periodKey: Int): LocalDate = {
      periodStartDate(periodKey).plusYears(1).minusDays(1)
    }

    new ReliefsTaxAvoidance(atedRefNo = atedRefNo,
      periodKey = periodKey,
      periodStartDate = periodStartDate(periodKey),
      periodEndDate = periodEndDate(periodKey),
      reliefs = reliefs,
      taxAvoidance = taxAvoidance
    )
  }
}
