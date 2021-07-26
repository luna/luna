package org.enso.languageserver.websocket.json

import buildinfo.Info
import io.circe.literal.JsonStringContext
import org.enso.languageserver.data.Config
import org.enso.testkit.FlakySpec

import java.io.{File, FileOutputStream}

class WorkspaceOperationsTest extends BaseServerTest with FlakySpec {

  override def initializeProjectPackage: Boolean = false

  "workspace/projectInfo" must {
    val packageConfigName = Config.ensoPackageConfigName
    val testYamlPath      = new File(testContentRoot.file, packageConfigName)

    "return the project info" taggedAs Flaky in {
      testYamlPath.delete()
      val packageYamlContents =
        getClass.getClassLoader.getResourceAsStream(packageConfigName)
      val yamlOutStream = new FileOutputStream(testYamlPath)
      yamlOutStream.write(packageYamlContents.readAllBytes())
      yamlOutStream.close()

      val client = getInitialisedWsClient()

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "workspace/projectInfo",
            "id": 1,
            "params": {}
          }
          """)

      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": {
              "projectName" : "Standard",
              "engineVersion" : ${Info.ensoVersion},
              "graalVersion" : ${Info.graalVersion}
            }
          }
          """)
    }

    "return an error when the project configuration cannot be decoded" taggedAs Flaky in {
      testYamlPath.delete()
      val yamlOutStream = new FileOutputStream(testYamlPath)
      yamlOutStream.write(0x00)
      yamlOutStream.close()

      val client = getInitialisedWsClient()

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "workspace/projectInfo",
            "id": 1,
            "params": {}
          }
          """)

      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "error": {
              "code": 1010,
              "message": "Cannot decode the project configuration"
            }
          }
          """)
    }

    "return an error when the project configuration is not present" taggedAs Flaky in {
      testYamlPath.delete()

      val client = getInitialisedWsClient()

      client.send(json"""
          { "jsonrpc": "2.0",
            "method": "workspace/projectInfo",
            "id": 1,
            "params": {}
          }
          """)

      client.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "error": {
              "code": 1003,
              "message": "File not found"
            }
          }
          """)
    }
  }
}
