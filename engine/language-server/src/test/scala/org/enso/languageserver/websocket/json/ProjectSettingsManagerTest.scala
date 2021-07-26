package org.enso.languageserver.websocket.json

import io.circe.literal._
import org.enso.distribution.FileSystem

import java.nio.file.Files

class ProjectSettingsManagerTest extends BaseServerTest {
  override def beforeAll(): Unit = {
    super.beforeAll()

    val editionsDir = getTestDirectory.resolve("test_data").resolve("editions")
    Files.createDirectories(editionsDir)
    FileSystem.writeTextFile(
      editionsDir.resolve("some-edition.yaml"),
      """engine-version: 1.2.3
        |""".stripMargin
    )

    FileSystem.writeTextFile(
      editionsDir.resolve("broken.yaml"),
      """extends: non-existent
        |""".stripMargin
    )
  }

  "ProjectSettingsManager" should {
    "get default settings" in {
      val client = getInitialisedWsClient()
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/getProjectSettings",
            "id": 0
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "result": {
              "parentEdition": ${buildinfo.Info.currentEdition},
              "preferLocalLibraries": true
            }
          }
          """)
    }

    "allow to set local libraries preference and parent edition and reflect " +
    "these changes" in {
      val client = getInitialisedWsClient()
      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/setProjectLocalLibrariesPreference",
            "id": 0,
            "params": {
              "preferLocalLibraries": false
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "result": {
              "needsRestart": true
            }
          }
          """)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/setParentEdition",
            "id": 1,
            "params": {
              "newEditionName": "some-edition"
            }
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": {
              "needsRestart": true
            }
          }
          """)

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "editions/getProjectSettings",
            "id": 2
          }
          """)
      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 2,
            "result": {
              "parentEdition": "some-edition",
              "preferLocalLibraries": false
            }
          }
          """)
    }

    "fail if the provided parent edition is not resolvable" ignore {}
  }
}
