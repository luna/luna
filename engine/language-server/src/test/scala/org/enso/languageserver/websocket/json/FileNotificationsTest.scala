package org.enso.languageserver.websocket.json

import java.io.File

import io.circe.literal._
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.testkit.FlakySpec
import org.enso.text.editing.model.{Position, Range, TextEdit}

class FileNotificationsTest extends BaseServerTest with FlakySpec {

  def file(name: String): File = new File(testContentRoot.file, name)

  "text operations" should {

    "notify runtime about operations with files" taggedAs Flaky in {
      // Interaction:
      // 1.  Client 1 creates a file.
      // 2.  Client 1 opens the file.
      // 3.  Client 1 receives confirmation.
      // 4.  Runtime receives open notification.
      // 5.  Client 2 opens the same file.
      // 6.  Client 2 receives confirmation
      // 7.  Runtime receives no notifications.
      // 8.  Client 1 edits the file.
      // 9.  Client 2 receives a notification.
      // 10. Runtime receives edit notification.
      // 11. Client 1 saves the file.
      // 12. Runtime receives no notifications.
      // 13. Client 1 closes the file.
      // 14. Runtime receives no notifications.
      // 15. Client 2 closes the file.
      // 16. Runtime receives close notification.
      val client1 = getInitialisedWsClient()
      val client2 = getInitialisedWsClient()

      // 1
      client1.send(json"""
          { "jsonrpc": "2.0",
            "method": "file/write",
            "id": 0,
            "params": {
              "path": {
                "rootId": $testContentRootId,
                "segments": [ "foo.txt" ]
              },
              "contents": "123456789"
            }
          }
          """)
      client1.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 0,
            "result": null
          }
          """)

      // 2
      client1.send(json"""
          { "jsonrpc": "2.0",
            "method": "text/openFile",
            "id": 1,
            "params": {
              "path": {
                "rootId": $testContentRootId,
                "segments": [ "foo.txt" ]
              }
            }
          }
          """)
      // 3
      client1.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 1,
            "result": {
              "writeCapability": {
                "method": "text/canEdit",
                "registerOptions": { "path": {
                  "rootId": $testContentRootId,
                  "segments": ["foo.txt"]
                } }
              },
              "content": "123456789",
              "currentVersion": "5795c3d628fd638c9835a4c79a55809f265068c88729a1a3fcdf8522"
            }
          }
          """)
      // 4
      runtimeConnectorProbe.expectMsg(
        Api.Request(
          Api.OpenFileNotification(file("foo.txt"), "123456789")
        )
      )

      // 5
      client2.send(json"""
          { "jsonrpc": "2.0",
            "method": "text/openFile",
            "id": 2,
            "params": {
              "path": {
                "rootId": $testContentRootId,
                "segments": [ "foo.txt" ]
              }
            }
          }
          """)
      // 6
      client2.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 2,
            "result": {
              "writeCapability": null,
              "content": "123456789",
              "currentVersion": "5795c3d628fd638c9835a4c79a55809f265068c88729a1a3fcdf8522"
            }
          }
          """)
      // 7
      runtimeConnectorProbe.expectNoMessage()

      // 8
      client1.send(json"""
          { "jsonrpc": "2.0",
            "method": "text/applyEdit",
            "id": 3,
            "params": {
              "edit": {
                "path": {
                  "rootId": $testContentRootId,
                  "segments": [ "foo.txt" ]
                },
                "oldVersion": "5795c3d628fd638c9835a4c79a55809f265068c88729a1a3fcdf8522",
                "newVersion": "7602967cab172183d1a67ea40cb8e92e23218764bc9934c3795fcea5",
                "edits": [
                  {
                    "range": {
                      "start": { "line": 0, "character": 0 },
                      "end": { "line": 0, "character": 0 }
                    },
                    "text": "bar"
                  }
                ]
              }
            }
          }
          """)
      client1.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 3,
            "result": null
          }
          """)
      // 9
      client2.expectJson(json"""
          {
            "jsonrpc" : "2.0",
            "method" : "text/didChange",
            "params" : {
              "edits" : [
                {
                  "path" : {
                    "rootId" : $testContentRootId,
                    "segments" : [
                      "foo.txt"
                    ]
                  },
                  "edits" : [
                    {
                      "range" : {
                        "start" : { "line" : 0, "character" : 0 },
                        "end" : { "line" : 0, "character" : 0 }
                      },
                      "text" : "bar"
                    }
                  ],
                  "oldVersion" : "5795c3d628fd638c9835a4c79a55809f265068c88729a1a3fcdf8522",
                  "newVersion" : "7602967cab172183d1a67ea40cb8e92e23218764bc9934c3795fcea5"
                }
              ]
            }
          }
          """)
      // 10
      runtimeConnectorProbe.expectMsg(
        Api.Request(
          Api.EditFileNotification(
            file("foo.txt"),
            Seq(TextEdit(Range(Position(0, 0), Position(0, 0)), "bar"))
          )
        )
      )

      // 11
      client1.send(json"""
          { "jsonrpc": "2.0",
            "method": "text/save",
            "id": 4,
            "params": {
              "path": {
                "rootId": $testContentRootId,
                "segments": [ "foo.txt" ]
              },
              "currentVersion": "7602967cab172183d1a67ea40cb8e92e23218764bc9934c3795fcea5"
            }
          }
          """)
      client1.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 4,
            "result": null
          }
          """)
      // 12
      runtimeConnectorProbe.expectNoMessage()

      // 13
      client1.send(json"""
          { "jsonrpc": "2.0",
            "method": "text/closeFile",
            "id": 5,
            "params": {
              "path": {
                "rootId": $testContentRootId,
                "segments": [ "foo.txt" ]
              }
            }
          }
          """)
      client1.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 5,
            "result": null
          }
          """)
      // 14
      runtimeConnectorProbe.expectNoMessage()

      // 15
      client2.send(json"""
          { "jsonrpc": "2.0",
            "method": "text/closeFile",
            "id": 6,
            "params": {
              "path": {
                "rootId": $testContentRootId,
                "segments": [ "foo.txt" ]
              }
            }
          }
          """)
      client2.expectJson(json"""
          { "jsonrpc": "2.0",
            "id": 6,
            "result": null
          }
          """)
      // 16
      runtimeConnectorProbe.expectMsg(
        Api.Request(Api.CloseFileNotification(file("foo.txt")))
      )
    }
  }

}
