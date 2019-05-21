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

import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, JsObject, Json}
/*import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.commands._
import reactivemongo.api.indexes.Index
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.core.errors.GenericDatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers
import reactivemongo.play.json.collection.JSONBatchCommands.JSONCountCommand._
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.commands.JSONFindAndModifyCommand.{FindAndModifyResult, Update}*/
import org.mongodb.scala.{Completed, Document, MongoClient, MongoDatabase, MongoCollection}
import org.mongodb.scala.model.{Filters, IndexModel, Updates, UpdateOptions, FindOneAndUpdateOptions}
import mongo.json.ReactiveMongoFormats
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.result.DeleteResult

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace

abstract class ReactiveRepository[A, ID](
    protected[mongo] val collectionName: String
  , protected[mongo] val mongo: () => MongoDatabase
  , domainFormat: Format[A]
  , idFormat: Format[ID] = ReactiveMongoFormats.objectIdFormats
  )(implicit ct: ClassTag[A])
    extends Indexes
    /*with MongoDb
    with CollectionName
    with CurrentTime*/ {

  //import ImplicitBSONHandlers._
  import play.api.libs.json.Json.JsValueWrapper

  implicit val domainFormatImplicit: Format[A] = domainFormat
  implicit val idFormatImplicit: Format[ID]    = idFormat

  lazy val collection: MongoCollection[A] = mongo().getCollection(collectionName)

  protected[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val message: String                = "Failed to ensure index"

  //ensureIndexes(scala.concurrent.ExecutionContext.Implicits.global)

  protected val _Id                   = "_id"
  protected def _id(id: ID): JsObject = Json.obj(_Id -> id)

//   def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[Seq[A]] = {
  def find(filter: Bson)(implicit ec: ExecutionContext): Future[Seq[A]] = {
    collection
      //.find(Json.obj(query: _*))
      //.cursor[A](ReadPreference.primaryPreferred)
      //.collect(maxDocs = -1, FailOnError[List[A]]())
      .find[A](filter)
      .toFuture
  }


 def findAndUpdate(
      filter: Bson
    , update: Bson
    , options: FindOneAndUpdateOptions
    )(implicit ec: ExecutionContext): Future[A] =
    collection
      .findOneAndUpdate(
          filter  = filter
        , update  = update
        , options = options
        )
      .toFuture

  /*def count(implicit ec: ExecutionContext): Future[Int] = count(Json.obj())

  def count(query: JsObject, readPreference: ReadPreference = ReadPreference.primary)(
    implicit ec: ExecutionContext): Future[Int] =
    collection
      .runCommand(Count(ImplicitlyDocumentProducer.producer(query)), readPreference)
      .map(_.count)*/

//   def remove(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[DeleteResult] =
  def remove(filter: Bson)(implicit ec: ExecutionContext): Future[DeleteResult] =
    collection
      .deleteOne(filter)
      .toFuture //TODO: pass in the WriteConcern

  def drop(implicit ec: ExecutionContext): Future[Boolean] =
    collection
      .drop()
      .toFuture
      .map(_ => true)
      .recover[Boolean] {
        case _ => false
      }

  //@deprecated("use ReactiveRepository#insert() instead", "3.0.1")
  //def save(entity: A)(implicit ec: ExecutionContext): Future[WriteResult] = insert(entity)

  def insert(entity: A)(implicit ec: ExecutionContext): Future[Completed] =
    collection
      .insertOne(entity)
      .toFuture

/*
    collection.indexesManager
      .create(index)
      .map(wr => {
        if (!wr.ok) {
          val msg = wr.writeErrors.mkString(", ")
          val maybeMsg = if (msg.contains("E11000")) {
            // this is for backwards compatibility to mongodb 2.6.x
            throw GenericDatabaseException(msg, wr.code)
          } else Some(msg)
          logger.error(s"$message (${index.eventualName}) : '${maybeMsg.map(_.toString)}'")
        }
        wr.ok
      })
      .recover {
        case t =>
          logger.error(s"$message (${index.eventualName})", t)
          false
      }*/

  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[String]] =
    collection
      .createIndexes(indexes)
      .toFuture
}

sealed abstract class UpdateType[A] {
  def savedValue: A
}

case class Saved[A](savedValue: A) extends UpdateType[A]

case class Updated[A](previousValue: A, savedValue: A) extends UpdateType[A]

trait Indexes {
  def indexes: Seq[IndexModel] = Seq.empty
}