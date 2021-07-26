package org.enso.projectmanager.data

import java.time.OffsetDateTime
import java.util.UUID

import nl.gn0s1s.bump.SemVer

/** Contains project metadata.
  *
  * @param name the name of the project
  * @param namespace the namespace of the project
  * @param id the project id
  * @param engineVersion version of the engine associated with the project, it
  *                      may be missing if project's edition could not be loaded
  * @param lastOpened the last opened datetime
  */
case class ProjectMetadata(
  name: String,
  namespace: String,
  id: UUID,
  engineVersion: Option[SemVer],
  lastOpened: Option[OffsetDateTime]
)
