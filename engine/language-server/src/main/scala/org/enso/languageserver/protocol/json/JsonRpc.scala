package org.enso.languageserver.protocol.json

import io.circe.generic.auto._
import org.enso.cli.task.notifications.TaskNotificationApi.{
  TaskFinished,
  TaskProgressUpdate,
  TaskStarted
}
import org.enso.jsonrpc.Protocol
import org.enso.languageserver.capability.CapabilityApi.{
  AcquireCapability,
  ForceReleaseCapability,
  GrantCapability,
  ReleaseCapability
}
import org.enso.languageserver.filemanager.FileManagerApi._
import org.enso.languageserver.io.InputOutputApi._
import org.enso.languageserver.monitoring.MonitoringApi.{InitialPing, Ping}
import org.enso.languageserver.refactoring.RefactoringApi.RenameProject
import org.enso.languageserver.runtime.ExecutionApi._
import org.enso.languageserver.search.SearchApi._
import org.enso.languageserver.runtime.VisualisationApi._
import org.enso.languageserver.session.SessionApi.InitProtocolConnection
import org.enso.languageserver.text.TextApi._
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.workspace.WorkspaceApi.ProjectInfo

object JsonRpc {

  /** A description of supported JSON RPC messages.
    */
  val protocol: Protocol = Protocol.empty
    .registerRequest(Ping)
    .registerRequest(InitialPing)
    .registerRequest(InitProtocolConnection)
    .registerRequest(AcquireCapability)
    .registerRequest(ReleaseCapability)
    .registerRequest(WriteFile)
    .registerRequest(ReadFile)
    .registerRequest(CreateFile)
    .registerRequest(OpenFile)
    .registerRequest(CloseFile)
    .registerRequest(SaveFile)
    .registerRequest(ApplyEdit)
    .registerRequest(DeleteFile)
    .registerRequest(CopyFile)
    .registerRequest(MoveFile)
    .registerRequest(ExistsFile)
    .registerRequest(ListFile)
    .registerRequest(TreeFile)
    .registerRequest(InfoFile)
    .registerRequest(ChecksumFile)
    .registerRequest(RedirectStandardOutput)
    .registerRequest(RedirectStandardError)
    .registerRequest(SuppressStandardOutput)
    .registerRequest(SuppressStandardError)
    .registerRequest(FeedStandardInput)
    .registerRequest(ExecutionContextCreate)
    .registerRequest(ExecutionContextDestroy)
    .registerRequest(ExecutionContextPush)
    .registerRequest(ExecutionContextPop)
    .registerRequest(ExecutionContextRecompute)
    .registerRequest(ExecuteExpression)
    .registerRequest(AttachVisualisation)
    .registerRequest(DetachVisualisation)
    .registerRequest(ModifyVisualisation)
    .registerRequest(GetSuggestionsDatabase)
    .registerRequest(GetSuggestionsDatabaseVersion)
    .registerRequest(InvalidateSuggestionsDatabase)
    .registerRequest(Completion)
    .registerRequest(Import)
    .registerRequest(RenameProject)
    .registerRequest(ProjectInfo)
    .registerRequest(EditionsListAvailable)
    .registerRequest(EditionsResolve)
    .registerRequest(EditionsGetProjectSettings)
    .registerRequest(EditionsSetParentEdition)
    .registerRequest(EditionsSetLocalLibrariesPreference)
    .registerRequest(EditionsListDefinedLibraries)
    .registerRequest(LibraryListLocal)
    .registerRequest(LibraryCreate)
    .registerRequest(LibraryGetMetadata)
    .registerRequest(LibrarySetMetadata)
    .registerRequest(LibraryPublish)
    .registerRequest(LibraryPreinstall)
    .registerNotification(TaskStarted)
    .registerNotification(TaskProgressUpdate)
    .registerNotification(TaskFinished)
    .registerNotification(ForceReleaseCapability)
    .registerNotification(GrantCapability)
    .registerNotification(TextDidChange)
    .registerNotification(EventFile)
    .registerNotification(ContentRootAdded)
    .registerNotification(ContentRootRemoved)
    .registerNotification(ExecutionContextExpressionUpdates)
    .registerNotification(ExecutionContextExecutionFailed)
    .registerNotification(ExecutionContextExecutionStatus)
    .registerNotification(StandardOutputAppended)
    .registerNotification(StandardErrorAppended)
    .registerNotification(WaitingForStandardInput)
    .registerNotification(SuggestionsDatabaseUpdates)
    .registerNotification(VisualisationEvaluationFailed)
}
