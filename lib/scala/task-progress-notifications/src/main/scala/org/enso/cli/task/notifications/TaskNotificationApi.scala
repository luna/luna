package org.enso.cli.task.notifications

import org.enso.jsonrpc.{HasParams, Method}

import java.util.UUID

object TaskNotificationApi {

  case object TaskStarted extends Method("task/started") {

    case class Params(
      taskId: UUID,
      relatedOperation: String,
      unit: SerializableProgressUnit,
      total: Option[Long]
    )

    implicit val hasParams = new HasParams[this.type] {
      type Params = TaskStarted.Params
    }
  }

  case object TaskProgressUpdate extends Method("task/progress-update") {

    case class Params(
      taskId: UUID,
      message: Option[String],
      done: Long
    )

    implicit val hasParams = new HasParams[this.type] {
      type Params = TaskProgressUpdate.Params
    }
  }

  case object TaskFinished extends Method("task/finished") {

    case class Params(
      taskId: UUID,
      message: Option[String],
      success: Boolean
    )

    implicit val hasParams = new HasParams[this.type] {
      type Params = TaskFinished.Params
    }
  }
}
