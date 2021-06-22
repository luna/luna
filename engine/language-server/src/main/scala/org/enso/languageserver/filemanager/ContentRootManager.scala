package org.enso.languageserver.filemanager

import java.io.File
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait ContentRootManager {
  def getContentRoots(implicit
    ec: ExecutionContext
  ): Future[List[ContentRootWithFile]]
  def findContentRoot(id: UUID)(implicit
    ec: ExecutionContext
  ): Future[ContentRootWithFile]
  def findRelativePath(path: File)(implicit
    ec: ExecutionContext
  ): Future[Option[Path]]
}

object ContentRootManager {
  case class ContentRootNotFound(id: UUID)
      extends RuntimeException(s"Content root $id could not be found.")
}
