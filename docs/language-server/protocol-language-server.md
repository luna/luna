---
layout: developer-doc
title: Enso Protocol Language Server Message Specification
category: language-server
tags: [language-server, protocol, specification]
order: 4
---

# Enso Protocol Language Server Message Specification

This document contains the specification of the Enso protocol messages that
pertain to the language server component. Please familiarise yourself with the
[common](./protocol-common.md) features of the protocol before reading this
document.

For information on the design and architecture of the protocol, as well as its
transport formats, please look [here](./protocol-architecture).

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Types](#types)
  - [`ExpressionId`](#expressionid)
  - [`ContextId`](#contextid)
  - [`StackItem`](#stackitem)
  - [`MethodPointer`](#methodpointer)
  - [`ProfilingInfo`](#profilinginfo)
  - [`ExpressionUpdate`](#expressionupdate)
  - [`ExpressionUpdatePayload`](#expressionupdatepayload)
  - [`VisualisationConfiguration`](#visualisationconfiguration)
  - [`SuggestionEntryArgument`](#suggestionentryargument)
  - [`SuggestionEntry`](#suggestionentry)
  - [`SuggestionEntryType`](#suggestionentrytype)
  - [`SuggestionId`](#suggestionid)
  - [`SuggestionsDatabaseEntry`](#suggestionsdatabaseentry)
  - [`SuggestionsOrderDatabaseEntry`](#suggestionsorderdatabaseentry)
  - [`FieldAction`](#fieldaction)
  - [`FieldUpdate`](#fieldupdate)
  - [`SuggestionArgumentUpdate`](#suggestionargumentupdate)
  - [`SuggestionsDatabaseUpdate`](#suggestionsdatabaseupdate)
  - [`SuggestionsOrderDatabaseUpdate`](#suggestionsorderdatabaseupdate)
  - [`Export`](#export)
  - [`File`](#file)
  - [`DirectoryTree`](#directorytree)
  - [`FileAttributes`](#fileattributes)
  - [`UTCDateTime`](#utcdatetime)
  - [`FileEventKind`](#fileeventkind)
  - [`Position`](#position)
  - [`Range`](#range)
  - [`TextEdit`](#textedit)
  - [`DiagnosticType`](#diagnostictype)
  - [`StackTraceElement`](#stacktraceelement)
  - [`Diagnostic`](#diagnostic)
  - [`SHA3-224`](#sha3-224)
  - [`FileEdit`](#fileedit)
  - [`FileContents`](#filecontents)
  - [`FileSystemObject`](#filesystemobject)
  - [`WorkspaceEdit`](#workspaceedit)
  - [`EnsoDigest`](#ensodigest)
  - [`FileSegment`](#filesegment)
  - [`ContentRoot`](#contentroot)
  - [`LibraryEntry`](#libraryentry)
  - [`EditionReference`](#editionreference)
- [Connection Management](#connection-management)
  - [`session/initProtocolConnection`](#sessioninitprotocolconnection)
  - [`session/initBinaryConnection`](#sessioninitbinaryconnection)
- [Capability Management](#capability-management)
  - [`capability/acquire`](#capabilityacquire)
  - [`capability/release`](#capabilityrelease)
  - [`capability/granted`](#capabilitygranted)
  - [`capability/forceReleased`](#capabilityforcereleased)
- [Capabilities](#capabilities)
  - [`text/canEdit`](#textcanedit)
  - [`file/receivesTreeUpdates`](#filereceivestreeupdates)
  - [`executionContext/canModify`](#executioncontextcanmodify)
  - [`executionContext/receivesUpdates`](#executioncontextreceivesupdates)
  - [`search/receivesSuggestionsDatabaseUpdates`](#searchreceivessuggestionsdatabaseupdates)
- [File Management Operations](#file-management-operations)
  - [`file/write`](#filewrite)
  - [`file/read`](#fileread)
  - [`file/writeBinary`](#filewritebinary)
  - [`file/readBinary`](#filereadbinary)
  - [`file/writeBytes`](#filewritebytes)
  - [`file/readBytes`](#filereadbytes)
  - [`file/create`](#filecreate)
  - [`file/delete`](#filedelete)
  - [`file/copy`](#filecopy)
  - [`file/move`](#filemove)
  - [`file/exists`](#fileexists)
  - [`file/tree`](#filetree)
  - [`file/list`](#filelist)
  - [`file/info`](#fileinfo)
  - [`file/checksum`](#filechecksum)
  - [`file/checksumBytes`](#filechecksumbytes)
  - [`file/event`](#fileevent)
  - [`file/rootAdded`](#filerootadded)
  - [`file/rootRemoved`](#filerootremoved)
- [Text Editing Operations](#text-editing-operations)
  - [`text/openFile`](#textopenfile)
  - [`text/closeFile`](#textclosefile)
  - [`text/save`](#textsave)
  - [`text/applyEdit`](#textapplyedit)
  - [`text/didChange`](#textdidchange)
- [Workspace Operations](#workspace-operations)
  - [`workspace/projectInfo`](#workspaceprojectinfo)
  - [`workspace/undo`](#workspaceundo)
  - [`workspace/redo`](#workspaceredo)
- [Monitoring](#monitoring)
  - [`heartbeat/ping`](#heartbeatping)
  - [`heartbeat/init`](#heartbeatinit)
- [Refactoring](#refactoring)
  - [`refactoring/renameProject`](#refactoringrenameproject)
- [Execution Management Operations](#execution-management-operations)
  - [Execution Management Example](#execution-management-example)
  - [Create Execution Context](#create-execution-context)
  - [Push Item](#push-item)
  - [Pop Item](#pop-item)
  - [`executionContext/create`](#executioncontextcreate)
  - [`executionContext/destroy`](#executioncontextdestroy)
  - [`executionContext/fork`](#executioncontextfork)
  - [`executionContext/push`](#executioncontextpush)
  - [`executionContext/pop`](#executioncontextpop)
  - [`executionContext/recompute`](#executioncontextrecompute)
  - [`executionContext/expressionUpdates`](#executioncontextexpressionupdates)
  - [`executionContext/executionFailed`](#executioncontextexecutionfailed)
  - [`executionContext/executionStatus`](#executioncontextexecutionstatus)
  - [`executionContext/executeExpression`](#executioncontextexecuteexpression)
  - [`executionContext/attachVisualisation`](#executioncontextattachvisualisation)
  - [`executionContext/detachVisualisation`](#executioncontextdetachvisualisation)
  - [`executionContext/modifyVisualisation`](#executioncontextmodifyvisualisation)
  - [`executionContext/visualisationUpdate`](#executioncontextvisualisationupdate)
  - [`executionContext/visualisationEvaluationFailed`](#executioncontextvisualisationevaluationfailed)
- [Search Operations](#search-operations)
  - [Suggestions Database Example](#suggestions-database-example)
  - [`search/getSuggestionsDatabase`](#searchgetsuggestionsdatabase)
  - [`search/invalidateSuggestionsDatabase`](#searchinvalidatesuggestionsdatabase)
  - [`search/getSuggestionsDatabaseVersion`](#searchgetsuggestionsdatabaseversion)
  - [`search/suggestionsDatabaseUpdate`](#searchsuggestionsdatabaseupdate)
  - [`search/suggestionsOrderDatabaseUpdate`](#searchsuggestionsorderdatabaseupdate)
  - [`search/completion`](#searchcompletion)
  - [`search/import`](#searchimport)
- [Input/Output Operations](#inputoutput-operations)
  - [`io/redirectStandardOutput`](#ioredirectstandardoutput)
  - [`io/suppressStandardOutput`](#iosuppressstandardoutput)
  - [`io/standardOutputAppended`](#iostandardoutputappended)
  - [`io/redirectStandardError`](#ioredirectstandarderror)
  - [`io/suppressStandardError`](#iosuppressstandarderror)
  - [`io/standardErrorAppended`](#iostandarderrorappended)
  - [`io/feedStandardInput`](#iofeedstandardinput)
  - [`io/waitingForStandardInput`](#iowaitingforstandardinput)
- [Library-Related Operations](#library-related-operations)
  - [`editions/listAvailable`](#editionslistavailable)
  - [`editions/resolve`](#editionsresolve)
  - [`editions/getProjectSettings`](#editionsgetprojectsettings)
  - [`editions/setProjectParentEdition`](#editionssetprojectparentedition)
  - [`editions/setProjectLocalLibrariesPreference`](#editionssetprojectlocallibrariespreference)
  - [`editions/listDefinedLibraries`](#editionslistdefinedlibraries)
  - [`library/listLocal`](#librarylistlocal)
  - [`library/create`](#librarycreate)
  - [`library/getMetadata`](#librarygetmetadata)
  - [`library/setMetadata`](#librarysetmetadata)
  - [`library/publish`](#librarypublish)
  - [`library/preinstall`](#librarypreinstall)
- [Errors](#errors-74)
  - [`Error`](#error)
  - [`AccessDeniedError`](#accessdeniederror)
  - [`FileSystemError`](#filesystemerror)
  - [`ContentRootNotFoundError`](#contentrootnotfounderror)
  - [`FileNotFound`](#filenotfound)
  - [`FileExists`](#fileexists)
  - [`OperationTimeoutError`](#operationtimeouterror)
  - [`NotDirectory`](#notdirectory)
  - [`NotFile`](#notfile)
  - [`CannotOverwrite`](#cannotoverwrite)
  - [`ReadOutOfBounds`](#readoutofbounds)
  - [`CannotDecode`](#cannotdecode)
  - [`StackItemNotFoundError`](#stackitemnotfounderror)
  - [`ContextNotFoundError`](#contextnotfounderror)
  - [`EmptyStackError`](#emptystackerror)
  - [`InvalidStackItemError`](#invalidstackitemerror)
  - [`ModuleNotFoundError`](#modulenotfounderror)
  - [`VisualisationNotFoundError`](#visualisationnotfounderror)
  - [`VisualisationExpressionError`](#visualisationexpressionerror)
  - [`FileNotOpenedError`](#filenotopenederror)
  - [`TextEditValidationError`](#texteditvalidationerror)
  - [`InvalidVersionError`](#invalidversionerror)
  - [`WriteDeniedError`](#writedeniederror)
  - [`CapabilityNotAcquired`](#capabilitynotacquired)
  - [`SessionNotInitialisedError`](#sessionnotinitialisederror)
  - [`SessionAlreadyInitialisedError`](#sessionalreadyinitialisederror)
  - [`ResourcesInitializationError`](#resourcesinitializationerror)
  - [`SuggestionsDatabaseError`](#suggestionsdatabaseerror)
  - [`ProjectNotFoundError`](#projectnotfounderror)
  - [`ModuleNameNotResolvedError`](#modulenamenotresolvederror)
  - [`SuggestionNotFoundError`](#suggestionnotfounderror)
  - [`EditionNotFoundError`](#editionnotfounderror)
  - [`LibraryAlreadyExists`](#libraryalreadyexists)
  - [`LibraryRepositoryAuthenticationError`](#libraryrepositoryauthenticationerror)
  - [`LibraryPublishError`](#librarypublisherror)
  - [`LibraryUploadError`](#libraryuploaderror)
  - [`LibraryDownloadError`](#librarydownloaderror)
  - [`LocalLibraryNotFound`](#locallibrarynotfound)
  - [`LibraryNotResolved`](#librarynotresolved)

<!-- /MarkdownTOC -->

## Types

There are a number of types that are used only within the language server's
protocol messages. These are specified here.

### `ExpressionId`

An identifier used for Enso expressions.

```typescript
type ExpressionId = UUID;
```

### `ContextId`

An identifier used for execution contexts.

```typescript
type ContextId = UUID;
```

### `StackItem`

A representation of an executable position in code, used by the execution APIs.

`ExplicitCall` is a call performed at the top of the stack, to initialize the
context with first execution. The `thisArgumentsPosition` field can be omitted,
in which case the context will try to infer the argument on a best-effort basis.
E.g. for a module-level method, or a method defined on a parameter-less atom
type, `this` will be substituted for the unambiguous singleton instance.

`LocalCall` is a call corresponding to "entering a function call".

```typescript
type StackItem = ExplicitCall | LocalCall;

interface ExplicitCall {
  methodPointer: MethodPointer;
  thisArgumentExpression?: String;
  positionalArgumentsExpressions: String[];
}

interface LocalCall {
  expressionId: ExpressionId;
}
```

### `MethodPointer`

Points to a method definition.

```typescript
interface MethodPointer {
  /** The fully qualified module name. */
  module: String;

  /** The type on which the method is defined. */
  definedOnType: String;

  /** The method name. */
  name: String;
}
```

### `ProfilingInfo`

Profiling information on an executed expression. It is implemented as a union as
additional types of information will be added in the future.

```typescript
type ProfilingInfo = ExecutionTime;
```

Where:

```typescript
interface ExecutionTime {
  /** The time elapsed during the expression's evaluation, in nanoseconds */
  nanoTime: Number;
}
```

### `ExpressionUpdate`

An update about the computed expression.

```typescript
interface ExpressionUpdate {
  /**
   * The id of updated expression.
   */
  expressionId: ExpressionId;

  /**
   * The updated type of the expression.
   */
  type?: String;

  /**
   * The updated pointer to the method call.
   */
  methodPointer?: SuggestionId;

  /**
   * Profiling information about the expression.
   */
  profilingInfo: ProfilingInfo[];

  /**
   * Wether or not the expression's value came from the cache.
   */
  fromCache: bool;

  /**
   * An extra information about the computed value.
   */
  payload: ExpressionUpdatePayload;
}
```

### `ExpressionUpdatePayload`

An information about the computed value.

```typescript
type ExpressionUpdatePayload = Value | DatafalowError | Panic;

/**
 * An empty payload. Indicates that the expression was computed to a value.
 */
interface Value {}

/**
 * Indicates that the expression was computed to an error.
 */
interface DataflowError {
  /**
   * The list of expressions leading to the root error.
   */
  trace: ExpressionId[];
}

/**
 * Indicates that the expression failed with the runtime exception.
 */
interface Panic {
  /**
   * The error message.
   */
  message: String;

  /**
   * The stack trace.
   */
  trace: ExpressionId[];
}
```

### `VisualisationConfiguration`

A configuration object for properties of the visualisation.

```typescript
interface VisualisationConfiguration {
  /**
   * An execution context of the visualisation.
   */
  executionContextId: UUID;
  /**
   * A qualified name of the module containing the expression which creates
   * visualisation.
   */
  visualisationModule: String;
  /**
   * The expression that creates a visualisation.
   */
  expression: String;
}
```

### `SuggestionEntryArgument`

The argument of a [`SuggestionEntry`](#suggestionentry).

#### Format

```typescript
// The argument of an atom, method or function suggestion
interface SuggestionEntryArgument {
  // The argument name
  name: string;
  // The arguement type. String 'Any' is used to specify genric types
  type: string;
  // Indicates whether the argument is lazy
  isSuspended: bool;
  // Indicates whether the argument has default value
  hasDefault: bool;
  // Optional default value
  defaultValue?: string;
}
```

### `SuggestionEntry`

The language construct that can be returned as a suggestion.

#### Format

```typescript
// The definition scope
interface SuggestionEntryScope {
  // The start position of the definition scope
  start: Position;
  // The end position of the definition scope
  end: Position;
}

// A type of suggestion entries.
type SuggestionEntry =
  // A module
  | SuggestionEntryModule
  // A value constructor
  | SuggestionEntryAtom
  // A method defined on a type
  | SuggestionEntryMethod
  // A function
  | SuggestionEntryFunction
  // A local value
  | SuggestionEntryLocal;

interface SuggestionEntryModule {
  /** The fully qualified module name. */
  module: string;

  /** The documentation string. */
  documentation?: string;

  /** The fully qualified module name re-exporting this module. */
  reexport?: string;

  /** The rendered HTMl of the documentation string. */

  documentationHtml?: string;
}

interface SuggestionEntryAtom {
  /** The external id. */
  externalId?: UUID;

  /** The atom name. */
  name: string;

  /** The module name where the atom is defined. */
  module: string;

  /** The list of arguments. */
  arguments: SuggestionEntryArgument[];

  /** The type of an atom. */
  returnType: string;

  /** The documentation string. */
  documentation?: string;

  /** The fully qualified module name re-exporting this module. */
  reexport?: string;

  /** The rendered HTMl of the documentation string. */

  documentationHtml?: string;
}

interface SuggestionEntryMethod {
  /** The external id. */
  externalId?: UUID;

  /** The method name. */
  name: string;

  /** The module name where this method is defined. */
  module: string;

  /** The list of arguments. */
  arguments: SuggestionEntryArgument[];

  /** The method self type. */
  selfType: string;

  /** The return type of this method. */
  returnType: string;

  /** The documentation string. */
  documentation?: string;

  /** The fully qualified module name re-exporting this module. */
  reexport?: string;

  /** The rendered HTMl of the documentation string. */

  documentationHtml?: string;
}

interface SuggestionEntryFunction {
  /** The external id. */
  externalId?: UUID;

  /** The function name. */
  name: string;

  /** The module name where this function is defined. */
  module: string;

  /** The list of arguments. */
  arguments: SuggestionEntryArgument[];

  /** The function return type. */
  returnType: string;

  /** The scope where the function is defined. */
  scope: SuggestionEntryScope;

  /** The rendered HTMl of the documentation string. */

  documentationHtml?: string;
}

interface SuggestionEntryLocal {
  /** The external id. */
  externalId?: UUID;

  /** The name of a value. */
  name: string;

  /** The module where this value is defined. */
  module: string;

  /** The type of a value. */
  returnType: string;

  /** The scope where the value is defined. */
  scope: SuggestionEntryScope;

  /** The rendered HTMl of the documentation string. */

  documentationHtml?: string;
}
```

### `SuggestionEntryType`

The suggestion entry type that is used as a filter in search requests.

#### Format

```typescript
// The kind of a suggestion.
type SuggestionEntryType = Module | Atom | Method | Function | Local;
```

### `SuggestionId`

The suggestion entry id of the suggestions database.

#### Format

```typescript
type SuggestionId = number;
```

### `SuggestionsDatabaseEntry`

#### Format

The entry in the suggestions database.

```typescript
interface SuggestionsDatabaseEntry {
  /**
   * The suggestion entry id.
   */
  id: SuggestionId;

  /**
   * The suggestion entry.
   */
  suggestion: SuggestionEntry;
}
```

### `SuggestionsOrderDatabaseEntry`

The entry in the suggestions order database.

#### Format

```typescript
interface SuggestionsOrderDatabaseEntry {
  /**
   * The unique identifier of a suggestion referring to the `id` identifier of
   * the suggestions database.
   */
  suggestionId: SuggestionId;

  /**
   * The suggestion that goes before this one in the source file.
   */
  prevId?: SuggestionId;

  /**
   * Ths suggestion that goes after this one in the source file.
   */
  nextId?: SuggestionId;
}
```

### `FieldAction`

The modifying action on a record field.

#### Format

```typescript
type FieldAction = Remove | Set;
```

### `FieldUpdate`

An object representing a modification of a field in a record.

#### Format

```typescript
interface FieldUpdate<T> {
  /**
   * The modifying action.
   */
  tag: FieldAction;

  /**
   * The updated value.
   */
  value?: T;
}
```

### `SuggestionArgumentUpdate`

An operation applied to the suggestion argument.

#### Format

```typescript
type SuggestionArgumentUpdate = Add | Remove | Modify;

interface Add {
  /**
   * The position of the argument.
   */
  index: int;

  /**
   * The argument to add.
   */
  argument: SuggestionEntryArgument;
}

interface Remove {
  /**
   * The position of the argument.
   */
  index: int;
}

interface Modify {
  /**
   * The position of the argument.
   */
  index: int;

  /**
   * The name to update.
   */
  name?: FieldUpdate<String>;

  /**
   * The argument type to update.
   */
  reprType?: FieldUpdate<String>;

  /**
   * The isSuspended flag to update.
   */
  isSuspended?: FieldUpdate<Boolean>;

  /**
   * The hasDefault flag to update.
   */
  hasDefault?: FieldUpdate<Boolean>;

  /**
   * The default value to update.
   */
  defaultValue?: FieldUpdate<String>;
}
```

### `SuggestionsDatabaseUpdate`

The update of the suggestions database.

#### Format

```typescript
/**
 * The kind of the suggestions database update.
 */
type SuggestionsDatabaseUpdate = Add | Remove | Modify;

interface Add {
  /**
   * Suggestion entry id.
   */
  id: SuggestionId;

  /**
   * Suggestion entry.
   */
  suggestion: SuggestionEntry;
}

interface Remove {
  /**
   * Suggestion entry id.
   */
  id: SuggestionId;
}

interface Modify {
  /**
   * Suggestion entry id.
   */
  id: SuggestionId;

  /**
   * The external id to update.
   */
  externalId?: FieldUpdate<UUID>;

  /**
   * The list of argument updates.
   */
  arguments?: SuggestionArgumentUpdate[];

  /**
   * The module name to update.
   */
  module?: FieldUpdate<String>;

  /**
   * The self type to update.
   */
  selfType?: FieldUpdate<String>;

  /**
   * The return type to update.
   */
  returnType?: FieldUpdate<String>;

  /**
   * The documentation string to update.
   */
  documentation?: FieldUpdate<String>;

  /**
   * The scope to update.
   */
  scope?: FieldUpdate<SuggestionEntryScope>;

  /**
   * The reexport field to update.
   */
  reexport?: FieldUpdate<String>;
}
```

### `SuggestionsOrderDatabaseUpdate`

The update of the suggestions order database.

#### Format

```typescript
/**
 * The kind of the suggestions order database update.
 */
type SuggestionsOrderDatabaseUpdate = AddOrder | RemoveOrder | ModifyOrder;

interface AddOrder {
  entry: SuggestionOrderDatabaseEntry;
}

interface RemoveOrder {
  /**
   * The unique identifier of a suggestion.
   */
  suggestionId: SuggestionId;
}

interface ModifyOrder {
  /**
   * The unique identifier of a suggestion.
   */
  suggestionId: SuggestionId;

  /**
   * The previous suggestion id to update.
   */
  prevId?: FieldUpdate<SuggestionId>;

  /**
   * The next suggestion id to update.
   */
  nextId?: FieldUpdate<SuggestionId>;
}
```

### `Export`

The info about module re-export.

#### Format

```typescript
type Export = Qualified | Unqualified;

interface Qualified {
  /**
   * The module that re-exports the given module.
   */
  module: String;

  /**
   * The new name of the given module if it was renamed in the export clause.
   *
   * I.e. `X` in `export A.B as X`.
   */
  alias?: String;
}

interface Unqualified {
  /**
   * The module name that re-exports the given module.
   */
  module: String;
}
```

### `File`

A representation of a file on disk.

#### Format

```typescript
interface File {
  name: String; // Includes the file extension
  type: String;
}
```

### `DirectoryTree`

A directory tree is a recursive type used to represent tree structures of files
and directories. It contains files and symlinks in the `files` section and
directories in the `directories` section. When the tree was requested with the
parameter limiting the maximum depth, the bottom of the `DirectoryTree` will
contain `Directory` node in the `files` section indicating that there is a
directory, but the contents are unknown because we've reached the maximum depth.

#### Format

```typescript
interface DirectoryTree {
  path: Path;
  name: String;
  files: [FileSystemObject];
  directories: [DirectoryTree];
}
```

### `FileAttributes`

A description of the attributes of a file required by the IDE. These attributes
may be expanded in future.

#### Format

```typescript
/**
 * A representation of the attributes of a file.
 *
 * @param creationTime creation time
 * @param lastAccessTime last access time
 * @param lastModifiedTime last modified time
 * @param kind type of [[FileSystemObject]], can be: `Directory`, `File`, `Other`
 * @param byteSize size in bytes
 */
interface FileAttributes {
  creationTime: UTCDateTime;
  lastAccessTime: UTCDateTime;
  lastModifiedTime: UTCDateTime;
  kind: FileSystemObject;
  byteSize: number;
}
```

### `UTCDateTime`

Time in UTC time zone represented as ISO-8601 string

#### Format

```typescript
type UTCDateTime = String;
```

### `FileEventKind`

The kind of event being described for a watched file.

#### Format

```typescript
type FileEventKind = Added | Removed | Modified;
```

### `Position`

A representation of a position in a text file.

#### Format

```typescript
interface Position {
  /**
   * Line position in a document (zero-based).
   */
  line: number;

  /**
   * Character offset on a line in a document (zero-based). Assuming that the
   * line is represented as a string, the `character` value represents the gap
   * between the `character` and `character + 1`.
   *
   * If the character value is greater than the line length it defaults back to
   * the line length.
   */
  character: number;
}
```

```idl
namespace org.enso.languageserver.protocol.binary;

struct Position {
  // Line position in a document (zero-based)
  line: uint64;
  // Character offset on a line in a document (zero-based)
  character: uint64;
}
```

### `Range`

A representation of a range of text in a text file.

#### Format

```typescript
interface Range {
  /**
   * The range's start position.
   */
  start: Position;

  /**
   * The range's end position.
   */
  end: Position;
}
```

### `TextEdit`

A representation of a change to a text file at a given position.

#### Format

```typescript
interface TextEdit {
  range: Range;
  text: String;
}
```

### `DiagnosticType`

The type of diagnostic message.

#### Format

```typescript
type DiagnosticType = Error | Warning;
```

### `StackTraceElement`

The frame of the stack trace. If the error refer to a builtin node, the `path`
and `location` fields will be empty.

#### Format

```typescript
interface StackTraceElement {
  /**
   * The function name containing the stack trace element.
   */
  functionName: String;

  /**
   * The location of the file.
   */
  path?: Path;

  /**
   * The location of the element in a file.
   */
  location?: Range;
}
```

### `Diagnostic`

A diagnostic object is produced as a result of an execution attempt, like
pushing the method pointer to a call stack, or editing the file. It can
represent a compiler warning, a compilation error, or a runtime error. The
message has optional `path`, `location` and `stack` fields containing
information about the location in the source code.

In case of the runtime errors, the `path` and `location` fields may be empty if
the error happens in a builtin node. Then, to locate the error in the code, you
can use the `stack` field with a stack trace to find the first element with
non-empty location (as the head of the stack will point to the builtin element).

#### Format

```typescript
interface Diagnostic {
  /**
   * The type of diagnostic message.
   */
  kind: DiagnosticType;

  /**
   * The diagnostic message.
   */
  message: String;

  /**
   * The location of a file containing the diagnostic.
   */
  path?: Path;

  /**
   * The location of the diagnostic object in a file.
   */
  location?: Range;

  /**
   * The id of related expression.
   */
  expressionId?: ExpressionId;

  /**
   * The stack trace.
   */
  stack: StackTraceElement[];
}
```

### `SHA3-224`

The `SHA3-224` message digest encoded as a base16 string. For the equivalent
structure on the binary connection please see [`EnsoDigest`](#ensodigest)

#### Format

```typescript
type SHA3-224 = String;
```

### `FileEdit`

A representation of a batch of edits to a file, versioned.

`SHA3-224` represents hash of the file contents. `oldVersion` is the version
you're applying your update on, `newVersion` is what you compute as the hash
after applying the changes. In other words,

```python
hash(origFile) == oldVersion
hash(applyEdits(origFile, edits)) == newVersion
```

it's a sanity check to make sure that the diffs are applied consistently.

Consecutive text edits are applied sequentially, every one acting on the result
of applying previous ones on the original buffer contents. In pseudocode:

```haskell
applyEdits buffer [] = buffer
applyEdits buffer (first : rest) = applyEdits (applyTextEdit buffer first) rest
```

#### Format

```typescript
interface FileEdit {
  path: Path;
  edits: [TextEdit];
  oldVersion: SHA3-224;
  newVersion: SHA3-224;
}
```

### `FileContents`

A representation of the contents of a file.

#### Format

```typescript
interface FileContents<T> {
  contents: T;
}

class TextFileContents extends FileContents<String> {}
```

### `FileSystemObject`

A representation of what kind of type a filesystem object can be.

#### Format

```typescript
type FileSystemObject = Directory | SymlinkLoop | File | Other;

/**
 * Represents a directory.
 *
 * @param name a name of the directory
 * @param path a path to the directory
 */
interface Directory {
  name: String;
  path: Path;
}

/**
 * Represents a symbolic link that creates a loop.
 *
 * @param name a name of the symlink
 * @param path a path to the symlink
 * @param target a target of the symlink. Since it is a loop,
 * target is a subpath of the symlink
 */
interface SymlinkLoop {
  name: String;
  path: Path;
  target: Path;
}

/**
 * Represents a file.
 *
 * @param name a name of the file
 * @param path a path to the file
 */
interface File {
  name: String;
  path: Path;
}

/**
 * Represents unrecognized object.
 * Example is a broken symbolic link.
 */
interface Other {
  name: String;
  path: Path;
}
```

### `WorkspaceEdit`

This is a message to be specified once we better understand the intricacies of
undo/redo.

> The actionables for this section are:
>
> - Work out the design of this message.
> - Specify this message.

### `EnsoDigest`

A counterpart to [SHA3-224](#sha3-224) for the binary connection, this is a
standard message digest encoded using FlatBuffers.

```idl
namespace org.enso.languageserver.protocol.binary;

table EnsoDigest {
  bytes : [ubyte] (required);
}
```

Notes:

- It is an error for the length of the vector `bytes` to not be equal to 28 (224
  / 8). This is the length of the chosen digest in bytes.

### `FileSegment`

A representation of a segment of a file for use in the binary protocol.

```idl
namespace org.enso.languageserver.protocol.binary;

table FileSegment {
  // The file to access.
  path : Path (required);

  // The byte offset in the file to read from.
  byteOffset : ulong;

  // The number of bytes to read.
  length : ulong;
}
```

The `byteOffset` property is zero-indexed, so the last byte in the file is at
index `file.length - 1`.

### `ContentRoot`

A representation of a content root for use in the IDE. A content root represents
a location on a real file-system that has been virtualised for use in the Enso
VFS.

```typescript
type ContentRoot = Project | FileSystemRoot | Home | Library | Custom;
```

```typescript
/** This content root points to the project home. */
interface Project {
  // A unique identifier for the content root.
  id: UUID;
}

/**
 * This content root points to the system root (`/`) on unix systems, or to a
 * drive root on Windows. In Windows' case, there may be multiple `Root` entries
 * corresponding to the various drives.
 */
interface FileSystemRoot {
  // A unique identifier for the content root.
  id: UUID;

  // The absolute filesystem path of the content root.
  path: String;
}

/** The user's home directory. */
interface Home {
  // A unique identifier for the content root.
  id: UUID;
}

/** An Enso library location. */
interface Library {
  // A unique identifier for the content root.
  id: UUID;

  // The namespace of the library.
  namespace: String;

  // The name of the library.
  name: String;

  /**
   * The version of the library.
   *
   * It is either a semver version of the library or the string "local".
   */
  version: String;
}

/** A content root that has been added by the IDE (unused for now). */
interface Custom {
  // A unique identifier for the content root.
  id: UUID;
}
```

### `LibraryEntry`

Represents a library available in a resolved edition.

```typescript
interface LibraryEntry {
  namespace: String;
  name: String;
  version: LibraryVersion;
}
```

```typescript
type LibraryVersion = LocalLibraryVersion | PublishedLibraryVersion;

/** A library version that references a version of the library published in some
 * repository.
 */
interface PublishedLibraryVersion {
  // A semver-compliant version of the library.
  version: String;

  // URL to the repository that this library will be downloaded from.
  repositoryUrl: String;
}

// A library version that references a locally editable version of the library.
interface LocalLibraryVersion {}
```

The local libraries do not have metadata associated with them by default, as for
the published libraries, the canonical way to access the metadata is to download
the manifest, as described in the
[library repository structure](../libraries/repositories.md#libraries-repository).
So the manifest URL can be found by combining the repository URL and library
name and version: `<repositoryUrl>/<namespace>/<name>/<version>/manifest.yaml`.

### `EditionReference`

A reference to a specific edition.

Currently, it can either reference an edition by its name, or reference the
edition associated with the currently open project.

```typescript
type EditionReference = NamedEdition | CurrentProjectEdition;

// The edition associated with the current project, with all of its overrides.
interface CurrentProjectEdition {}

// An edition stored under a given name.
interface NamedEdition {
  editionName: String;
}
```

## Connection Management

In order to properly set-up and tear-down the language server connection, we
need a set of messages to control this process.

### `session/initProtocolConnection`

This message initialises the connection used to send the textual protocol
messages. This initialisation is important such that the client identifier can
be correlated between the textual and data connections.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  clientId: UUID;
}
```

#### Result

```typescript
{
  contentRoots: [ContentRoot];
}
```

#### Errors

- [`SessionAlreadyInitialisedError`](#sessionalreadyinitialisederror) to signal
  that session is already initialised.
- [`ResourcesInitializationError`](#resourcesinitializationerror) to signal
  about the error during the initialization of Language Server resources.

### `session/initBinaryConnection`

This message initialises the data connection used for transferring binary data
between engine and clients. This initialisation is important such that the
client identifier can be correlated between the data and textual connections.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Data
- **Visibility:** Public

#### Parameters

```idl
namespace org.enso.languageserver.protocol.binary;

//A command initializing a data session.
table InitSessionCommand {

  //A unique identifier of a client initializing the session.
  identifier: EnsoUUID (required);

}

root_type InitSessionCommand;
```

#### Result

```
namespace org.enso.languageserver.protocol.binary;

//Indicates an operation has succeeded.
table Success {}
```

#### Errors

N/A

## Capability Management

In order to mediate between multiple clients properly, the language server has a
robust notion of capability management to grant and remove permissions from
clients.

### `capability/acquire`

This requests that the server grant the specified capability to the requesting
client.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  method: String;
  registerOptions?: any;
}
```

The `registerOptions` are determined by the `method`. The method must be listed
in the section on [capabilities](#capabilities) below.

#### Result

```typescript
null;
```

#### Errors

TBC

### `capability/release`

This requests that the server acknowledge that the client is releasing a given
capability.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  registration: CapabilityRegistration;
}
```

#### Result

```typescript
null;
```

#### Errors

TBC

### `capability/granted`

This notifies the client that it has been granted a capability without any
action on its part.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  registration: CapabilityRegistration;
}
```

#### Errors

TBC

### `capability/forceReleased`

This notifies the client that a capability has been forcibly removed from its
capability set.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  registration: CapabilityRegistration;
}
```

#### Errors

TBC

## Capabilities

The capability management features work with the following capabilities.

### `text/canEdit`

This capability states that the capability has the ability to perform both
`text/applyEdit` and `text/save` for the specified file.

- **method:** `text/canEdit`
- **registerOptions:** `{path: Path;}`

#### Enables

- [`text/applyEdit`](#textapplyedit)
- [`text/save`](#textsave)

#### Disables

None

### `file/receivesTreeUpdates`

This capability states that the client will receive updates for any watched
content roots in the current project.

- **method:** `file/receivesTreeUpdates`
- **registerOptions:** `{ path: Path; }`

#### Enables

- [`file/event`](#fileevent)

#### Disables

None

#### Errors

[`capability/acquire`](#capabilityacquire):

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`FileNotFound`](#filenotfound) informs that path cannot be found.

[`capability/release`](#capabilityrelease):

- [`CapabilityNotAcquired`](#capabilitynotacquired) informs that requested
  capability is not acquired.

### `executionContext/canModify`

This capability states that the client has the ability to modify an execution
context, including modifying the execution stack, invalidating caches, or
destroying the context.

- **method:** `executionContext/canModify`
- **registerOptions:** `{ contextId: ContextId; }`

#### Enables

- [`executionContext/destroy`](#executioncontextdestroy)
- [`executionContext/recompute`](#executioncontextrecompute)
- [`executionContext/push`](#executioncontextpush)
- [`executionContext/pop`](#executioncontextpop)
- [`executionContext/executeExpression`](#executioncontextexecuteexpression)
- [`executionContext/attachVisualisation`](#executioncontextattachvisualisation)
- [`executionContext/modifyVisualisation`](#executioncontextmodifyvisualisation)
- [`executionContext/detachVisualisation`](#executioncontextdetachvisualisation)
- [`executionContext/visualisationUpdate`](#executioncontextvisualisationupdate)
- [`executionContext/visualisationEvaluationFailed`](#executioncontextvisualisationevaluationfailed)

#### Disables

None

### `executionContext/receivesUpdates`

This capability states that the client receives expression value updates from a
given execution context.

- **method:** `executionContext/receivesUpdates`
- **registerOptions:** `{ contextId: ContextId; }`

#### Enables

- [`executionContext/expressionUpdates`](#executioncontextexpressionupdates)
- [`executionContext/executionFailed`](#executioncontextexecutionfailed)
- [`executionContext/executionStatus`](#executioncontextexecutionstatus)

#### Disables

None

### `search/receivesSuggestionsDatabaseUpdates`

This capability states that the client receives the search database updates for
a given execution context.

- **method:** `search/receivesSuggestionsDatabaseUpdates`
- **registerOptions:** `{}`

#### Enables

- [`search/suggestionsDatabaseUpdate`](#suggestionsdatabaseupdate)
- [`search/suggestionsOrderDatabaseUpdate`](#suggestionsorderdatabaseupdate)

#### Disables

None

## File Management Operations

The language server also provides file operations to the IDE.

### `file/write`

This requests that the file manager component write to a specified file with the
specified contents.

- **Type:** Request
- **Direction:** Client -> Server

This request is _explicitly_ allowed to write to files that do not exist, and
will create them under such circumstances. If a file is recorded as 'open' by
one of the clients, and another client attempts to write to that file, the write
must fail.

#### Parameters

```typescript
{
  path: Path;
  contents: FileContents<T>;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`AccessDeniedError`](#accessdeniederror) to signal that a user doesn't have
  access to a resource.

### `file/read`

This requests that the file manager component reads the contents of a specified
file.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

If the file is recorded as open by the language server, then the result will
return the contents from the in-memory buffer rather than the file on disk.

#### Parameters

```typescript
{
  path: Path;
}
```

#### Result

```typescript
{
  contents: FileContents<T>;
}
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`AccessDeniedError`](#accessdeniederror) to signal that a user doesn't have
  access to a resource.
- [`FileNotFound`](#filenotfound) informs that file cannot be found.

### `file/writeBinary`

This requests that the file manager component write to a specified file with the
binary contents.

- **Type:** Request
- **Connection:** Binary
- **Direction:** Client -> Server

This request is _explicitly_ allowed to write to files that do not exist, and
will create them under such circumstances. If a file is recorded as 'open' by
one of the clients, and another client attempts to write to that file, the write
must fail.

#### Parameters

```idl
namespace org.enso.languageserver.protocol.binary;

//A command writing binary contents to a file.
table WriteFileCommand {

  //A path to a file.
  path: Path;

  //Binary contents.
  contents: [ubyte];

}

```

#### Result

```idl
namespace org.enso.languageserver.protocol.binary;

//Indicates an operation has succeeded.
table Success {}
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`AccessDeniedError`](#accessdeniederror) to signal that a user doesn't have
  access to a resource.

### `file/readBinary`

This requests that the file manager component reads the binary contents of a
specified file.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Binary
- **Visibility:** Public

If the file is recorded as open by the language server, then the result will
return the contents from the in-memory buffer rather than the file on disk.

#### Parameters

```idl
namespace org.enso.languageserver.protocol.binary;

//A command reading binary contents from a file.
table ReadFileCommand {

  //A path to a file.
  path: Path;

}
```

#### Result

```idl
namespace org.enso.languageserver.protocol.binary;

//A reply for a ReadFileCommand.
table FileContentsReply {

  //Binary contents.
  contents: [ubyte];

}
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`AccessDeniedError`](#accessdeniederror) to signal that a user doesn't have
  access to a resource.
- [`FileNotFound`](#filenotfound) informs that file cannot be found.

### `file/writeBytes`

This requests that the file manager component writes a set of bytes to the
specified file at the specified offset.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Binary
- **Visibility:** Public

This method will create a file if no file is present at `path`.

- The `overwriteExisting` boolean should be set if `byteOffset` is less than the
  length of the file.
- The `byteOffset` property is zero-indexed. To append to the file you begin
  writing at index `file.length`.
- If `byteOffset` is less than the length of the file and `overwriteExisting` is
  set, it will truncate the file to length `byteOffset + bytes.length`.
- If `byteOffset > file.length`, the bytes in the range
  `[file.length, byteOffset)` will be filled with null bytes. Please note that,
  in this case, the checksum in the response will also be calculated on the null
  bytes.

#### Parameters

```idl
namespace org.enso.languageserver.protocol.binary;

table WriteBytesCommand {
  // The file to write to.
  path : Path (required);

  // The byte offset in the file to write from.
  byteOffset : ulong;

  // Whether existing content should be overwritten.
  overwriteExisting : bool;

  // The file contents.
  bytes : [ubyte] (required);
}
```

#### Result

```idl
namespace org.enso.languageserver.protocol.binary;

table WriteBytesReply {
  // The checksum of the written bytes.
  checksum : EnsoDigest (required);
}
```

Notes:

- The `checksum` is only of the `bytes` in the request as they were written to
  disk. This does _not_ include checksumming the entire file. For that, please
  see [`file/checksumBytes`](#file-checksumbytes).

#### Errors

- [`CannotOverwrite`](#cannotoverwrite) to signal that an overwrite would be
  necessary to perform the operation but that `overwriteExisting` is not set.
- [`NotFile`](#notfile) if the provided `segment.path` is not a file.

### `file/readBytes`

Asks the language server to read the specified number of bytes at the specified
offset in the file.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Binary
- **Visibility:** Public

It will attempt to read _as many as_ `segment.length` bytes, but does not
guarantee that the response will contain `segment.length` bytes (e.g. if
`segment.length` would require reading off the end of the file).

#### Parameters

```idl
namespace org.enso.languageserver.protocol.binary;

table ReadBytesCommand {
  // The segment in a file to read bytes from.
  segment : FileSegment (required);
}
```

#### Result

```idl
namespace org.enso.languageserver.protocol.binary;

table ReadBytesReply {
  // The checksum of the bytes in this response.
  checksum : EnsoDigest (required);

  // The requested file contents.
  bytes : [ubyte] (required);
}
```

Notes:

- The `checksum` is of the `bytes` as they have been read from disk.

#### Errors

- [`FileNotFound`](#filenotfound) if the file at `segment.path` does not exist.
- [`ReadOutOfBounds`](#readoutofbounds) if `segment.byteOffset` is not present
  in the file at `segment.path`.
- [`NotFile`](#notfile) if the provided `segment.path` is not a file.

### `file/create`

This request asks the file manager to create the specified file system object.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

This will fail if the specified object already exists.

#### Parameters

```typescript
{
  object: FileSystemObject;
}
```

#### Response

```typescript
null;
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`AccessDeniedError`](#accessdeniederror) to signal that a user doesn't have
  access to a resource.

### `file/delete`

This request asks the file manager to delete the specified file system object.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  path: Path;
}
```

#### Result

```
null
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`FileNotFound`](#filenotfound) informs that file cannot be found.
- [`FileExists`](#fileexists) informs that file already exists

### `file/copy`

This request asks the file manager to copy a specified filesystem object to
another location.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  from: Path;
  to: Path;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`FileNotFound`](#filenotfound) informs that file cannot be found.

### `file/move`

This request asks the file manager to move a specified filesystem object to
another location.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

The move should be specified by filesystem events, and such notifications should
inform the client that the currently edited file has been moved.

#### Parameters

```typescript
{
  from: Path;
  to: Path;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`FileNotFound`](#filenotfound) informs that file cannot be found.
- [`FileExists`](#fileexists) informs that target file already exists.

### `file/exists`

This request asks the file manager to check whether a filesystem object exists
at the specified path.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  path: Path;
}
```

#### Result

```typescript
{
  exists: Boolean;
}
```

#### Errors

- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.

### `file/tree`

This request asks the file manager component to generate and provide the
directory tree starting at a given path.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  path: Path;
  depth?: Number;
}
```

#### Result

```typescript
{
  tree: DirectoryTree;
}
```

#### Errors

- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`FileNotFound`](#filenotfound) informs that requested path does not exist or
  provided depth argument is <= 0.
- [`NotDirectory`](#notdirectory) informs that requested path is not a
  directory.

### `file/list`

This request lists the contents of a given filesystem object. For a file it will
just return the file, while for a directory it will list the contents of the
directory.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  path: Path;
}
```

#### Result

```typescript
{
  paths: [FileSystemObject];
}
```

#### Errors

- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`FileNotFound`](#filenotfound) informs that requested path does not exist.
- [`NotDirectory`](#notdirectory) informs that requested path is not a
  directory.

### `file/info`

This request gets information about a specified filesystem object.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

This request should work for all kinds of filesystem object.

#### Parameters

```typescript
{
  path: Path;
}
```

#### Result

```typescript
{
  attributes: FileAttributes;
}
```

#### Errors

- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`FileNotFound`](#filenotfound) informs that requested path does not exist.

### `file/checksum`

Requests that the language server provide the checksum of the provided file.
Only defined when the provided `path` is a file.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

It calculates the checksum of the entire file.

#### Parameters

```typescript
interface ChecksumRequest {
  // The path to the file to get the checksum for.
  path: Path;
}
```

#### Result

```typescript
interface ChecksumResponse {
  // The checksum of the file at `path`.
  checksum : SHA3-224;
}
```

#### Errors

- [`FileNotFound`](#filenotfound) if the file at `path` does not exist.
- [`NotFile`](#notfile) if the provided `path` does not point to a file.

### `file/checksumBytes`

Requests that the language server provides the checksum of the provided byte
range.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Binary
- **Visibility:** Public

#### Parameters

```idl
namespace org.enso.languageserver.protocol.binary;

table ChecksumBytesCommand {
  // The segment in a file to checksum.
  segment : FileSegment (required);
}
```

#### Result

```idl
namespace org.enso.languageserver.protocol.binary;

table ChecksumBytesReply {
  // The segment in a file to checksum.
  checksum : EnsoDigest;
}
```

#### Errors

- [`FileNotFound`](#filenotfound) if the file at `segment.path` does not exist.
- [`ReadOutOfBounds`](#readoutofbounds) if `segment.byteOffset` is not present
  in the file at `segment.path`, or if `segment.length` does not fit within the
  file.
- [`NotFile`](#notfile) if the provided `segment.path` is not a file.

### `file/event`

This is a notification that is sent every time something under a watched content
root changes. It is used to ensure that the client's filesystem representation
stays in synchronisation with reality.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

Events should be sent from server to client for every event observed under one
of the (possibly multiple) content roots.

#### Parameters

```typescript
{
  path: Path;
  kind: FileEventKind;
}
```

#### Errors

None

### `file/rootAdded`

This is a notification sent to all clients to inform them that a content root
has been added.

At the beginning, a series of notifications is sent that lists all content roots
that are present at the current moment. This message may contain the same
content roots that were already present in the `session/initProtocolConnection`.
That is done, because there is no guarantee that no root has been added between
the init message and the time when notifications start being sent, and this
ensures that no content root is missed.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  root: ContentRoot;
}
```

#### Errors

TBC

### `file/rootRemoved`

This is a notification sent to all clients other than the one performing the
removal of the content root in order to inform them of the removal of the root.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  id: UUID; // The content root ID
}
```

#### Errors

TBC

## Text Editing Operations

The language server also has a set of text editing operations to ensure that it
stays in sync with the clients.

### `text/openFile`

This requests the language server to open the specified file.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

If no client has write lock on the opened file, the capability is granted to the
client that sent the `text/openFile` message.

#### Parameters

```typescript
{
  path: Path;
}
```

#### Result

```typescript
{
  writeCapability?: CapabilityRegistration;
  content: String;
  currentVersion: SHA3-224;
}
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`AccessDeniedError`](#accessdeniederror) to signal that a user doesn't have
  access to a resource.
- [`FileNotFound`](#filenotfound) informs that file cannot be found.

### `text/closeFile`

This requests the language server to close the specified file.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  path: Path;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`FileNotOpenedError`](#filenotopenederror) to signal that a file wasn't
  opened.

### `text/save`

This requests for the language server to save the specified file.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

The request may fail if the requesting client does not have permission to edit
that file, or if the client is requesting a save of an outdated version.

#### Parameters

```typescript
{
  path: Path;
  currentVersion: SHA3 - 224;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`FileNotOpenedError`](#filenotopenederror) to signal that the file isn't
  open.
- [`InvalidVersionError`](#invalidversionerror) to signal that the version
  provided by the client doesn't match the version computed by the server.
- [`WriteDeniedError`](#writedeniederror) to signal that the client doesn't hold
  write lock for the buffer.
- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.
- [`ContentRootNotFoundError`](#contentrootnotfounderror) to signal that the
  requested content root cannot be found.
- [`AccessDeniedError`](#accessdeniederror) to signal that the user doesn't have
  access to a resource.

### `text/applyEdit`

This requests that the server apply a series of edits to the project. These
edits solely concern text files.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

This operation may fail if the requesting client does not have permission to
edit the resources for which edits are sent. This failure _may_ be partial, in
that some edits are applied and others are not.

#### Parameters

```typescript
{
  edit: FileEdit;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`FileNotOpenedError`](#filenotopenederror) to signal that the file isn't
  open.
- [`TextEditValidationError`](#texteditvalidationerror) to signal that
  validation has failed for a series of edits.
- [`InvalidVersionError`](#invalidversionerror) to signal that the version
  provided by the client doesn't match the version computed by the server.
- [`WriteDeniedError`](#writedeniederror) to signal that the client doesn't hold
  write lock for the buffer.

### `text/didChange`

This is a notification sent from the server to the clients to inform them of any
changes made to files that they have open.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

This notification must _only_ be sent for files that the client has open.

#### Parameters

```typescript
{
  edits: [FileEdit];
}
```

#### Errors

```typescript
null;
```

## Workspace Operations

The language server also has a set of operations useful for managing the client
workspace.

### `workspace/projectInfo`

This request allows the IDE to request information about the currently open
project in situations where it does not have a project manager to connect to.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
}
```

#### Result

```typescript
{
  // The name of the project.
  projectName: String;

  // The engine version on which the project is running.
  engineVersion: String;

  // The version of graal on which the project is running.
  graalVersion: String;
}
```

#### Errors

- [`CannotDecode`](#cannotdecode) if the project configuration cannot be
  decoded.
- [`FileNotFound`](#filenotfound) if the project configuration cannot be found.

### `workspace/undo`

This request is sent from the client to the server to request that an operation
be undone.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

The exact behaviour of this message is to be determined, but it must involve the
server undoing that same action for all clients in the workspace.

#### Parameters

```typescript
{
  requestID?: UUID; // If not specified, it undoes the latest request
}
```

#### Result

```typescript
null;
```

#### Errors

TBC

### `workspace/redo`

This request is sent from the client to the server to request that an operation
be redone.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

The exact behaviour of this message is to be determined, but it must involve the
server redoing that same action for all clients in the workspace.

#### Parameters

```typescript
{
  requestID?: UUID; // If not specified, it redoes the latest request
}
```

#### Result

```typescript
null;
```

#### Errors

TBC

## Monitoring

The language server also has a heartbeat operation to monitor the Language
server. This API is private and should be used only by the Project Manager.

### `heartbeat/ping`

This request is sent from the supervisor process to the server to check the
health of the Language Server.

- **Type:** Request
- **Direction:** Supervisor -> Server
- **Connection:** Protocol
- **Visibility:** Private

#### Parameters

```typescript
null;
```

#### Result

```typescript
null;
```

#### Errors

None

### `heartbeat/init`

This request is sent from the bootloader to check if the started language server
instance has finished initialization. A reply should only be sent when the main
module has been fully initialized.

- **Type:** Request
- **Direction:** Supervisor -> Server
- **Connection:** Protocol
- **Visibility:** Private

#### Parameters

```typescript
null;
```

#### Result

```typescript
null;
```

#### Errors

None

## Refactoring

The language server also provides refactoring operations to restructure an
internal body of code.

### `refactoring/renameProject`

This request is sent from the project manager to the server to refactor project
name in an interpreter runtime.

- **Type:** Request
- **Direction:** Project Manager -> Server
- **Connection:** Protocol
- **Visibility:** Private

#### Parameters

```typescript
{
  namespace: String;
  oldName: String;
  newName: String;
}
```

#### Result

```typescript
null;
```

#### Errors

None

## Execution Management Operations

The execution management portion of the language server API deals with exposing
fine-grained control over program and expression execution to the clients of the
language server. This is incredibly important for enabling the high levels of
interactivity required by Enso Studio.

### Execution Management Example

Given the default project structure.

```text
├── package.yaml
└── src
    └── Main.enso
```

```bash
$ cat src/Main.enso

main =
    x = 6
    y = x.foo 5
    z = y + 5
    z

Number.foo = x ->
    y = this + 3
    z = y * x
    z



#### METADATA ####
[[{"index": {"value": 98}, "size": {"value": 5}}, "5fc0c11d-bd83-4ca3-b847-b8e362f7658c"],[{"index": {"value": 81}, "size": {"value": 8}}, "1cda3676-bd62-41f8-b6a1-a1e1b7c73d18"],[{"index": {"value": 42}, "size": {"value": 5}}, "899a11e5-4d2b-43dc-a867-2f2ef2d2ba62"],[{"index": {"value": 26}, "size": {"value": 7}}, "37f284d4-c593-4e65-a4be-4948fbd2adfb"],[{"index": {"value": 16}, "size": {"value": 1}}, "c553533e-a2b9-4305-9f12-b8fe7781f933"]]
[]
```

Notice extra newline in the beginning of the `Main.enso` file, it is important
for the precalculated metadata indexes.

### Create Execution Context

```json
{
  "jsonrpc": "2.0",
  "method": "executionContext/create",
  "id": 0,
  "params": null
}
```

Return capabilities together with a newly created `ContextId`.

```json
{
  "jsonrpc": "2.0",
  "id": 0,
  "result": {
    "contextId": "1eb5ad04-4094-4c1f-be54-e9d29ddf19a3",
    "canModify": {
      "method": "executionContext/canModify",
      "registerOptions": {
        "contextId": "1eb5ad04-4094-4c1f-be54-e9d29ddf19a3"
      }
    },
    "receivesUpdates": {
      "method": "executionContext/receivesUpdates",
      "registerOptions": {
        "contextId": "1eb5ad04-4094-4c1f-be54-e9d29ddf19a3"
      }
    }
  }
}
```

### Push Item

Entering the `main` method. First item on the stack should always be an
`ExplicitCall`.

```json
{
  "jsonrpc": "2.0",
  "method": "executionContext/push",
  "id": 0,
  "params": {
    "contextId": "1eb5ad04-4094-4c1f-be54-e9d29ddf19a3",
    "stackItem": {
      "type": "ExplicitCall",
      "methodPointer": {
        "file": {
          "rootId": "18f642a2-5f69-4fc8-add6-13bf199ca326",
          "segments": ["src", "Main.enso"]
        },
        "definedOnType": "Main",
        "name": "main"
      },
      "thisArgumentExpression": null,
      "positionalArgumentsExpressions": []
    }
  }
}
```

Returns successful reponse.

```json
{
  "jsonrpc": "2.0",
  "id": 0,
  "result": null
}
```

And a value update, result of the method `foo` call defined on type `Number`.

```json
{
  "jsonrpc": "2.0",
  "method": "executionContext/expressionValuesComputed",
  "params": {
    "contextId": "1eb5ad04-4094-4c1f-be54-e9d29ddf19a3",
    "updates": [
      {
        "id": "37f284d4-c593-4e65-a4be-4948fbd2adfb",
        "type": "Number",
        "shortValue": "45",
        "methodCall": {
          "file": {
            "rootId": "18f642a2-5f69-4fc8-add6-13bf199ca326",
            "segments": ["src", "Main.enso"]
          },
          "definedOnType": "Number",
          "name": "foo"
        }
      }
    ]
  }
}
```

We can go deeper and evaluate the method `foo` call by pushing the `LocalCall`
on the stack. In general, all consequent stack items should be `LocalCall`s.

```json
{
  "jsonrpc": "2.0",
  "method": "executionContext/push",
  "id": 0,
  "params": {
    "contextId": "1eb5ad04-4094-4c1f-be54-e9d29ddf19a3",
    "stackItem": {
      "type": "LocalCall",
      "expressionId": "37f284d4-c593-4e65-a4be-4948fbd2adfb"
    }
  }
}
```

Returns successful reponse.

```json
{
  "jsonrpc": "2.0",
  "id": 0,
  "result": null
}
```

And update of some value inside the function `foo`.

```json
{
  "jsonrpc": "2.0",
  "method": "executionContext/expressionValuesComputed",
  "params": {
    "contextId": "1eb5ad04-4094-4c1f-be54-e9d29ddf19a3",
    "updates": [
      {
        "id": "1cda3676-bd62-41f8-b6a1-a1e1b7c73d18",
        "type": "Number",
        "shortValue": "9",
        "methodCall": null
      }
    ]
  }
}
```

### Pop Item

```json
{
  "jsonrpc": "2.0",
  "method": "executionContext/pop",
  "id": 0,
  "params": {
    "contextId": "1eb5ad04-4094-4c1f-be54-e9d29ddf19a3"
  }
}
```

Popping one item will return us into the `main` method. Second call will clear
the stack. Subsequent pop calls will result in an error indicating that the
stack is empty.

### `executionContext/create`

Sent from the client to the server to create a new execution context. Return
capabilities [`executionContext/canModify`](#executioncontextcanmodify) and
[`executionContext/receivesUpdates`](#executioncontextreceivesupdates).

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
{
  contextId: ContextId;
  canModify: CapabilityRegistration;
  receivesUpdates: CapabilityRegistration;
}
```

#### Errors

None

### `executionContext/destroy`

Sent from the client to the server destroy an execution context and free its
resources.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  contextId: ContextId;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`AccessDeniedError`](#accessdeniederror) when the user does not hold the
  `executionContext/canModify` capability for this context.
- [`ContextNotFoundError`](#contextnotfounderror) when context can not be found
  by provided id.

### `executionContext/fork`

Sent from the client to the server to duplicate an execution context, creating
an independent copy, containing all the data precomputed in the first one.
Return capabilities [`executionContext/canModify`](#executioncontextcanmodify)
and [`executionContext/receivesUpdates`](#executioncontextreceivesupdates).

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  contextId: ContextId;
}
```

#### Result

```typescript
{
  contextId: ContextId;
  canModify: CapabilityRegistration;
  receivesUpdates: CapabilityRegistration;
}
```

#### Errors

No known errors.

### `executionContext/push`

Sent from the client to the server execute item and move the execution context
to a new location deeper down the stack. If a stack item becomes invalid because
of a text edit (e.g. the root function of the view was removed), it will stop
executing. If the function reappears, execution should resume as normal.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  contextId: ContextId;
  stackItem: StackItem;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`AccessDeniedError`](#accessdeniederror) when the user does not hold the
  `executionContext/canModify` capability for this context.
- [`StackItemNotFoundError`](#stackitemnotfounderror) when the request stack
  item could not be found.
- [`InvalidStackItemError`](#invalidstackitemerror) when pushing `LocalCall` on
  top of the empty stack, or pushing `ExplicitCall` on top of non-empty stack.

### `executionContext/pop`

Sent from the client to the server move the execution context up the stack,
corresponding to the client clicking out of the current breadcrumb.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  contextId: ContextId;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`AccessDeniedError`](#accessdeniederror) when the user does not hold the
  `executionContext/canModify` capability for this context.
- [`EmptyStackError`](#emptystackerror) when the user tries to pop an empty
  stack.

### `executionContext/recompute`

Sent from the client to the server to force recomputation of current position.
May include a list of expressions for which caches should be invalidated.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  contextId: ContextId;
  invalidatedExpressions?: "all" | [ExpressionId]
}
```

#### Result

```typescript
null;
```

#### Errors

- [`AccessDeniedError`](#accessdeniederror) when the user does not hold the
  `executionContext/canModify` capability for this context.
- [`EmptyStackError`](#emptystackerror) when the user tries to recompute an
  empty stack.

### `executionContext/expressionUpdates`

Sent from the server to the client to inform about new information for certain
expressions becoming available. Supersedes the
`executionContext/expressionValuesComputed` notification, that will be removed
in future versions.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  contextId: ContextId;
  updates: [ExpressionUpdate];
}
```

#### Errors

None

### `executionContext/executionFailed`

Sent from the server to the client to inform about a critical failure when
attempting to execute a context.

When the [`executionContext/executionStatus`](#executioncontextexecutionstatus)
notifies about potential problems in the code found by compiler, or the errors
during runtime, this message signals about the errors in the logic or the
implementation. It can be a compiler crash, an attempt to execute an empty
stack, an error location a method or a module when issuing a
[`executionContext/push`](#executioncontextpush) command.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  /**
   * The identifier of the execution context.
   */
  contextId: ContextId;

  /**
   * The error message.
   */
  message: String;

  /**
   * The location of a file producing the error.
   */
  path?: Path;
}
```

#### Errors

None

### `executionContext/executionStatus`

Sent from the server to the client to inform about a status of execution.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  /**
   * The identifier of the execution context.
   */
  contextId: ContextId;

  /**
   * The list of encountered problems.
   */
  diagnostics: Diagnostic[];
}
```

#### Errors

None

### `executionContext/executeExpression`

This message allows the client to execute an arbitrary expression on a given
node. It behaves like oneshot
[`executionContext/attachVisualisation`](#executioncontextattachvisualisation)
visualisation request, meaning that the visualisation expression will be
executed only once.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface ExecuteExpressionRequest {
  visualisationId: UUID;
  expressionId: UUID;
  visualisationConfig: VisualisationConfiguration;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`AccessDeniedError`](#accessdeniederror) when the user does not hold the
  `executionContext/canModify` capability for this context.
- [`ContextNotFoundError`](#contextnotfounderror) when context can not be found
  by provided id.
- [`ModuleNotFoundError`](#modulenotfounderror) to signal that the module with
  the visualisation cannot be found.
- [`VisualisationExpressionError`](#visualisationexpressionerror) to signal that
  the expression specified in the `VisualisationConfiguration` cannot be
  evaluated.

### `executionContext/attachVisualisation`

This message allows the client to attach a visualisation, potentially
preprocessed by some arbitrary Enso code, to a given node in the program.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface AttachVisualisationRequest {
  visualisationId: UUID;
  expressionId: UUID;
  visualisationConfig: VisualisationConfiguration;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`AccessDeniedError`](#accessdeniederror) when the user does not hold the
  `executionContext/canModify` capability for this context.
- [`ContextNotFoundError`](#contextnotfounderror) when context can not be found
  by provided id.
- [`ModuleNotFoundError`](#modulenotfounderror) to signal that the module with
  the visualisation cannot be found.
- [`VisualisationExpressionError`](#visualisationexpressionerror) to signal that
  the expression specified in the `VisualisationConfiguration` cannot be
  evaluated.

### `executionContext/detachVisualisation`

This message allows a client to detach a visualisation from the executing code.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface DetachVisualisationRequest {
  executionContextId: UUID;
  visualisationId: UUID;
  expressionId: UUID;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`AccessDeniedError`](#accessdeniederror) when the user does not hold the
  `executionContext/canModify` capability for this context.
- [`ContextNotFoundError`](#contextnotfounderror) when context can not be found
  by provided id.
- [`VisualisationNotFoundError`](#visualisationnotfounderror) when a
  visualisation can not be found.

### `executionContext/modifyVisualisation`

This message allows a client to modify the configuration for an existing
visualisation.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface ModifyVisualisationRequest {
  visualisationId: UUID;
  visualisationConfig: VisualisationConfiguration;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`AccessDeniedError`](#accessdeniederror) when the user does not hold the
  `executionContext/canModify` capability for this context.
- [`ContextNotFoundError`](#contextnotfounderror) when context can not be found
  by provided id.
- [`ModuleNotFoundError`](#modulenotfounderror) to signal that the module with
  the visualisation cannot be found.
- [`VisualisationExpressionError`](#visualisationexpressionerror) to signal that
  the expression specified in the `VisualisationConfiguration` cannot be
  evaluated.
- [`VisualisationNotFoundError`](#visualisationnotfounderror) when a
  visualisation can not be found.

### `executionContext/visualisationUpdate`

This message is responsible for providing a visualisation data update to the
client.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Data
- **Visibility:** Public

The `visualisationData` component of the table definition _must_ be
pre-serialized before being inserted into this message. As far as this level of
transport is concerned, it is just a binary blob.

#### Parameters

```idl
namespace org.enso.languageserver.protocol.binary;

//A visualisation context identifying a concrete visualisation.
table VisualisationContext {

  //A visualisation identifier.
  visualisationId: EnsoUUID (required);

  //A context identifier.
  contextId: EnsoUUID (required);

  //An expression identifier.
  expressionId: EnsoUUID (required);

}

//An event signaling visualisation update.
table VisualisationUpdate {

  //A visualisation context identifying a concrete visualisation.
  visualisationContext: VisualisationContext (required);

  //A visualisation data.
  data: [ubyte] (required);

}

root_type VisualisationUpdate;
```

#### Errors

N/A

### `executionContext/visualisationEvaluationFailed`

Signals that an evaluation of a visualisation expression on the computed value
has failed.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
interface VisualisationEvaluationFailed {
  /**
   * An execution context identifier.
   */
  contextId: ContextId;

  /**
   * A visualisation identifier.
   */
  visualisationId: UUID;

  /**
   * An identifier of a visualised expression.
   */
  expressionId: UUID;

  /**
   * An error message.
   */
  message: string;

  /**
   * Detailed information about the error.
   */
  diagnostic?: Diagnostic;
}
```

#### Errors

N/A

## Search Operations

Search operations allow requesting for the autocomplete suggestions and search
for the documentation. Search operations return links to the items in the
Suggestions Database instead of returning full entries. Suggestions Database is
a key-value storage with [`SuggestionEntry`](#suggestionentry) values.

### Suggestions Database Example

The following code snippet shows examples of the database entries.

```ruby
type MyType a b

type Maybe
    Nothing
    Just a

    is_just = case this of
        Just _  -> true
        Nothing -> false

foo x =
    10 - x

Number.baz x =
    this + x * 10

main =
    x = foo 42
    y = x.baz x
    IO.println y
```

#### MyType

```typescript
<SuggestionEntryAtom>{
  name: "MyType",
  arguments: [],
  returnType: "MyType",
};
```

#### Maybe.Nothing

```typescript
<SuggestionEntryAtom>{
  name: "Nothing",
  arguments: [],
  returnType: "Maybe",
};
```

#### Maybe.Just

```typescript
<SuggestionEntryAtom>{
  name: "Just",
  arguments: [
    {
      name: "a",
      type: "Any",
      isSuspended: false,
      hasDefault: false,
    },
  ],
  returnType: "Maybe",
};
```

#### Maybe.is_just

```typescript
<SuggestionEntryMethod>{
  name: "is_just",
  arguments: [],
  selfType: "Maybe",
  returnType: "Bool",
};
```

#### foo

```typescript
<SuggestionEntryFunction>{
  name: "foo",
  arguments: [
    {
      name: "x",
      type: "Number",
      isSuspended: false,
      hasDefault: false,
    },
  ],
  returnType: "Bool",
};
```

#### Number.baz

```typescript
<SuggestionEntryMethod>{
  name: "baz",
  arguments: [
    {
      name: "x",
      type: "Number",
      isSuspended: false,
      hasDefault: false,
    },
  ],
  selfType: "Number",
  returnType: "Number",
};
```

#### Local x

```typescript
<SuggestionEntryLocal>{
  name: "x",
  returnType: "Number",
};
```

#### Local y

```typescript
<SuggestionEntryLocal>{
  name: "y",
  returnType: "Number",
};
```

### `search/getSuggestionsDatabase`

Sent from client to the server to receive the full suggestions database.

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
{
  // The list of suggestions database entries
  entries: [SuggestionsDatabaseEntry];
  // The version of received suggestions database
  currentVersion: number;
}
```

#### Errors

- [`SuggestionsDatabaseError`](#suggestionsdatabaseerror) an error accessing the
  suggestions database
- [`ProjectNotFoundError`](#projectnotfounderror) project is not found in the
  root directory

### `search/invalidateSuggestionsDatabase`

Sent from client to the server to clean the suggestions database resetting the
version.

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
null;
```

#### Errors

- [`SuggestionsDatabaseError`](#suggestionsdatabaseerror) an error accessing the
  suggestions database

### `search/getSuggestionsDatabaseVersion`

Sent from client to the server to receive the current version of the suggestions
database.

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
{
  // The version of the suggestions database
  currentVersion: number;
}
```

#### Errors

- [`SuggestionsDatabaseError`](#suggestionsdatabaseerror) an error accessing the
  suggestions database
- [`ProjectNotFoundError`](#projectnotfounderror) project is not found in the
  root directory

### `search/suggestionsDatabaseUpdate`

Sent from server to the client to inform abouth the change in the suggestions
database.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  updates: [SuggestionsDatabaseUpdate];
  currentVersion: number;
}
```

#### Errors

None

### `search/suggestionsOrderDatabaseUpdate`

Sent from server to the client to inform abouth the change in the suggestions
order database.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  updates: [SuggestionsOrderDatabaseUpdate];
}
```

#### Errors

None

### `search/completion`

Sent from client to the server to receive the autocomplete suggestion.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  // The edited file
  file: Path;
  // The cursor position
  position: Position;
  // Filter by methods with the provided self type
  selfType?: string;
  // Filter by the return type
  returnType?: string;
  // Filter by the suggestion types
  tags?: [SuggestionEntryType];
}
```

#### Result

The identifiers in `results` are guaranteed to be ordered by the specificity of
the type match.

```typescript
{
  results: [SuggestionId];
  currentVersion: number;
}
```

#### Errors

- [`SuggestionsDatabaseError`](#suggestionsdatabaseerror) an error accessing the
  suggestions database
- [`ProjectNotFoundError`](#projectnotfounderror) project is not found in the
  root directory
- [`ModuleNameNotResolvedError`](#modulenamenotresolvederror) the module name
  cannot be extracted from the provided file path parameter

### `search/import`

Sent from client to the server to receive the information required for module
import.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  /**
   * The id of suggestion to import.
   */
  id: SuggestionId;
}
```

#### Result

```typescript
{
  /**
   * The definition module of the suggestion.
   */
  module: String;

  /**
   * The name of the resolved suggestion.
   */
  symbol: String;

  /**
   * The list of modules that re-export the suggestion. Modules are ordered
   * from the least to most nested.
   */
  exports: Export[];
}
```

#### Errors

- [`SuggestionsDatabaseError`](#suggestionsdatabaseerror) an error accessing the
  suggestions database
- [`SuggestionNotFoundError`](#suggestionnotfounderror) the requested suggestion
  was not found in the suggestions database

## Input/Output Operations

The input/output portion of the language server API deals with redirecting
stdin/stdout/stderr of Enso programs to the clients of the language server. This
is incredibly important for enabling the high levels of interactivity required
by Enso Studio.

### `io/redirectStandardOutput`

This message allows a client to redirect the standard output of Enso programs.
Once the standard output is redirected, the Language server will notify the
client about new output data by emitting `io/standardOutputAppended` messages.

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
null;
```

#### Errors

N/A

### `io/suppressStandardOutput`

This message allows a client to suppress the redirection of the standard output.

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
null;
```

#### Errors

N/A

### `io/standardOutputAppended`

Sent from the server to the client to inform that new output data are available
for the standard output.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  output: String;
}
```

### `io/redirectStandardError`

This message allows a client to redirect the standard error of Enso programs.
Once the standard error is redirected, the Language server will notify the
client about new output data by emitting `io/standardErrorAppended` messages.

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
null;
```

#### Errors

N/A

### `io/suppressStandardError`

This message allows a client to suppress the redirection of the standard error.

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
null;
```

#### Errors

N/A

### `io/standardErrorAppended`

Sent from the server to the client to inform that new output data are available
for the standard error.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  output: String;
}
```

### `io/feedStandardInput`

This message allows a client to feed the standard input of Enso programs.

- **Type:** Request
- **Direction:** Client -> Server
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
{
  input: String;
  isLineTerminated: Boolean;
}
```

#### Result

```typescript
null;
```

#### Errors

N/A

### `io/waitingForStandardInput`

Sent from the server to the client to inform that an Enso program is suspended
by `IO.readln`. This message is used to notify a client that she should feed the
standard input.

- **Type:** Notification
- **Direction:** Server -> Client
- **Connection:** Protocol
- **Visibility:** Public

#### Parameters

```typescript
null;
```

## Library-Related Operations

The library-related operations provide the Language Server with capabilities to
check and modify project's edition settings, list editions published in a given
edition, create local library projects which can be imported in the currently
opened project and publish them.

### `editions/listAvailable`

Lists editions available on the system.

Moreover, if `update` is set to `true`, it will download any new editions from
the repositories and include them in the result as well.

> Currently, if `update` was `true` but some downloads failed, the endpoint will
> still return a success, just containing the editions that were already
> available. In the future it should emit warnings using proper notification
> channels.

#### Parameters

```typescript
{
  update: Boolean;
}
```

#### Result

```typescript
{
  editionNames: [String];
}
```

### `editions/resolve`

Resolves settings implied by the edition.

> Currently, it only resolves the engine version, as only it is needed, but
> other settings may be added if necessary.

#### Parameters

```typescript
{
  edition: EditionReference;
}
```

#### Result

```typescript
{
  engineVersion: String;
}
```

#### Errors

- [`EditionNotFoundError`](#editionnotfounderror) indicates that the requested
  edition, or some edition referenced in the ancestors of the edition being
  resolved, could not be found.
- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

### `editions/getProjectSettings`

Returns the currently set edition-related settings of the project.

Currently, it only returns the parent edition and local library preference. Once
more advanced edition settings are to be supported in the IDE, this endpoint
will be extended.

#### Parameters

```typescript
null;
```

#### Result

```typescript
{
    parentEdition?: String;
    preferLocalLibraries: Boolean;
}
```

The `parentEdition` may be missing if it is not set. It is possible to manually
edit the `pacakge.yaml` and generate a valid edition config that does not
specify a parent edition.

### `editions/setProjectParentEdition`

Sets the parent edition of the project to a specific edition.

This change may even change the version of the engine associated with the
project, so for the changes to take effect, the language server may need to be
restarted. The endpoint only modifies the `pacakge.yaml` file, which is
preloaded in the Language Server, so it is IDE's responsibility to re-open the
project.

It returns an optional field `needsRestart` which specifies whether the Language
Server needs to be restarted for the change to take effect. If the field is
missing, it should be treated as set to `false`. In the current version it is
always set to `true`.

#### Parameters

```typescript
{
  newEditionName: String;
}
```

#### Result

```typescript
{
    needsRestart?: Boolean;
}
```

#### Errors

- [`EditionNotFoundError`](#editionnotfounderror) indicates that the requested
  edition could not be found.
- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

### `editions/setProjectLocalLibrariesPreference`

Sets the `prefer-local-libraries` setting of the project, which specifies if
libraries from `ENSO_HOME/libraries` should take precedence over the ones
defined in the edition.

> This may change which libraries should be loaded in the project. In the future
> it may be possible that this reload could happen dynamically, however
> currently, the language server needs to be restarted (in the same way as for
> `editions/setProjectParentEdition`) for the changes to take effect.

It returns an optional field `needsRestart` which specifies whether the Language
Server needs to be restarted for the change to take effect. If the field is
missing, it should be treated as set to `false`. In the current version it is
always set to `true`.

#### Parameters

```typescript
{
  preferLocalLibraries: Boolean;
}
```

#### Result

```typescript
{
    needsRestart?: Boolean;
}
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

### `editions/listDefinedLibraries`

Lists all libraries defined in an edition (or all of its parents).

This can be used to display which libraries can be downloaded / added to the
project.

This does not include local libraries not defined explicitly in the project's
edition, even if they can be resolved as per `prefer-local-libraries` setting.
To get local libraries that are not directly referenced in the edition, use
[`library/listLocal`](#librarylistlocal) instead.

#### Parameters

```typescript
{
  edition: EditionReference;
}
```

#### Result

```typescript
{
  availableLibraries: [LibraryEntry];
}
```

#### Errors

- [`EditionNotFoundError`](#editionnotfounderror) indicates that the requested
  edition, or an edition referenced in one of its parents, could not be found.
- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

### `library/listLocal`

Lists all local libraries available in the system.

#### Parameters

```typescript
null;
```

#### Result

```typescript
{
  localLibraries: [LibraryEntry];
}
```

#### Errors

- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

### `library/create`

Creates a new project package, but placed not in the projects directory, but in
the local libraries directory, so that other projects (including the current
one) that can resolve local libraries will be able to import it.

The created library package inherits all edition settings from the current
project.

The endpoint just returns an empty message at success. Once this operation
finishes, the IDE can add the import `import <namespace>.<name>` to the open
file which will import the newly created library (the IDE must also ensure that
`prefer-local-libraries` is set to `true` or otherwise, it must add a proper
override to its own edition file to see this local library). Once the import is
added, the library will be loaded and its content root will be sent in a
[`file/rootAdded`](#filerootadded) notification.

#### Parameters

```typescript
{
  namespace: String;
  name: String;
  authors: [String];
  maintainers: [String];
  license: String;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`LibraryAlreadyExists`](#libraryalreadyexists) to signal that a library with
  the given namespace and name already exists.
- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

### `library/getMetadata`

Gets metadata associated with a local library that will be used for publishing.

All returned fields are optional, as they may be missing.

#### Parameters

```typescript
{
  namespace: String;
  name: String;
}
```

#### Results

```typescript
{
  description?: String;
  tagLine?: String;
}
```

#### Errors

- [`LocalLibraryNotFound`](#locallibrarynotfound) to signal that a local library
  with the given name does not exist on the local libraries path.
- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

### `library/setMetadata`

Sets metadata associated with a local library that will be used for publishing.

All metadata fields are optional. If a field is not set in the parameters, it
will be removed from the metadata (if it was present before).

#### Parameters

```typescript
{
  namespace: String;
  name: String;
  description?: String;
  tagLine?: String;
}
```

#### Results

```typescript
null;
```

#### Errors

- [`LocalLibraryNotFound`](#locallibrarynotfound) to signal that a local library
  with the given name does not exist on the local libraries path.
- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

### `library/publish`

Publishes a library located in the local libraries directory to the main Enso
library repository.

If `bumpVersionAfterPublish` is set to true, after publishing the library, its
version is bumped automatically, so that future publications will not clash
versions. This is a temporary solution and in the longer-term it should be
replaced with separate settings allowing to arbitrarily modify the library
version from the IDE.

The metadata for publishing the library can be set with
[`library/setMetadata`](#librarysetmetadata). If it was not set, the publish
operation will still proceed, but that metadata will be missing.

#### Parameters

```typescript
{
  namespace: String;
  name: String;
  authToken: String;

  bumpVersionAfterPublish?: Boolean;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`LibraryPublishError`](#librarypublisherror) to signal that the server did
  not accept to publish the library (for example because a library with the same
  version already exists).
- [`LibraryRepositoryAuthenticationError`](#libraryrepositoryauthenticationerror)
  to signal an authentication failure.
- [`LibraryUploadError`](#libraryuploaderror) to signal that the upload
  operation has failed, for network-related reasons.
- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

### `library/preinstall`

Ensures that the requested library and all of its (transitive) dependencies, at
versions as resolved by the current project's edition, are downloaded.

Once this operation completes, the library import can be added to the project
and it is guaranteed that (unless there were unexpected changes to the system,
like files being manually deleted) no further downloads will be needed, so that
the import should load quickly.

This can be used by the IDE to predownload the library that is about to be added
to a project, to avoid freezing the compiler for the time the dependencies are
being downloaded.

While this operation is in progress, multiple series of `task/*` notifications
may be sent, indicating progress of particular components being downloaded and
installed.

#### Parameters

```typescript
{
  namespace: String;
  name: String;
}
```

#### Result

```typescript
null;
```

#### Errors

- [`LibraryNotResolved`](#librarynotresolved) to signal that the requested
  library or one of its dependencies could not be resolved.
- [`LibraryDownloadError`](#librarydownloaderror) to signal that the download
  operation has failed, for network-related reasons, or because the library was
  missing in the repository. The error includes the name and version of the
  library that failed to download - as when preinstalling a specific library the
  failure may be tied not to that library itself but also one of its
  dependencies.
- [`FileSystemError`](#filesystemerror) to signal a generic, unrecoverable
  file-system error.

## Errors

The language server component also has its own set of errors. This section is
not a complete specification and will be updated as new errors are added.

Besides the required `code` and `message` fields, the errors may have a `data`
field which can store additional error-specific payload.

### `Error`

An error container for the binary connection that contains a code, message and
payload.

```idl
namespace org.enso.languageserver.protocol.binary;

table Error {
  // A unique error code identifying error type.
  code: int;

  // An error message.
  message: string (required);

  // Additional payloads for the error.
  data : ErrorPayload;
}

union ErrorPayload {
  ...
}
```

Note:

- The union `ErrorPayload` will be extended with additional payloads as
  necessary.
- All textual-protocol errors can be represented using this structure.

### `AccessDeniedError`

It signals that a user doesn't have access to a resource.

```typescript
"error" : {
  "code" : 100,
  "message" : "Access denied"
}
```

### `FileSystemError`

This error signals generic file system errors.

```typescript
"error" : {
  "code" : 1000,
  "message" : String
}
```

### `ContentRootNotFoundError`

The error informs that the requested content root cannot be found.

```typescript
"error" : {
  "code" : 1001,
  "message" : "Content root not found"
}
```

### `FileNotFound`

It signals that requested file doesn't exist.

```typescript
"error" : {
  "code" : 1003,
  "message" : "File not found"
}
```

### `FileExists`

It signals that file already exists.

```typescript
"error" : {
  "code" : 1004,
  "message" : "File already exists"
}
```

### `OperationTimeoutError`

It signals that IO operation timed out.

```typescript
"error" : {
  "code" : 1005,
  "message" : "IO operation timeout"
}
```

### `NotDirectory`

It signals that provided path is not a directory.

```typescript
"error" : {
  "code" : 1006,
  "message" : "Path is not a directory"
}
```

### `NotFile`

It signals that the provided path is not a file.

```typescript
"error" : {
  "code" : 1007,
  "message" : "Path is not a file"
}
```

### `CannotOverwrite`

Signals that a streaming file write cannot overwrite a portion of the requested
file.

```typescript
"error" : {
  "code" : 1008,
  "message" : "Cannot overwrite the file without `overwriteExisting` set"
}
```

### `ReadOutOfBounds`

Signals that the requested file read was out of bounds for the file's size.

```typescript
"error" : {
  "code" : 1009
  "message" : "Read is out of bounds for the file"
  "data" : {
    "fileLength" : 0
  }
}
```

### `CannotDecode`

Signals that the project configuration cannot be decoded.

```typescript
"error" : {
  "code" : 1010
  "message" : "Cannot decode the project configuration"
}
```

```idl
namespace org.enso.languageserver.protocol.binary;

table ReadOutOfBoundsError {
  // The actual length of the file.
  fileLength : ulong (required);
}
```

### `StackItemNotFoundError`

It signals that provided stack item was not found.

```typescript
"error" : {
  "code" : 2001,
  "message" : "Stack item not found"
}

```

### `ContextNotFoundError`

It signals that provided context was not found.

```typescript
"error" : {
  "code" : 2002,
  "message" : "Context not found"
}
```

### `EmptyStackError`

It signals that stack is empty.

```typescript
"error" : {
  "code" : 2003,
  "message" : "Stack is empty"
}
```

### `InvalidStackItemError`

It signals that stack is invalid in this context.

```typescript
"error" : {
  "code" : 2004,
  "message" : "Invalid stack item"
}
```

### `ModuleNotFoundError`

It signals that the given module cannot be found.

```typescript
"error" : {
  "code" : 2005,
  "message" : "Module not found [Foo.Bar.Baz]"
}
```

### `VisualisationNotFoundError`

It signals that the visualisation cannot be found.

```typescript
"error" : {
  "code" : 2006,
  "message" : "Visualisation not found"
}
```

### `VisualisationExpressionError`

It signals that the expression specified in the `VisualisationConfiguration`
cannot be evaluated. The error contains an optional `data` field of type
[`Diagnostic`](#diagnostic) providing error details.

```typescript
"error" : {
  "code" : 2007,
  "message" : "Evaluation of the visualisation expression failed [i is not defined]"
  "data" : {
    "kind" : "Error",
    "message" : "i is not defined",
    "path" : null,
    "location" : {
      "start" : {
        "line" : 0,
        "character" : 8
      },
      "end" : {
        "line" : 0,
        "character" : 9
      }
    },
    "expressionId" : "aa1f75c4-8c4d-493d-a6a7-72123a52f084",
    "stack" : []
  }
}
```

### `FileNotOpenedError`

Signals that a file wasn't opened.

```typescript
"error" : {
  "code" : 3001,
  "message" : "File not opened"
}
```

### `TextEditValidationError`

Signals that validation has failed for a series of edits.

```typescript
"error" : {
  "code" : 3002,
  "message" : "The start position is after the end position"
}
```

### `InvalidVersionError`

Signals that version provided by a client doesn't match to the version computed
by the server.

```typescript
"error" : {
  "code" : 3003,
  "message" : "Invalid version [client version: ade2967cab172183d1a67ea40cb8e92e23218764bc9934c3795fcea5, server version: 7602967cab172183d1a67ea40cb8e92e23218764bc9934c3795fcea5]"
}
```

### `WriteDeniedError`

Signals that the client doesn't hold write lock to the buffer.

```typescript
"error" : {
  "code" : 3004,
  "message" : "Write denied"
}
```

### `CapabilityNotAcquired`

Signals that requested capability is not acquired.

```typescript
"error" : {
  "code" : 5001,
  "message" : "Capability not acquired"
}
```

### `SessionNotInitialisedError`

Signals that requested cannot be proccessed, beacuse session is not initialised.

```typescript
"error" : {
  "code" : 6001,
  "message" : "Session not initialised"
}
```

### `SessionAlreadyInitialisedError`

Signals that session is already initialised.

```typescript
"error" : {
  "code" : 6002,
  "message" : "Session already initialised"
}
```

### `ResourcesInitializationError`

Signals about the failure in the Language Server initialization process.

```typescript
"error" : {
  "code" : 6003,
  "message" : "Failed to initialize the Language Server resources"
}
```

### `SuggestionsDatabaseError`

Signals about an error accessing the suggestions database.

```typescript
"error" : {
  "code" : 7001,
  "message" : "Suggestions database error"
}
```

### `ProjectNotFoundError`

Signals that the project not found in the root directory.

```typescript
"error" : {
  "code" : 7002,
  "message" : "Project not found in the root directory"
}
```

### `ModuleNameNotResolvedError`

Signals that the module name can not be resolved for the given file.

```typescript
"error" : {
  "code" : 7003,
  "message" : "Module name can't be resolved for the given file"
}
```

### `SuggestionNotFoundError`

Signals that the requested suggestion was not found.

```typescript
"error" : {
  "code" : 7004,
  "message" : "Requested suggestion was not found"
}
```

### `EditionNotFoundError`

Signals that an edition could not be found.

Its payload includes the name of the edition that could not be found.

```typescript
"error" : {
  "code" : 8001,
  "message" : "Edition [<name>] could not be found.",
  "payload" : {
    "editionName": "<name>"
  }
}
```

### `LibraryAlreadyExists`

Signals that a local library with the specified namespace and name combination
already exists, so it cannot be created again.

```typescript
"error" : {
  "code" : 8002,
  "message" : "Library [<namespace>.<name>] already exists."
}
```

### `LibraryRepositoryAuthenticationError`

Signals that authentication to the library repository was declined.

```typescript
"error" : {
  "code" : 8003,
  "message" : "Authentication failed: [message]"
}
```

### `LibraryPublishError`

Signals that a request to the library repository failed.

```typescript
"error" : {
  "code" : 8004,
  "message" : "Could not publish the library: [message]"
}
```

### `LibraryUploadError`

Signals that uploading the library failed for network-related reasons.

```typescript
"error" : {
  "code" : 8005,
  "message" : "Could not upload the library: [message]"
}
```

### `LibraryDownloadError`

Signals that downloading the library failed for network-related reasons or that
it was not available in the repository.

```typescript
"error" : {
  "code" : 8006,
  "message" : "Could not download the library: [message]",
  "payload" : {
    "namespace" : "<namespace>",
    "name" : "<name>",
    "version": "<version>"
  }
}
```

### `LocalLibraryNotFound`

Signals that a local library with the specified namespace and name combination
was not found on the local libraries path.

```typescript
"error" : {
  "code" : 8007,
  "message" : "Local library [<namespace>.<name>] has not been found."
}
```

### `LibraryNotResolved`

Signals that a library could not be resolved - it was not defined in the edition
and the settings did not allow to resolve local libraries or it did not exist
there either.

```typescript
"error" : {
  "code" : 8008,
  "message" : "Could not resolve [<namespace>.<name>].",
  "payload" : {
    "namespace" : "<namespace>",
    "name" : "<name>"
  }
}
```
