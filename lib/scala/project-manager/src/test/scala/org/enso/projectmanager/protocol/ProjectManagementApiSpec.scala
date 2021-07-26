package org.enso.projectmanager.protocol

import io.circe.literal._
import nl.gn0s1s.bump.SemVer
import org.apache.commons.io.FileUtils
import org.enso.editions.SemVerJson._
import org.enso.projectmanager.{BaseServerSpec, ProjectManagementOps}
import org.enso.testkit.{FlakySpec, RetrySpec}

import java.io.File
import java.nio.file.Paths
import java.util.UUID
import scala.io.Source

class ProjectManagementApiSpec
    extends BaseServerSpec
    with FlakySpec
    with RetrySpec
    with ProjectManagementOps {

  override def beforeEach(): Unit = {
    super.beforeEach()
    gen.reset()
  }

  override val engineToInstall = Some(SemVer(0, 0, 1))

  "project/create" must {

    "check if project name is not empty" taggedAs Flaky in {
      val client = new WsTestClient(address)
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "project/create",
            "id": 1,
            "params": {
              "name": "",
              "missingComponentAction": "Install"
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "error": { "code": 4001, "message": "Project name cannot be empty." }
          }
          """)
    }

    "validate project name for forbidden characters" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 1,
              "params": {
                "name": "Enso-test-roject4/#$$%^@!"
              }
            }
          """)
      client.expectJson(json"""
          {"jsonrpc":"2.0",
          "id":1,
          "error":{
            "code":4001,
            "message":"Project name contains forbidden characters: [-,/,#,$$,%,^,@,!]."
            }
          }
          """)
    }

    "validate project name should start with a capital letter" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 1,
              "params": {
                "name": "enso-test-project"
              }
            }
          """)
      client.expectJson(json"""
          {"jsonrpc":"2.0",
          "id":1,
          "error":{
            "code":4001,
            "message":"Project name should start with a capital letter."
            }
          }
          """)
    }

    "validate project name should be in upper snake case" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 1,
              "params": {
                "name": "EnsoTestProject"
              }
            }
          """)
      client.expectJson(json"""
          {"jsonrpc":"2.0",
          "id":1,
          "error":{
            "code":4001,
            "message":"Project name should be in upper snake case: Enso_Test_Project."
            }
          }
          """)
    }

    "fail when the project with the same name exists" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 1,
              "params": {
                "name": "Foo"
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 1,
            "result" : {
              "projectId" : $getGeneratedUUID
            }
          }
          """)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 2,
              "params": {
                "name": "Foo"
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":2,
            "error":{
              "code":4003,
              "message":"Project with the provided name exists"
            }
          }
          """)
    }

    "create project structure" in {
      val projectName = "Foo"

      implicit val client = new WsTestClient(address)

      createProject(projectName)

      val projectDir  = new File(userProjectDir, projectName)
      val packageFile = new File(projectDir, "package.yaml")
      val mainEnso    = Paths.get(projectDir.toString, "src", "Main.enso").toFile
      val meta        = Paths.get(projectDir.toString, ".enso", "project.json").toFile

      packageFile shouldBe Symbol("file")
      mainEnso shouldBe Symbol("file")
      meta shouldBe Symbol("file")
    }

    "create project with specific version" in {
      implicit val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 1,
              "params": {
                "name": "Foo",
                "version": "0.0.1"
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : 1,
            "result" : {
              "projectId" : $getGeneratedUUID
            }
          }
          """)
    }

    "create a project dir with a suffix if a directory is taken" in {
      val projectName           = "Foo"
      val projectDir            = new File(userProjectDir, projectName)
      val projectDirWithSuffix1 = new File(userProjectDir, projectName + "_1")
      val projectDirWithSuffix2 = new File(userProjectDir, projectName + "_2")
      val packageFile           = new File(projectDirWithSuffix2, "package.yaml")
      projectDir.mkdirs()
      projectDirWithSuffix1.mkdirs()
      implicit val client = new WsTestClient(address)

      createProject(projectName)
      projectDirWithSuffix2.isDirectory shouldBe true
      packageFile.isFile shouldBe true
    }

  }

  "project/delete" must {

    "fail when project doesn't exist" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 1,
              "params": {
                "projectId": ${UUID.randomUUID()}
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":1,
            "error":{
              "code":4004,
              "message":"Project with the provided id does not exist"
            }
          }
          """)

    }

    "fail when project is running" taggedAs Flaky in {
      //given
      implicit val client = new WsTestClient(address)
      val projectId       = createProject("Foo")
      openProject(projectId)
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 2,
              "params": {
                "projectId": $projectId
              }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":2,
            "error":{
              "code":4008,
              "message":"Cannot remove open project"
            }
          }
          """)

      //teardown
      closeProject(projectId)
      deleteProject(projectId)
    }

    "remove project structure" in {
      //given
      val projectName     = "To_Remove"
      implicit val client = new WsTestClient(address)
      val projectId       = createProject(projectName)
      val projectDir      = new File(userProjectDir, projectName)
      projectDir shouldBe Symbol("directory")
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
              "id": 1,
              "params": {
                "projectId": $projectId
              }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":1,
            "result": null
          }
          """)

      projectDir.exists() shouldBe false
    }

  }

  "project/open" must {

    "open a project" taggedAs Flaky in {
      val projectName                   = "Test_Project"
      implicit val client: WsTestClient = new WsTestClient(address)
      val projectId                     = createProject(projectName)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 0,
              "params": {
                "projectId": $projectId
              }
            }
          """)
      val result = openProjectData
      result.projectName shouldEqual projectName
      result.engineVersion shouldEqual SemVer("0.0.1").get
      closeProject(projectId)
      deleteProject(projectId)
    }

    "fail when project doesn't exist" in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 0,
              "params": {
                "projectId": ${UUID.randomUUID()}
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "error":{
              "code":4004,
              "message":"Project with the provided id does not exist"
            }
          }
          """)
    }

    "fail when project's edition could not be resolved" in {
      implicit val client = new WsTestClient(address)
      val projectId       = createProject("Foo")
      setProjectParentEdition(
        "Foo",
        "some_weird_edition_name_that-surely-does-not-exist"
      )

      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 0,
              "params": {
                "projectId": $projectId
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "error":{
              "code" : 4011,
              "message" : "Could not resolve project engine version: Cannot load the edition: Could not find edition `some_weird_edition_name_that-surely-does-not-exist`."
            }
          }
          """)

      deleteProject(projectId)
    }

    "start the Language Server if not running" taggedAs Flaky in {
      //given
      val projectName     = "To_Remove"
      implicit val client = new WsTestClient(address)
      val projectId       = createProject(projectName)
      //when
      val socket = openProject(projectId)
      val languageServerClient =
        new WsTestClient(s"ws://${socket.host}:${socket.port}")
      languageServerClient.send(json"""
          {
            "jsonrpc": "2.0",
            "method": "file/read",
            "id": 1,
            "params": {
              "path": {
                "rootId": ${UUID.randomUUID()},
                "segments": ["src", "Main.enso"]
              }
            }
          }
            """)
      //then
      languageServerClient.expectJson(json"""
          {
            "jsonrpc":"2.0",
             "id":1,
             "error":{"code":6001,"message":"Session not initialised"}}
            """)
      //teardown
      closeProject(projectId)
      deleteProject(projectId)
    }

    "not start new Language Server if one is running" taggedAs Flaky in {
      val client1   = new WsTestClient(address)
      val projectId = createProject("Foo")(client1)
      //when
      val socket1 = openProject(projectId)(client1)
      val client2 = new WsTestClient(address)
      val socket2 = openProject(projectId)(client2)
      //then
      socket2 shouldBe socket1
      //teardown
      client1.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 2,
              "params": {
                "projectId": $projectId
              }
            }
          """)
      client1.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":2,
            "error" : {
              "code" : 4007,
              "message" : "Cannot close project because it is open by other peers"
             }
          }
          """)
      closeProject(projectId)(client2)
      deleteProject(projectId)(client1)
    }

    "start the Language Server after moving the directory" taggedAs Flaky in {
      //given
      val projectName     = "Foo"
      implicit val client = new WsTestClient(address)
      val projectId       = createProject(projectName)

      val newName       = "bar"
      val newProjectDir = new File(userProjectDir, newName)
      FileUtils.moveDirectory(
        new File(userProjectDir, projectName),
        newProjectDir
      )
      val packageFile = new File(newProjectDir, "package.yaml")
      val mainEnso =
        Paths.get(newProjectDir.toString, "src", "Main.enso").toFile
      val meta =
        Paths.get(newProjectDir.toString, ".enso", "project.json").toFile

      packageFile shouldBe Symbol("file")
      mainEnso shouldBe Symbol("file")
      meta shouldBe Symbol("file")

      //when
      val socket = openProject(projectId)
      val languageServerClient =
        new WsTestClient(s"ws://${socket.host}:${socket.port}")
      languageServerClient.send(json"""
          {
            "jsonrpc": "2.0",
            "method": "file/read",
            "id": 1,
            "params": {
              "path": {
                "rootId": ${UUID.randomUUID()},
                "segments": ["src", "Main.enso"]
              }
            }
          }
            """)
      //then
      // 'not initialized' response indicates that language server is running
      languageServerClient.expectJson(json"""
          {
            "jsonrpc":"2.0",
             "id":1,
             "error":{"code":6001,"message":"Session not initialised"}}
            """)
      //teardown
      closeProject(projectId)
      deleteProject(projectId)
    }

    "deduplicate project ids" taggedAs Flaky in {
      val projectName1 = "Foo"

      implicit val client = new WsTestClient(address)

      // given
      val projectId1  = createProject(projectName1)
      val projectDir1 = new File(userProjectDir, projectName1)
      val projectDir2 = new File(userProjectDir, "Test")
      FileUtils.copyDirectory(projectDir1, projectDir2)

      // when
      testClock.moveTimeForward()
      openProject(projectId1)
      val projectOpenTime = testClock.currentTime
      //then
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/list",
              "id": 0,
              "params": { }
            }
          """)
      val projectId2 = getGeneratedUUID
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": {
              "projects": [
                {
                  "name": "Foo",
                  "namespace": "local",
                  "id": $projectId1,
                  "engineVersion": $engineToInstall,
                  "lastOpened": $projectOpenTime
                },
                {
                  "name": "Foo",
                  "namespace": "local",
                  "id": $projectId2,
                  "engineVersion": $engineToInstall,
                  "lastOpened": null
                }
              ]
            }
          }
          """)

      // teardown
      closeProject(projectId1)
      deleteProject(projectId1)
      deleteProject(projectId2)
    }

  }

  "project/close" must {

    "fail when project is not open" taggedAs Flaky in {
      val client = new WsTestClient(address)
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 0,
              "params": {
                "projectId": ${UUID.randomUUID()}
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "error":{
              "code":4006,
              "message":"Cannot close project that is not open"
            }
          }
          """)

    }

    "close project when the requester is the only client" taggedAs Flaky in {
      //given
      implicit val client = new WsTestClient(address)
      val projectId       = createProject("Foo")
      val socket          = openProject(projectId)
      val languageServerClient =
        new WsTestClient(s"ws://${socket.host}:${socket.port}")
      languageServerClient.send("test")
      languageServerClient.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "id" : null,
            "error" : {
              "code" : -32700,
              "message" : "Parse error"
            }
          }
            """)

      //when
      closeProject(projectId)
      languageServerClient.send("test")
      //then
      languageServerClient.expectNoMessage()
      //teardown
      deleteProject(projectId)
    }

  }

  "project/list" must {

    "return a list sorted by creation time if none of projects was opened" in {
      implicit val client = new WsTestClient(address)
      //given
      val fooId = createProject("Foo")
      testClock.moveTimeForward()
      val barId = createProject("Bar")
      testClock.moveTimeForward()
      val bazId = createProject("Baz")
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/list",
              "id": 0,
              "params": { }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": {
              "projects": [
                {
                  "name": "Baz",
                  "namespace": "local",
                  "id": $bazId,
                  "engineVersion": $engineToInstall,
                  "lastOpened": null
                },
                {
                  "name": "Bar",
                  "namespace": "local",
                  "id": $barId,
                  "engineVersion": $engineToInstall,
                  "lastOpened": null
                },
                {
                  "name": "Foo",
                  "namespace": "local",
                  "id": $fooId,
                  "engineVersion": $engineToInstall,
                  "lastOpened": null
                }
              ]
            }
          }
          """)
      deleteProject(fooId)
      deleteProject(barId)
      deleteProject(bazId)
    }

    "return a list of projects even if editions of some of them cannot be resolved" in {
      implicit val client = new WsTestClient(address)
      //given
      val fooId = createProject("Foo")
      testClock.moveTimeForward()
      val barId = createProject("Bar")
      setProjectParentEdition(
        "Bar",
        "some_weird_edition_name_that-surely-does-not-exist"
      )

      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/list",
              "id": 0,
              "params": { }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": {
              "projects": [
                {
                  "name": "Bar",
                  "namespace": "local",
                  "id": $barId,
                  "engineVersion": null,
                  "lastOpened": null
                },
                {
                  "name": "Foo",
                  "namespace": "local",
                  "id": $fooId,
                  "engineVersion": $engineToInstall,
                  "lastOpened": null
                }
              ]
            }
          }
          """)
      deleteProject(fooId)
      deleteProject(barId)
    }

    "returned sorted list of recently opened projects" in {
      implicit val client = new WsTestClient(address)
      //given
      val fooId = createProject("Foo")
      val barId = createProject("Bar")
      testClock.moveTimeForward()
      openProject(fooId)
      val fooOpenTime = testClock.currentTime
      testClock.moveTimeForward()
      openProject(barId)
      val barOpenTime = testClock.currentTime
      testClock.moveTimeForward()
      val bazId = createProject("Baz")
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/list",
              "id": 0,
              "params": {
                "numberOfProjects": 3
              }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": {
              "projects": [
                {
                  "name": "Bar",
                  "namespace": "local",
                  "id": $barId,
                  "engineVersion": $engineToInstall,
                  "lastOpened": $barOpenTime
                },
                {
                  "name": "Foo",
                  "namespace": "local",
                  "id": $fooId,
                  "engineVersion": $engineToInstall,
                  "lastOpened": $fooOpenTime
                },
                {
                  "name": "Baz",
                  "namespace": "local",
                  "id": $bazId,
                  "engineVersion": $engineToInstall,
                  "lastOpened": null
                }
              ]
            }
          }
          """)
      //teardown
      closeProject(fooId)
      closeProject(barId)
      deleteProject(fooId)
      deleteProject(barId)
      deleteProject(bazId)
    }

    "resolve clashing ids" taggedAs Flaky in {
      val projectName1 = "Foo"

      implicit val client = new WsTestClient(address)

      // given
      val projectId1  = createProject(projectName1)
      val projectDir1 = new File(userProjectDir, projectName1)
      val projectDir2 = new File(userProjectDir, "Test")
      FileUtils.copyDirectory(projectDir1, projectDir2)

      // when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/list",
              "id": 0,
              "params": { }
            }
          """)

      //then
      val projectId2 = getGeneratedUUID
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": {
              "projects": [
                {
                  "name": "Foo",
                  "namespace": "local",
                  "id": $projectId1,
                  "engineVersion": $engineToInstall,
                  "lastOpened": null
                },
                {
                  "name": "Foo",
                  "namespace": "local",
                  "id": $projectId2,
                  "engineVersion": $engineToInstall,
                  "lastOpened": null
                }
              ]
            }
          }
          """)

      // teardown
      deleteProject(projectId1)
      deleteProject(projectId2)
    }

  }

  "project/rename" must {

    "rename a project and move project dir" in {
      implicit val client = new WsTestClient(address)
      //given
      val newProjectName = "Bar"
      val projectId      = createProject("Foo")
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/rename",
              "id": 0,
              "params": {
                "projectId": $projectId,
                "name": $newProjectName
              }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": null
          }
          """)
      val projectDir  = new File(userProjectDir, newProjectName)
      val packageFile = new File(projectDir, "package.yaml")
      val buffer      = Source.fromFile(packageFile)
      val lines       = buffer.getLines()
      lines.contains("name: Bar") shouldBe true
      buffer.close()
      //teardown
      deleteProject(projectId)
    }

    "create a project dir with a suffix if a directory is taken" taggedAs Flaky in {
      val oldProjectName  = "Foobar"
      val newProjectName  = "Foo"
      implicit val client = new WsTestClient(address)
      //given
      val projectId         = createProject(oldProjectName)
      val primaryProjectDir = new File(userProjectDir, newProjectName)
      primaryProjectDir.mkdirs()
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/rename",
              "id": 0,
              "params": {
                "projectId": $projectId,
                "name": $newProjectName
              }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": null
          }
          """)
      val projectDir  = new File(userProjectDir, s"${newProjectName}_1")
      val packageFile = new File(projectDir, "package.yaml")
      val buffer      = Source.fromFile(packageFile)
      val lines       = buffer.getLines()
      lines.contains("name: Foo") shouldBe true
      buffer.close()
      //teardown
      deleteProject(projectId)
    }

    "reply with an error when the project with the same name exists" in {
      //given
      implicit val client   = new WsTestClient(address)
      val oldProjectName    = "Foo"
      val newProjectName    = "Bar"
      val projectId         = createProject(oldProjectName)
      val existingProjectId = createProject(newProjectName)
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/rename",
              "id": 0,
              "params": {
                "projectId": $projectId,
                "name": $newProjectName
              }
            }
          """)
      //then
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "error":{
              "code":4003,
              "message":"Project with the provided name exists"
            }
          }
          """)
      //teardown
      deleteProject(projectId)
      deleteProject(existingProjectId)
    }

    "reply with an error when the project doesn't exist" in {
      //given
      implicit val client = new WsTestClient(address)
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/rename",
              "id": 0,
              "params": {
                "projectId": ${UUID.randomUUID()},
                "name": "Bar"
              }
            }
          """)
      client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "error":{
              "code":4004,
              "message":"Project with the provided id does not exist"
            }
          }
          """)
    }

    "check if project name is not empty" in {
      //given
      implicit val client = new WsTestClient(address)
      val projectId       = createProject("Foo")
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/rename",
              "id": 0,
              "params": {
                "projectId": $projectId,
                "name": ""
              }
            }
          """)
      //then
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "error": { "code": 4001, "message": "Project name cannot be empty." }
          }
          """)
      //teardown
      deleteProject(projectId)
    }

    "validate project name for forbidden characters" in {
      //given
      implicit val client = new WsTestClient(address)
      val projectId       = createProject("Foo")
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/rename",
              "id": 0,
              "params": {
                "projectId": $projectId,
                "name": "Enso-test-project4/#$$%^@!"
              }
            }
          """)
      //then
      client.expectJson(json"""
          {"jsonrpc":"2.0",
          "id":0,
          "error":{
            "code":4001,
            "message":"Project name contains forbidden characters: [-,/,#,$$,%,^,@,!]."
            }
          }
          """)
      //teardown
      deleteProject(projectId)
    }

    "validate project name should start with a capital letter" in {
      //given
      implicit val client = new WsTestClient(address)
      val projectId       = createProject("Foo")
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/rename",
              "id": 0,
              "params": {
                "projectId": $projectId,
                "name": "enso-test-project"
              }
            }
          """)
      //then
      client.expectJson(json"""
          {"jsonrpc":"2.0",
          "id":0,
          "error":{
            "code":4001,
            "message":"Project name should start with a capital letter."
            }
          }
          """)
      //teardown
      deleteProject(projectId)
    }

    "validate project name should be in upper snake case" in {
      //given
      implicit val client = new WsTestClient(address)
      val projectId       = createProject("Foo")
      //when
      client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/rename",
              "id": 0,
              "params": {
                "projectId": $projectId,
                "name": "EnsoTestProject"
              }
            }
          """)
      //then
      client.expectJson(json"""
          {"jsonrpc":"2.0",
          "id":0,
          "error":{
            "code":4001,
            "message":"Project name should be in upper snake case: Enso_Test_Project."
            }
          }
          """)
      //teardown
      deleteProject(projectId)
    }

  }
}
