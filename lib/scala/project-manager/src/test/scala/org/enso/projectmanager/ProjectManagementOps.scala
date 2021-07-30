package org.enso.projectmanager

import java.util.UUID
import akka.testkit.TestDuration
import io.circe.Json
import io.circe.syntax._
import io.circe.literal._
import org.enso.editions.SemVerJson._
import io.circe.parser.parse
import nl.gn0s1s.bump.SemVer
import org.enso.projectmanager.data.{MissingComponentAction, Socket}
import org.enso.projectmanager.protocol.ProjectManagementApi.ProjectOpen

import scala.concurrent.duration._

trait ProjectManagementOps { this: BaseServerSpec =>

  def createProject(
    name: String,
    nameSuffix: Option[Int]                                = None,
    projectTemplate: Option[String]                        = None,
    missingComponentAction: Option[MissingComponentAction] = None
  )(implicit client: WsTestClient): UUID = {
    val fields = Seq("name" -> name.asJson) ++
      missingComponentAction
        .map(a => "missingComponentAction" -> a.asJson)
        .toSeq ++
      projectTemplate
        .map(t => "projectTemplate" -> t.asJson)
        .toSeq

    val params  = Json.obj(fields: _*)
    val request = json"""
            { "jsonrpc": "2.0",
              "method": "project/create",
              "id": 0,
              "params": $params
            }
          """
    client.send(request)
    val projectId   = getGeneratedUUID
    val projectName = nameSuffix.fold(name)(n => s"${name}_$n")
    client.expectJson(json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": {
              "projectId": $projectId,
              "projectName": $projectName
            }
          }
          """)
    projectId
  }

  def openProject(
    projectId: UUID
  )(implicit client: WsTestClient): Socket = {
    client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/open",
              "id": 0,
              "params": {
                "projectId": $projectId
              }
            }
          """)
    val Right(openReply) = parse(client.expectMessage(20.seconds.dilated))
    val socket = for {
      result <- openReply.hcursor.downExpectedField("result")
      addr   <- result.downExpectedField("languageServerJsonAddress")
      host   <- addr.downField("host").as[String]
      port   <- addr.downField("port").as[Int]
    } yield Socket(host, port)

    socket.fold(fail(s"Failed to decode json: $openReply", _), identity)
  }

  def openProjectData(implicit client: WsTestClient): ProjectOpen.Result = {
    val Right(openReply) = parse(client.expectMessage(20.seconds.dilated))
    val openResult = for {
      result    <- openReply.hcursor.downExpectedField("result")
      engineVer <- result.downField("engineVersion").as[SemVer]
      jsonAddr  <- result.downExpectedField("languageServerJsonAddress")
      jsonHost  <- jsonAddr.downField("host").as[String]
      jsonPort  <- jsonAddr.downField("port").as[Int]
      binAddr   <- result.downExpectedField("languageServerBinaryAddress")
      binHost   <- binAddr.downField("host").as[String]
      binPort   <- binAddr.downField("port").as[Int]
      name      <- result.downField("projectName").as[String]
      namespace <- result.downField("projectNamespace").as[String]
    } yield {
      val jsonSock = Socket(jsonHost, jsonPort)
      val binSock  = Socket(binHost, binPort)
      ProjectOpen.Result(engineVer, jsonSock, binSock, name, namespace)
    }
    openResult.getOrElse(throw new Exception("Should have worked."))
  }

  def closeProject(
    projectId: UUID
  )(implicit client: WsTestClient): Unit = {
    client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/close",
              "id": 0,
              "params": {
                "projectId": $projectId
              }
            }
          """)
    client.expectJson(
      json"""
          {
            "jsonrpc":"2.0",
            "id":0,
            "result": null
          }
          """,
      20.seconds.dilated
    )
  }

  def deleteProject(
    projectId: UUID
  )(implicit client: WsTestClient): Unit = {
    client.send(json"""
            { "jsonrpc": "2.0",
              "method": "project/delete",
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
            "result": null
          }
          """)
  }

}
