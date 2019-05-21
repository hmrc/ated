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

package utils

// nasty non-private utility https://github.com/hmrc/reactivemongo-test/blob/master/src/main/scala/uk/gov/hmrc/mongo/RepositoryPreparation.scala
// doesn't respect patienceConfig - has nothing to do with Mongo - should have been private, but now projects use it...
trait Awaiting {

  import scala.concurrent._
  import scala.concurrent.duration._
  import scala.language.postfixOps

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val timeout: FiniteDuration = 5 seconds

  def await[A](future: Future[A])(implicit timeout: Duration = timeout): A =
    Await.result(future, timeout)
}