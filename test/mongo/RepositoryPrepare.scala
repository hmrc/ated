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

package mongo

import org.mongodb.scala.MongoCollection
import org.scalatest.Suite
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt

trait RepositoryPreparation {
  this: Suite =>

  def prepare[A](rep: ReactiveRepository[A])(implicit ec: ExecutionContext): Unit = {
    println(s"Dropping '${rep.collection.namespace}'")
    val f = for {
        _ <- rep.collection.drop().toFuture
        _ =  println(s"Applying indexes on '${rep.collection.namespace}'")
        _ <- rep.collection.createIndexes(rep.indices).toFuture
        _ =  println(s"Indices created '${rep.collection.namespace}'")
      } yield ()
     Await.result(f, 5.seconds)
  }
}