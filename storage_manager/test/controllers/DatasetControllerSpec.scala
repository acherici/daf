/*
 * Copyright 2017 TEAM PER LA TRASFORMAZIONE DIGITALE
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

package controllers

import akka.stream.ActorMaterializer
import controllers.modules.TestAbstractModule
import daf.instances.{ AkkaInstance, ConfigurationInstance, FileSystemInstance }
import daf.filesystem.StringPathSyntax
import org.apache.hadoop.conf.{ Configuration => HadoopConfiguration }
import org.apache.hadoop.fs.FileSystem
import org.pac4j.core.profile.{ CommonProfile, ProfileManager }
import org.pac4j.play.PlayWebContext
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import play.api.mvc._
import play.api.test.FakeRequest

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class DatasetControllerSpec extends TestAbstractModule
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ConfigurationInstance
  with FileSystemInstance
  with AkkaInstance {

  implicit lazy val executionContext = actorSystem.dispatchers.lookup("akka.actor.test-dispatcher")

  implicit val fileSystem = FileSystem.getLocal { new HadoopConfiguration }

  protected implicit lazy val materializer = ActorMaterializer.create { actorSystem }

  private def withController[U](f: DatasetController => U) = f { new DatasetController(configuration, sessionStore, ws, actorSystem, executionContext) with TestCatalogClient }

  private def request[A](method: String, uri: String, body: A, authorization: Option[String] = None, headers: Headers = Headers())(action: => Action[A]) = Await.result(
    action {
      FakeRequest(
        method  = method,
        uri     = uri,
        body    = body,
        headers = authorization.fold(headers) { auth => headers.add("Authorization" -> auth) }
      )
    },
    5.seconds
  )

  private val userProfile = {
    val profile = new CommonProfile
    profile.setId("test-user")
    profile.setRemembered(true)
    profile
  }

  private def createSession() = {
    val context = new PlayWebContext(
      FakeRequest(
        method  = "OPTIONS",
        uri     = "/",
        body    = AnyContentAsEmpty,
        headers = Headers()
      ),
      sessionStore
    )
    val profileManager = new ProfileManager[CommonProfile](context)
    profileManager.save(true, userProfile, false)
  }

  private def createData() = {
    val outputStream = fileSystem.create { "test-dir/large/file".asHadoop }
    Random.alphanumeric.grouped(15).take(20).foreach { stream => outputStream.writeUTF(stream.mkString) }
    outputStream.close()
  }

  override def beforeAll() = {
    startAkka()
    createSession()
    createData()
  }

  override def afterAll() = {
    fileSystem.delete("test-dir/large/file".asHadoop, true)
  }

  "A Dataset Controller" when {

    "calling download" must {

      "return 401 when the auth header is missing" in withController { controller =>
        request[AnyContent]("GET", "/dataset-manager/v1/dataset/data/path", AnyContentAsEmpty) {
          controller.getDataset("data/path", "invalid")
        }.header.status should be { 401 }
      }

      "return 400 when format is invalid" in withController { controller =>
        request[AnyContent]("GET", "/dataset-manager/v1/dataset/data/path", AnyContentAsEmpty, Some("Basic:token")) {
          controller.getDataset("data/path", "invalid")
        }.header.status should be { 400 }
      }

      "return 400 when the format is valid but unsupported" in withController { controller =>
        request[AnyContent]("GET", "/dataset-manager/v1/dataset/data/path", AnyContentAsEmpty, Some("Basic:token")) {
          controller.getDataset("data/path", "avro")
        }.header.status should be { 400 }
      }

      "return 404 when the path does not exist" in withController { controller =>
        request[AnyContent]("GET", "/dataset-manager/v1/dataset/path/to/failure", AnyContentAsEmpty, Some("Basic:token")) {
          controller.getDataset("path/to/failure", "csv")
        }.header.status should be { 404 }
      }

      "return 400 when the method is invalid" in withController { controller =>
        request[AnyContent]("GET", "/dataset-manager/v1/dataset/data/path", AnyContentAsEmpty, Some("Basic:token")) {
          controller.getDataset("data/path", "avro", "invalid")
        }.header.status should be { 400 }
      }

      "return 404 when a file is not found" in withController { controller =>
        request[AnyContent]("GET", "/dataset-manager/v1/dataset/unknown/file", AnyContentAsEmpty, Some("Basic:token")) {
          controller.getDataset("unknown/file", "csv", "quick")
        }.header.status should be { 404 }
      }

      "return 307 when a large file is attempted for quick download" in withController { controller =>
        request[AnyContent]("GET", "/dataset-manager/v1/dataset/large/file", AnyContentAsEmpty, Some("Basic:token")) {
          controller.getDataset("large/file", "csv", "quick")
        }.header.status should be { 307 }
      }

    }

  }

}
