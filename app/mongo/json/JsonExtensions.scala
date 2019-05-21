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

package mongo.json

object JsonExtensions {

  import play.api.libs.json.{JsPath, JsValue, __}

  def copyKey(fromPath: JsPath, toPath: JsPath) =
    __.json.update(toPath.json.copyFrom(fromPath.json.pick))

  def moveKey(fromPath: JsPath, toPath: JsPath) =
    (json: JsValue) => json.transform(copyKey(fromPath, toPath) andThen fromPath.json.prune).get
}