---
layout: developer-doc
title: Enso Protocol Project Manager Message Specification
category: language-server
tags: [language-server, protocol, specification]
order: 3
---

# Enso Protocol Project Manager Message Specification

This document contains the specification of the Enso protocol messages that
pertain to the project manager component. Please familiarise yourself with the
[common](./protocol-common.md) features of the protocol before reading this
document.

For information on the design and architecture of the protocol, as well as its
transport formats, please look [here](./protocol-architecture.md).

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Types](#types)
  - [`ProjectMetadata`](#projectmetadata)
  - [`MissingComponentAction`](#missingcomponentaction)
  - [`ProgressUnit`](#progressunit)
  - [`EngineVersion`](#engineversion)
- [Project Management Operations](#project-management-operations)
  - [`project/open`](#projectopen)
  - [`project/close`](#projectclose)
  - [`project/list`](#projectlist)
  - [`project/create`](#projectcreate)
  - [`project/rename`](#projectrename)
  - [`project/delete`](#projectdelete)
  - [`project/listSample`](#projectlistsample)
- [Action Progress Reporting](#action-progress-reporting)
  - [`task/started`](#taskstarted)
  - [`task/progress-update`](#taskprogress-update)
  - [`task/finished`](#taskfinished)
- [Runtime Version Management](#runtime-version-management)
  - [`engine/list-installed`](#enginelist-installed)
  - [`engine/list-available`](#enginelist-available)
  - [`engine/install`](#engineinstall)
  - [`engine/uninstall`](#engineuninstall)
- [Configuration Management](#configuration-management)
  - [`global-config/get`](#global-configget)
  - [`global-config/set`](#global-configset)
  - [`global-config/delete`](#global-configdelete)
- [Logging Service](#logging-service)
  - [`logging-service/get-endpoint`](#logging-serviceget-endpoint)
- [Language Server Management](#language-server-management)
- [Errors](#errors)
  - [`MissingComponentError`](#missingcomponenterror)
  - [`BrokenComponentError`](#brokencomponenterror)
  - [`ProjectManagerUpgradeRequired`](#projectmanagerupgraderequired)
  - [`ComponentInstallationError`](#componentinstallationerror)
  - [`ComponentUninstallationError`](#componentuninstallationerror)
  - [`ComponentRepositoryUnavailable`](#componentrepositoryunavailable)
  - [`ProjectNameValidationError`](#projectnamevalidationerror)
  - [`ProjectDataStoreError`](#projectdatastoreerror)
  - [`ProjectExistsError`](#projectexistserror)
  - [`ProjectNotFoundError`](#projectnotfounderror)
  - [`ProjectOpenError`](#projectopenerror)
  - [`ProjectNotOpenError`](#projectnotopenerror)
  - [`ProjectOpenByOtherPeersError`](#projectopenbyotherpeerserror)
  - [`CannotRemoveOpenProjectError`](#cannotremoveopenprojecterror)
  - [`ProjectCloseError`](#projectcloseerror)
  - [`LanguageServerError`](#languageservererror)
  - [`GlobalConfigurationAccessError`](#globalconfigurationaccesserror)
  - [`ProjectCreateError`](#projectcreateerror)
  - [`LoggingServiceUnavailable`](#loggingserviceunavailable)

<!-- /MarkdownTOC -->

## Types

There are a number of types that are used only within the project server's
protocol messages. These are specified here.

### `ProjectMetadata`

This type represents information about a project.

#### Format

```typescript
interface ProjectMetadata {
  /**
   * The name of the project.
   */
  name: String;

  /**
   * The namespace of the project.
   */
  namespace: String;

  /**
   * The project id.
   */
  id: UUID;

  /**
   * Enso Engine version to use for the project, represented by a semver version
   * string.
   *
   * If the edition associated with the project could not be resolved, the
   * engine version may be missing.
   */
  engineVersion?: String;

  /**
   * The last opened datetime.
   */
  lastOpened?: UTCDateTime;
}
```

### `MissingComponentAction`

This type specifies what action should be taken if a component required to
complete an operation is missing.

- `Fail` will make the operation fail if any components are missing.
- `Install` will try to install any missing components, unless they are marked
  as broken.
- `ForceInstallBroken` will try to install all missing components, even if some
  of them are marked as broken.

#### Format

```typescript
type MissingComponentAction = Fail | Install | ForceInstallBroken;
```

### `ProgressUnit`

This type specifies the unit of progress updates related to a task.

#### Format

```typescript
type ProgressUnit = Bytes | Other;
```

### `EngineVersion`

This type represents an installed or available engine version.

#### Format

```typescript
interface EngineVersion {
  /** Semver string of engine version. */
  version: String;

  /** Specifies if that version is marked as broken. */
  markedAsBroken: bool;
}
```

## Project Management Operations

The primary responsibility of the project managers is to allow users to manage
their projects.

### `project/open`

This message requests that the project manager open a specified project. This
operation also includes spawning an instance of the language server open on the
specified project.

To open a project, an engine version that is specified in project settings needs
to be installed. If `missingComponentAction` is set to `Install` or
`ForceInstallBroken`, this action will install any missing components,
otherwise, an error will be reported if a component is missing. A typical usage
scenario may consist of first trying to open the project without installing
missing components. If that fails with the `MissingComponentError`, the client
can ask the user if they want to install the missing components and re-attempt
the action.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  projectId: UUID;

  /**
   * Specifies how to handle missing components.
   *
   * If not provided, defaults to `Fail`.
   */
  missingComponentAction?: MissingComponentAction;
}
```

#### Result

```typescript
{
  /**
   * The version of the started language server represented by a semver version
   * string.
   */
  engineVersion: String;

  /**
   * The endpoint used for JSON-RPC protocol.
   */
  languageServerJsonAddress: IPWithSocket;

  /**
   * The endpoint used for binary protocol.
   */
  languageServerBinaryAddress: IPWithSocket;

  // The name of the project as it is opened.
  projectName: String;

  // The namespace of the project.
  projectNamespace: String;
}
```

#### Errors

- [`ProjectNotFoundError`](#projectnotfounderror) to signal that the project
  doesn't exist.
- [`ProjectDataStoreError`](#projectdatastoreerror) to signal problems with
  underlying data store.
- [`ProjectOpenError`](#projectopenerror) to signal failures during server boot.
- [`MissingComponentError`](#missingcomponenterror) to signal that the component
  required to open the project was missing (only in case
  `missingComponentAction` was set to `fail`).
- [`BrokenComponentError`](#brokencomponenterror) to signal that the component
  required to open the project was being installed but is marked as broken (only
  in case `missingComponentAction` was set to `install`).
- [`ComponentInstallationError`](#componentinstallationerror) to signal that the
  installation of a missing component has failed.
- [`ProjectManagerUpgradeRequired`](#projectmanagerupgraderequired) to signal
  that the requested engine version requires a more recent project manager, so
  an upgrade has to be performed before continuing.

### `project/close`

This message requests that the project manager close a specified project. This
operation includes shutting down the language server gracefully so that it can
persist state to disk as needed.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface ProjectCloseRequest {
  projectId: UUID;
}
```

#### Result

```typescript
{
}
```

#### Errors

- [`ProjectNotFoundError`](#projectnotfounderror) to signal that the project
  doesn't exist.
- [`ProjectDataStoreError`](#projectdatastoreerror) to signal problems with
  underlying data store.
- [`ProjectCloseError`](#projectcloseerror) to signal failures that occurred
  during language server stoppage.
- [`ProjectNotOpenError`](#projectnotopenerror) to signal cannot close a project
  that is not open.
- [`ProjectOpenByOtherPeersError`](#projectopenbyotherpeerserror) to signal that
  cannot close a project that is open by other clients.

### `project/list`

This message requests that the project manager lists all user's projects. The
list of projects is sorted by the open time.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface ProjectListRequest {
  numberOfProjects?: Int;
}
```

#### Result

```typescript
interface ProjectListResponse {
  projects: [ProjectMetadata];
}
```

#### Errors

- [`ProjectDataStoreError`](#projectdatastoreerror) to signal problems with
  underlying data store.
- [`GlobalConfigurationAccessError`](#globalconfigurationaccesserror) to signal
  that the global configuration file could not be accessed or parsed.

### `project/create`

This message requests the creation of a new project.

To create a project, an engine version associated with it needs to be installed.
Depending on `missingComponentAction`, any components required to complete the
operation are missing will be installed or a failure will be reported.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface ProjectCreateRequest {
  /** Name of the project to create. */
  name: String;

  /**
   * Enso Engine version to use for the project.
   *
   * Possible values are:
   * - a semver version string identifying an Enso engine version,
   * - `default` to use the current default.
   *
   * The field is optional - if it is missing, it is treated as `default`.
   */
  version?: String;

  /**
   * Specifies how to handle missing components.
   *
   * If not provided, defaults to `Fail`.
   */
  missingComponentAction?: MissingComponentAction;
}
```

#### Result

```typescript
interface ProjectOpenResponse {
  projectId: UUID;
}
```

#### Errors

- [`ProjectNameValidationError`](#projectnamevalidationerror) to signal
  validation failures.
- [`ProjectDataStoreError`](#projectdatastoreerror) to signal problems with
  underlying data store.
- [`ProjectExistsError`](#projectexistserror) to signal that the project already
  exists.
- [`MissingComponentError`](#missingcomponenterror) to signal that the component
  required to create the project was missing (only in case
  `missingComponentAction` was set to `fail`).
- [`BrokenComponentError`](#brokencomponenterror) to signal that the component
  required to create the project was being installed but is marked as broken
  (only in case `missingComponentAction` was set to `install`).
- [`ComponentInstallationError`](#componentinstallationerror) to signal that the
  installation of a missing component has failed.
- [`ProjectManagerUpgradeRequired`](#projectmanagerupgraderequired) to signal
  that the requested engine version requires a more recent project manager, so
  an upgrade has to be performed before continuing.

### `project/rename`

This message requests the renaming of a project.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface ProjectRenameRequest {
  projectId: UUID;
  name: String;
}
```

#### Result

```
null
```

#### Errors

- [`ProjectNameValidationError`](#projectnamevalidationerror) to signal
  validation failures.
- [`ProjectDataStoreError`](#projectdatastoreerror) to signal problems with
  underlying data store.
- [`ProjectExistsError`](#projectexistserror) to signal that the project with
  the provided name already exists.
- [`ServiceError`](./protocol-common.md#serviceerror) to signal that the the
  operation timed out.
- [`LanguageServerError`](#languageservererror) to signal generic language
  server failures.

### `project/delete`

This message requests the deletion of a project.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface ProjectDeleteRequest {
  projectId: UUID;
}
```

#### Result

```typescript
{
}
```

#### Errors

- [`ProjectDataStoreError`](#projectdatastoreerror) to signal problems with
  underlying data store.
- [`ProjectNotFoundError`](#projectnotfounderror) to signal that the project
  doesn't exist.
- [`CannotRemoveOpenProjectError`](#cannotremoveopenprojecterror) to signal that
  the project cannot be removed, because is open by at least one user.

### `project/listSample`

This request lists the sample projects that are available to the user.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface ProjectListSampleRequest {
  numProjects: Int;
}
```

#### Result

```typescript
interface ProjectListSampleResponse {
  projects: [ProjectMetadata];
}
```

#### Errors

TBC

## Action Progress Reporting

Some actions, especially those related to installation of new components may
take a long time (for example because big packages need to be downloaded).

The protocol includes notifications tied to such actions that can be used to
display progress bars.

Each task has a lifecycle of being initialized with a `task/started`
notification (which contains a UUID that identifies that task), being updated
with `task/progress-update` and finalized with `task/finished`. `task/finished`
may include an error (but please note that regardless of the task-related error,
the error will also be reported for the original request associated with the
task, for example as `ComponentInstallationError` returned for the
`project/open` request that triggered the installation).

Tasks are sent while an operation is being processed and a single operation may
consist of several (sub)tasks.

For example, when opening a project the flow may be following:

- `project/open` request sent to the server
- notification `task/started` (downloading the archive)
- multiple notifications `task/progress-update` related to that task
- notification `task/finished`
- notification `task/started` (extracting the archive)
- multiple notifications `task/progress-update` related to that task
- notification `task/finished`
- reply to the original `project/open` request

All task progress updates happen within the response/request flow (up to a
possible reordering of messages).

### `task/started`

Indicates that a long running task has been started.

Currently only used when components are being installed to show installation
progress.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface TaskStartNotification {
  /**
   * Unique identifier of the task, used to correlate progress updates and the
   * finished notification.
   */
  taskId: UUID;

  /**
   * Name of the operation this task is related to, for example
   * `project/open`.
   */
  relatedOperation: String;

  /** Unit in which progress of this task is measured. */
  unit: ProgressUnit;

  /**
   * Indicates total expected progress.
   *
   * May be missing, as it is not always known, for example when downloading a
   * file of unknown size or waiting on a lock.
   */
  total?: Long;
}
```

### `task/progress-update`

Indicates a progress update for a specific task.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface TaskUpdateNotification {
  taskId: UUID;

  /** Optional message explaining current status of the task. */
  message?: String;

  /** Indicates amount of progress, for example: count of processed bytes. */
  done: Long;
}
```

### `task/finished`

Indicates that a task has been finished, either successfully or with an error.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface TaskFinishedNotification {
  taskId: UUID;

  /** Optional message informing about task completion. */
  message?: String;

  /** Specifies if the task succeeded or failed. */
  success: bool;
}
```

## Runtime Version Management

### `engine/list-installed`

Lists engine versions currently installed.

Please note that the broken marks associated with each engine currently
represent the state at the moment of installation. As of now, if the broken mark
has been added later, it is not updated automatically.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
null;
```

#### Result

```typescript
interface EngineVersionListResponse {
  /** List of installed engines. */
  versions: [EngineVersion];
}
```

#### Errors

TBC

### `engine/list-available`

Queries the repository to list all engine versions that are available to be
installed.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
null;
```

#### Result

```typescript
interface EngineVersionListResponse {
  /** List of available engines. */
  versions: [EngineVersion];
}
```

#### Errors

- [`ComponentRepositoryUnavailable`](#componentrepositoryunavailable) to signal
  that the component repository could not be reached.

### `engine/install`

Requests to install the specified engine version. If that version is already
installed, it has no effect.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface EngineInstallRequest {
  /** Semver string of engine version that should be installed. */
  version: String;

  /**
   * Specifies whether the engine should be installed even if it is marked as
   * broken.
   *
   * If not provided, defaults to `false`.
   */
  forceInstallBroken?: bool;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`BrokenComponentError`](#brokencomponenterror) to signal that the requested
  engine version is marked as broken (only in case `forceInstallBroken` was set
  to `false`).
- [`ComponentInstallationError`](#componentinstallationerror) to signal that the
  installation of a missing component has failed.
- [`ProjectManagerUpgradeRequired`](#projectmanagerupgraderequired) to signal
  that the requested engine version requires a more recent project manager, so
  an upgrade has to be performed before continuing.

### `engine/uninstall`

Requests to uninstall the specified engine version.

If that version was not installed, it has no effect.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface EngineUninstallRequest {
  /** Semver string of engine version that should be uninstalled. */
  version: String;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`ComponentUninstallationError`](#componentuninstallationerror) to signal that
  the component could not have been uninstalled.

## Configuration Management

### `global-config/get`

Gets a value from the global config.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface GlobalConfigGetRequest {
  key: String;
}
```

#### Result

```typescript
interface GlobalConfigGetResponse {
  /**
   * The value set in the config.
   *
   * The field may be missing if the requested value is not set in the config.
   */
  value?: String;
}
```

#### Errors

- [`GlobalConfigurationAccessError`](#globalconfigurationaccesserror) to signal
  that the configuration file could not be accessed.

### `global-config/set`

Sets a value in the global config.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface GlobalConfigSetRequest {
  key: String;
  value: String;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`GlobalConfigurationAccessError`](#globalconfigurationaccesserror) to signal
  that the configuration file could not be accessed.

### `global-config/delete`

Deletes a value from the global config, or does nothing if it did not exist.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface GlobalConfigDeleteRequest {
  key: String;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`GlobalConfigurationAccessError`](#globalconfigurationaccesserror) to signal
  that the configuration file could not be accessed.

## Logging Service

### `logging-service/get-endpoint`

Requests the endpoint for connecting to the logging service.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
null;
```

#### Result

```typescript
interface LoggingServiceEndpointResponse {
  uri: String;
}
```

#### Errors

- [`LoggingServiceUnavailable`](#loggingserviceunavailable) to signal that the
  logging service is unavailable.

## Language Server Management

The project manager is also responsible for managing the language server. This
means that it needs to be able to spawn the process, but also tell the process
when to shut down.

A language server process is spawned within the `project/open` call. That call
returns endpoints that the client can use to connect to the language server.
When `project/close` is called, the language server is shutdown. Moreover,
between these two calls, the project manager sends heartbeat messages to the
language server to check if it is still running. In case that it has crashed, a
restart is attempted.

## Errors

The project manager component has its own set of errors. This section is not a
complete specification and will be updated as new errors are added.

Besides the required `code` and `message` fields, the errors may have a `data`
field which can store additional error-specific payload.

### `MissingComponentError`

Signals that a component required to complete the action was missing, but the
action did not ask for it to be automatically installed.

```typescript
"error" : {
  "code" : 4020,
  "message" : "Engine 1.2.3 is required to complete the action but it is not installed."
}
```

### `BrokenComponentError`

Signals that a component that was being installed is marked as broken, but the
option to forcibly install broken components was not set.

This error may handled by warning the user about the broken version or
suggesting to upgrade the project and asking to confirm using the broken
version. If the user wants to ignore the warning, the operation can be
reattempted with the option to forcibly install broken components.

```typescript
"error" : {
  "code" : 4021,
  "message" : "Engine 1.2.3 is marked as broken."
}
```

### `ProjectManagerUpgradeRequired`

Signals that installation of a missing compoment has been attempted, but the
required engine version requires a newer version of project manager than what is
currently running.

This error type includes the optional `data` field which is an object with a
field `minimumRequiredVersion` that is a semver string of the project manager
version that is required to complete the related action.

```typescript
"error" : {
  "code" : 4022,
  "message" : "Project manager 1.2.3 is required to install the requested engine. Please upgrade.",
  "data": {
    "minimumRequiredVersion": "1.2.3"
  }
}
```

### `ComponentInstallationError`

Signals that installation of a missing component has been attempted but it has
failed.

```typescript
"error" : {
  "code" : 4023,
  "message" : "A problem occurred when trying to find the release: Cannot find release `enso-1.2.3-not-published`."
}
```

### `ComponentUninstallationError`

Signals that uninstallation of a component has failed.

```typescript
"error" : {
  "code" : 4024,
  "message" : "The requested engine version is not installed."
}
```

### `ComponentRepositoryUnavailable`

Signals that the repository is unavailable and could not be queried (usually
caused by lack of internet connection).

```typescript
"error" : {
  "code" : 4025,
  "message" : "Could not connect to github.com"
}
```

### `ProjectNameValidationError`

Signals validation failures.

```typescript
"error" : {
  "code" : 4001,
  "message" : "Cannot create project with empty name"
}
```

### `ProjectDataStoreError`

Signals problems with underlying data store.

```typescript
"error" : {
  "code" : 4002,
  "message" : "Cannot load project index"
}
```

### `ProjectExistsError`

Signals that the project already exists.

```typescript
"error" : {
  "code" : 4003,
  "message" : "Project with the provided name exists"
}
```

### `ProjectNotFoundError`

Signals that the project doesn't exist.

```typescript
"error" : {
  "code" : 4004,
  "message" : "Project with the provided id does not exist"
}
```

### `ProjectOpenError`

Signals that the project cannot be open due to boot failures.

```typescript
"error" : {
  "code" : 4005,
  "message" : "A boot failure."
}
```

### `ProjectNotOpenError`

Signals that cannot close project that is not open.

```typescript
"error" : {
  "code" : 4006,
  "message" : "Cannot close project that is not open"
}
```

### `ProjectOpenByOtherPeersError`

Signals that cannot close a project that is open by other clients.

```typescript
"error" : {
  "code" : 4007,
  "message" : "Cannot close project because it is open by other peers"
}
```

### `CannotRemoveOpenProjectError`

Signals that cannot remove open project.

```typescript
"error" : {
  "code" : 4008,
  "message" : "Cannot remove open project"
}
```

### `ProjectCloseError`

Signals failures during shutdown of a server.

```typescript
"error" : {
  "code" : 4009,
  "message" : "A shutdown failure."
}
```

### `LanguageServerError`

Signals generic language server errors.

```typescript
"error" : {
  "code" : 4010,
  "message" : "The language server is unresponsive"
}
```

### `GlobalConfigurationAccessError`

Signals that the global configuration file could not be accessed or parsed.

```typescript
"error" : {
  "code" : 4011,
  "message" : "The global configuration file is malformed."
}
```

### `ProjectCreateError`

Signals that an error occurred when creating the project.

```typescript
"error" : {
  "code" : 4012,
  "message" : "Could not create the project."
}
```

### `LoggingServiceUnavailable`

Signals that the logging service is not available.

```typescript
"error" : {
  "code" : 4013,
  "message" : "The logging service has failed to boot."
}
```
