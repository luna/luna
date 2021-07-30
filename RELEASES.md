# Enso Next

## Libraries

- Added support for writing tables to XLSX spreadsheets
  ([#1906](https://github.com/enso-org/enso/pull/1906)).
- Added documentation for the new searcher categories
  ([#1910](https://github.com/enso-org/enso/pull/1910)).

# Enso 0.2.17 (2021-07-28)

## Interpreter/Runtime

- Added support for documenting modules directly
  ([#1900](https://github.com/enso-org/enso/pull/1900)).

## Tooling

- Added support for creating projects from a template
  ([#1902](https://github.com/enso-org/enso/pull/1902)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-07-28)
  for issues opened before the release date.

# Enso 0.2.16 (2021-07-23)

## Interpreter/Runtime

- Added support for the `ALIAS` tag in documentation blocks for use by the
  searcher ([#1896](https://github.com/enso-org/enso/pull/1896)).

## Tooling

- Implemented a basic library downloader
  ([#1885](https://github.com/enso-org/enso/pull/1885)), allowing the
  downloading of missing libraries.

## Libraries

- Added support for reading XLS and XLSX spreadsheets
  ([#1879](https://github.com/enso-org/enso/pull/1879)).
- Added support for serializing tables into CSV files.
  ([#1894](https://github.com/enso-org/enso/pull/1894)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-07-23)
  for issues opened before the release date.

# Enso 0.2.15 (2021-07-19)

## Tooling

- Implement parts of the new Language Server API related to library support
  ([#1875](https://github.com/enso-org/enso/pull/1875)). Parts of the API are
  still mocked internally, but they are supported externally for testing
  purposes.

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-07-19)
  for issues opened before the release date.

# Enso 0.2.14 (2021-07-15)

## Interpreter/Runtime

- Ensure that the module used by a visualization is preloaded when the
  visualization is being attached
  ([#1857](https://github.com/enso-org/enso/pull/1857)).
- Fix an issue with the `HostClassLoader` getting into a broken state
  ([#1867](https://github.com/enso-org/enso/pull/1867)).

## Tooling

- Implemented an HTTP endpoint returning the time that the language server has
  spent idle ([#1847](https://github.com/enso-org/enso/pull/1847)).
- Fix a bug where the `project/list` endpoint would fail if any of the projects
  referenced an edition that does not exist anymore
  ([#1858](https://github.com/enso-org/enso/pull/1858)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-07-15)
  for issues opened before the release date.

# Enso 0.2.13 (2021-07-09)

## Interpreter/Runtime

- Implemented changes to the import and export syntax, requiring to provide the
  project namespace, or use the new `project` keyword to import from the current
  project ([#1806](https://github.com/enso-org/enso/pull/1806)).
- Fixed a bug where unresolved imports would crash the compiler
  ([#1822](https://github.com/enso-org/enso/pull/1822)).
- Implemented the ability to dynamically load local libraries
  ([#1826](https://github.com/enso-org/enso/pull/1826)). Currently, it only
  supports the loading of local libraries, but will be integrated with the
  editions system soon.
- Integrated the library loading mechanism with the editions system
  ([#1832](https://github.com/enso-org/enso/pull/1832)).

## Tooling

- Added namespace information to project manager messages
  ([#1820](https://github.com/enso-org/enso/pull/1820)).
- Fixed a bug where the Project Manager would not preinstall the Graal runtime
  if the engine was already installed and only its runtime was missing
  ([#1824](https://github.com/enso-org/enso/pull/1824)).
- Extended content root mechanism to provide the home directory and filesystem
  roots on startup ([#1821](https://github.com/enso-org/enso/pull/1821)). It now
  also supports dynamically adding content roots and notifies the IDE when a new
  content root is added.
- Connected the documentation generator with Enso compiler and suggestion
  database, making the documentation generated before being sent to the IDE,
  using a faster Scala-based generator instead of a ScalaJS-based one on IDE's
  side, also enabling us to connect many AST elements with docs. See
  ([#1744](https://github.com/enso-org/enso/pull/1744).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-07-09)
  for issues opened before the release date.

# Enso 0.2.12 (2021-06-24)

## Interpreter/Runtime

- Upgraded the underlying runtime to
  [GraalVM 21.1.0](https://github.com/graalvm/graalvm-ce-builds/releases/tag/vm-21.1.0)
  ([#1738](https://github.com/enso-org/enso/pull/1738)). This brings a raft of
  bug-fixes and improvements to how quickly Enso can reach its peak performance.
- Added support for bidirectional dataflow tracking to the `DataflowAnalysis`
  pass ([#1748](https://github.com/enso-org/enso/pull/1748)). This will allow
  the interpreter to perform more detailed analyses in the future to enable
  optimisations and new features.

## Tooling

- Added support for higher-kinded types in suggestions in the language server
  ([#1712](https://github.com/enso-org/enso/pull/1712)). This allows the
  searcher to make more accurate suggestions when working with collection types.
- Fixed an issue where symlinks were not extracted properly when installing a
  runtime for Enso ([#1718](https://github.com/enso-org/enso/pull/1718)).
- Implemented log masking ([#1732](https://github.com/enso-org/enso/pull/1732)).
  This feature masks personally identifiable information in the logs, such as
  code literals, computed values, and user environment variables.
- Added support for evaluating one-shot expressions on the result values of
  arbitrary expressions ([#1749](https://github.com/enso-org/enso/pull/1749)).
  This is very useful for enabling more advanced introspection in the IDE.
- Added the `workspace/projectInfo` endpoint to the language server
  ([#1759](https://github.com/enso-org/enso/pull/1759)). This allows the IDE to
  get information about the running project in contexts where the project
  manager isn't available or works differently.
- Added the `file/checksum` endpoint to the language server
  ([#1787](https://github.com/enso-org/enso/pull/1787)). This allows the IDE to
  verify the integrity of files that it has transferred. The checksum is
  calculated in a streaming fashion so the checksummed file need not be resident
  in memory all at once.
- Added support for reading and writing byte ranges in files remotely
  ([#1795](https://github.com/enso-org/enso/pull/1795)). This allows the IDE to
  transfer files to a remote back-end in a streaming fashion.
- Added support for multiple content roots in the language server
  ([#1800](https://github.com/enso-org/enso/pull/1800/)). It is not yet exposed
  to the IDE, as this will be done as part of future work.
- Modified the `package.yaml` format in preparation for the library ecosystem
  ([#1797](https://github.com/enso-org/enso/pull/1797)). The `engine-version`
  field has been deprecated in favour of an `edition` field that allows to set
  up the engine version and dependency resolution using the upcoming Edition
  system. New tools will still be able to read the old format, but upon
  modification, they will save changes in the new format. As the `edition` file
  did not exist in the older version, old tools will actually correctly load the
  migrated package file (as we allow for unknown fields), but they will not know
  how to interpret the new `edition` field and so will fall back to using the
  `default` engine version, which may be unexpected. Ideally, after migration,
  the project should be used only with the new tools. The affected tools are the
  Launcher and the Project Manager.
- Added documentation and a minimal tool for hosting custom library repositories
  ([#1804](https://github.com/enso-org/enso/pull/1804)).
- Added `documentationHtml` field to Suggestions database entry
  ([#1791](https://github.com/enso-org/enso/pull/1791))

## Libraries

- Overhauled the examples throughout the standard library
  ([#1707](https://github.com/enso-org/enso/pull/1707),
  [#1725](https://github.com/enso-org/enso/pull/1725), and
  [#1731](https://github.com/enso-org/enso/pull/1731)). These examples all now
  conform to a standard format and have been tested to work.
- Made some miscellaneous fixes to the `HTTP` portion of the `Base` library that
  fix a few bugs ([#1722](https://github.com/enso-org/enso/pull/1722)).
- Removed reflective access when loading the OpenCV library
  ([#1727](https://github.com/enso-org/enso/pull/1727)). Illegal reflective
  access operations were deprecated and will be denied in future JVM releases.
- Overhauled the types we use for errors throughout the standard library
  ([#1734](https://github.com/enso-org/enso/pull/1734)). They are now much more
  informative, and should provide more clarity when things go wrong.
- Re-wrote the documentation generator for the Enso website from Python into
  Scala ([#1729](https://github.com/enso-org/enso/pull/1729)). This has greatly
  improved the performance, enabling us to generate the documentation structure
  for the entire standard library 8-10 times faster than before.
- Implemented Standard Library methods for controlling default visualizations in
  the graphical interface ([#1786](https://github.com/enso-org/enso/pull/1786)).

## Miscellaneous

- Adding a pipeline for automatic nightly builds
  ([#1689](https://github.com/enso-org/enso/pull/1689)). During the night after
  each workday any new changes to the `main` branch are built and released as a
  nightly build. The nightly builds can be useful to preview in-development
  features, but they should not be relied on as they are not considered stable.
  Only the 3 latest nightly builds are kept, so the nightly versions become
  obsolete very quickly.

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-06-24)
  for issues opened before the release date.

# Enso 0.2.11 (2021-04-28)

## Tooling

- Added a feature that allows the tooling to install
  [GraalVM](https://graalvm.org) language implementations to existing runtimes
  ([#1660](https://github.com/enso-org/enso/pull/1660)).
- Fixed an issue that would prevent the language server from starting if
  projects had clashing identifiers
  ([#1665](https://github.com/enso-org/enso/pull/1665)).
- Added support to the language server for suggesting the module types
  themselves.
- Added support for reporting errors in visualisation code, making it much
  simpler to write new visualisation preprocessors
  ([#1671](https://github.com/enso-org/enso/pull/1671)). Previously the
  preprocessor would fail without any information as to what went wrong.
- Fixed an issue where the language server's update state could become
  desynchronised with the IDE's one
  ([#1691](https://github.com/enso-org/enso/pull/1691)). This meant that the IDE
  and language server didn't agree on what had been sent, and hence the IDE
  would miss out on certain updates.
- Added a schema version to the suggestions database, allowing the tooling to
  detect out-of-date versions and upgrade them
  ([#1703](https://github.com/enso-org/enso/pull/1703)).
- Added detailed logging to the tooling boot sequence to help us debug issues
  that users are seeing ([#1704](https://github.com/enso-org/enso/pull/1704)).

## Libraries

- Fixed some inconsistent naming around the `Maybe` type
  ([#1666](https://github.com/enso-org/enso/pull/1666)).
- Added the `.sum` method for vectors of numeric types
  ([#1702](https://github.com/enso-org/enso/pull/1702)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-04-28)
  for issues opened before the release date.

# Enso 0.2.10 (2021-04-07)

## Interpreter/Runtime

- Added support for the Python and R runtimes to the bundled runtime
  ([#1644](https://github.com/enso-org/enso/pull/1644)).

## Tooling

- Added a feature to ensure that suggestions are ranked by type specificity,
  with the more specific suggestions being ranked first
  ([#1629](https://github.com/enso-org/enso/pull/1629)).
- Fixed a raft of small issues in the runtime server that caused bugs in the
  engine's interaction with the IDE
  ([#1633](https://github.com/enso-org/enso/pull/1633)).
- Fixed an issue where the suggestions database would get out of sync when a
  project was renamed ([#1647](https://github.com/enso-org/enso/pull/1647)).
- Fixed some bugs in the vector constructors that prevented them from working
  correctly on certain inputs
  ([#1650](https://github.com/enso-org/enso/pull/1650)).
- Added support to the launcher and project manager for installing companion
  runtimes alongside Enso ([#1651](https://github.com/enso-org/enso/pull/1651)).

## Libraries

- Added some additional useful methods to the `Standard.Table` library
  ([#1628](https://github.com/enso-org/enso/pull/1628)).
- Added a method to perform basic type inference on JSON, allowing converting
  Geo-JSON to a `Table` ([#1632](https://github.com/enso-org/enso/pull/1632)).
- Performed a comprehensive overhaul of the standard library documentation
  ([#1641](https://github.com/enso-org/enso/pull/1641)). It now has a standard
  format.

## Miscellaneous

- Fixed an issue where we were accidentally archiving two copies of some runtime
  components ([#1631](https://github.com/enso-org/enso/pull/1631)). Downloads
  should now be smaller.

# Enso 0.2.9 (2021-03-26)

## Tooling

- Fixed an issue where a panic would be improperly cached, resulting in no
  updates being sent to the IDE
  ([#1611](https://github.com/enso-org/enso/pull/1611)).
- Added a feature to provide searcher suggestions for types compatible with the
  type of `this` ([#1613](https://github.com/enso-org/enso/pull/1613)).

## Libraries

- Added a prototype of a library for working with images
  ([#1450](https://github.com/enso-org/enso/pull/1450)).
- Added histogram and scatter-plot visualisation support for the `Table` library
  ([#1608](https://github.com/enso-org/enso/pull/1608)).
- Fixed a bug in the implementation of `join` in the database library where it
  would join on the wrong table when doing a multiple-join
  ([#1614](https://github.com/enso-org/enso/pull/1614)).
- Fixed an outdated example for the `File.read` function.

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-03-26)
  for issues opened before the release date.

# Enso 0.2.8 (2021-03-19)

## Interpreter/Runtime

- Fixed miscellaneous crashes in the interpreter
  ([#1588](https://github.com/enso-org/enso/pull/1588)).

## Tooling

- Fixed an issue where the documentation for builtins wasn't getting indexed
  ([#1575](https://github.com/enso-org/enso/pull/1575)). The docs should now
  show up in the searcher!

## Libraries

- Added support for visualising database tables to the `Database` library
  ([#1582](https://github.com/enso-org/enso/pull/1582)).
- Reworked the `Process` library to work better in the IDE
  ([#1591](https://github.com/enso-org/enso/pull/1591)).
- Added a proper visualisation for `Array` and improved the one for `Vector`
  ([#1588](https://github.com/enso-org/enso/pull/1588)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-03-19)
  for issues opened before the release date.

# Enso 0.2.7 (2021-03-16)

## Interpreter/Runtime

- Added rudimentary support for interoperability with Python
  ([#1541](https://github.com/enso-org/enso/pull/1541)). Due to limitations of
  the underlying implementation
  ([GraalPython](https://github.com/oracle/graalpython)), this does not
  currently work on windows. We are working to have some means of supporting
  Python interop on Windows.
- Added rudimentary support for interoperability with R
  ([#1559](https://github.com/enso-org/enso/pull/1559)). Due to limitations of
  the underlying implementation ([FastR](https://github.com/oracle/fastr)), this
  does not currently work on windows. We are working to have some means of
  supporting R interop on Windows.
- Fixed a performance issue that occurred due to the interpreter observing
  deeper scopes than necessary during server-controlled execution
  ([#1564](https://github.com/enso-org/enso/pull/1564)). Execution of lambdas in
  the IDE is no longer far slower than it should be.
- Fixed an issue where interrupts during the execution of polyglot Java code
  would cause the host classloader to break, preventing further execution
  ([#1574](https://github.com/enso-org/enso/pull/1574)). _Please note_ that the
  fix that has been put in place is suboptimal, and means that we are currently
  unable to interrupt host code during its execution. We intend to fix this as
  soon as a fix for the host classloader has been merged upstream. You can track
  the associated issue in GraalVM
  [here](https://github.com/oracle/graal/issues/3273).
- Fixed an issue where the interpreter would crash due to project name shadowing
  ([#1571](https://github.com/enso-org/enso/pull/1571)).

## Tooling

- Added support for lazy initialization of the language server
  ([#1535](https://github.com/enso-org/enso/pull/1535)). This ensures that it
  behaves properly on systems where the working directories are on
  lazily-mounted NFS volumes.
- Fixed an issue where the unified logging infrastructure would disconnect,
  preventing it from gathering diagnostic logs
  ([#1563](https://github.com/enso-org/enso/pull/1563)). It now sends periodic
  keepalive messages to ensure that the connection has not timed out.
- Fixed project name validation in the project manager when renaming projects
  ([#1570](https://github.com/enso-org/enso/pull/1570)).

## Libraries

- Added support for materializing data from databases in the database library
  ([#1546](https://github.com/enso-org/enso/pull/1546)). You can now use this
  library to connect to your data sources (currently only SQLite and Postgres,
  but support for further backends is planned).
- Reorganized the standard library in order to support plans for its future
  evolution ([#1571](https://github.com/enso-org/enso/pull/1571)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-03-16)
  for issues opened before the release date.

# Enso 0.2.6 (2021-03-02)

## Interpreter/Runtime

- Fixed another issue where the parser would crash on partial issues, causing
  issues for both the Engine and IDE
  ([#1523](https://github.com/enso-org/enso/pull/1523)).
- Made panic messages short, fixing an issue where retention would cause
  ballooned memory usage while the full message contents were waiting to be
  logged ([#1528](https://github.com/enso-org/enso/pull/1528)).

## Tooling

- Fixed an issue where dynamic dependencies were analysed incorrectly, leading
  to missing updates for the IDE
  ([#1532](https://github.com/enso-org/enso/pull/1532)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-03-02)
  for issues opened before the release date.

# Enso 0.2.5 (2021-02-26)

## Interpreter/Runtime

- Fixed an issue where the parser would crash on partial inputs, causing issues
  for both the engine and IDE
  ([#1509](https://github.com/enso-org/enso/pull/1509)).
- Fixed a problem where `Type_Error`s would not be displayed properly when
  pretty printed ([#1504](https://github.com/enso-org/enso/pull/1504)).
- Fixed an issue with `_` desugaring where it would not desugar correctly when
  used in function position
  ([#1512](https://github.com/enso-org/enso/pull/1512)).

## Tooling

- Fixed an issue where suggestions were sometimes not being provided for modules
  other than `Base` ([#1507](https://github.com/enso-org/enso/pull/1507)).
- Fixed a few issues where expression and value updates were not sent when they
  should be ([#1516](https://github.com/enso-org/enso/pull/1516),
  [#1522](https://github.com/enso-org/enso/pull/1522), and
  [#1508](https://github.com/enso-org/enso/pull/1508)).

## Libraries

- Fixed a bug where sorting boolean columns in a `Table` would produce incorrect
  output ([#1505](https://github.com/enso-org/enso/pull/1505)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-02-26)
  for issues opened before the release date.

# Enso 0.2.4 (2021-02-23)

## Interpreter/Runtime

- Fixed another issue where dependency analysis was operating at too fine a
  granularity ([#1495](https://github.com/enso-org/enso/pull/1495)).
- Moved all user-facing errors to in-Enso errors, allowing them to be presented
  properly in the IDE, and interacted with by users
  ([#1487](https://github.com/enso-org/enso/pull/1487)).

## Tooling

- Fixed an issue where the runtime server would not send correct expression
  payloads for dataflow errors
  ([#1484](https://github.com/enso-org/enso/pull/1484)).

## Libraries

- Added "pretty" representations to all Error types, allowing for better display
  in the IDE ([#1498](https://github.com/enso-org/enso/pull/1498)).
- Updated the Table library with a raft of additional features
  ([#1489](https://github.com/enso-org/enso/pull/1489)). This includes table
  concatenation, direct indexing and column aggregation, as well as a general
  clean-up of the API pre-stabilisation.
- Added a flexible sorting mechanism to the Table library
  ([#1471](https://github.com/enso-org/enso/pull/1471)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-02-23)
  for issues opened before the release date.

# Enso 0.2.3 (2021-02-15)

## Tooling

- Improved the robustness of the project manager and language server in the
  presence of rapid restarts
  ([#1463](https://github.com/enso-org/enso/pull/1463)).

## Libraries

- Significantly improved the efficiency of visualising large tables through
  zero-cost translation to Enso's vectors
  ([#1476](https://github.com/enso-org/enso/pull/1476)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-02-15)
  for issues opened before the release date.

# Enso 0.2.2 (2021-02-11)

## Language

- Significantly improved the way that dataflow errors and panics are handled in
  the interpreter. They now flow through the program in a far more seamless
  fashion, and panics are supported properly in the IDE without the whole
  program dying ([#1433](https://github.com/enso-org/enso/pull/1433)).
- Significantly improved the syntax and semantics for FFI with JVM languages,
  making it far more of a first-class citizen in Enso
  ([#1443](https://github.com/enso-org/enso/pull/1443)).
- Added support for polyglot JavaScript definitions to Enso
  ([#1451](https://github.com/enso-org/enso/pull/1451)). These allow users to
  write JavaScript inside Enso, and seamlessly call between Enso and JS code.

## Interpreter/Runtime

- Fixed an issue where executing a host value could result in a
  `NullPointerException` due to a missing null check
  ([#1413](https://github.com/enso-org/enso/pull/1413)).
- Fixed an issue where dataflow analysis was incorrectly tracking usages of
  undefined variables. This resulted in problems for the runtime server
  ([#1421](https://github.com/enso-org/enso/pull/1421)).

## Tooling

- Added support for collection of profiling information about the running
  program to the language server protocol
  ([#1407](https://github.com/enso-org/enso/pull/1407)). Initial support is for
  collection of execution-time information.
- Updated the default `main` in a new Enso project to be more IDE friendly
  ([#1419](https://github.com/enso-org/enso/pull/1419)).
- Added support for panic sentinels in the runtime instrument, allowing the
  language server to trace the expressions affected by a panic while still
  executing others ([#1436](https://github.com/enso-org/enso/pull/1436)).
- Added support for checking the Enso version for a particular project in the
  project manager, allowing the IDE to improve compatibility with multiple
  versions ([#1454](https://github.com/enso-org/enso/pull/1454)).

## Libraries

- Updated the way that we use dataflow errors in the standard libraries, making
  our `Base` functionality much more amenable to working in the IDE
  ([#1446](https://github.com/enso-org/enso/pull/1446)).

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-02-10)
  for issues opened before the release date.

# Enso 0.2.1 (2021-01-15)

## Language

- Removed Uniform Function Call Syntax, making the language much more
  predictable and allowing the compiler to provide improved diagnostics for
  common mistakes.

## Interpreter/Runtime

- Return correct qualified names for modules at runtime, ensuring that the
  compiler and interpreter agree.

## Tooling

- Improve the handling of bundled components with the Project Manager, ensuring
  rapid startup and easy integration with the IDE.
- Fixed the reflection configuration for the Project Manager, fixing a bug where
  it was unable to extract archives on Windows.
- The Language Server now uses qualified names in its messages, fixing a class
  of bugs where the IDE and Tooling did not agree on what a given expression
  was.
- Fixed mis-handling of tags in the documentation parsing infrastructure.

## Libraries

- Implemented a stub file for the functionality built into the interpreter. This
  allows us to provide comprehensive documentation about this functionality for
  display in the IDE and for reading by library users.
- Added aggregation functionality to the Table library, allowing users to group
  their data.

## Known Issues

- This is a beta release, so please see the
  [issue tracker](https://github.com/enso-org/enso/issues?q=is%3Aissue+is%3Aopen+created%3A%3C2021-01-15)
  for issues opened before the release date.

# Enso 0.2.0 (2021-01-05)

## Language

The initial version of the Enso language, with most language features
functioning.

- Flexible and concise syntax for the construction of functional programs,
  including pattern matching and lambdas.
- Support for currying, named and defaulted arguments, and operator sections.
- Support for user-defined data-types with fields and dynamically-dispatched
  methods on them.
- Importing and working with Java code in a seamless fashion using polyglot
  imports.
- Functional monadic state and error handling, through the use of data errors
  and panics.
- Opt-in tail-call optimisation.

## Interpreter/Runtime

- The initial version of the interpreter and runtime.

## Type System

- Nothing.

## Tooling

- The initial version of the Enso Launcher and Project Manager, supporting:
  - Installation and management of Enso releases, and the GraalVM runtimes on
    which they depend.
  - Aggregation of logs from the various Enso service components.
  - Basic project management functionality.
  - Initialisation and set-up of a language server for a specific project.
- The initial version of the Enso Language Server, supporting:
  - Dynamic introspection and modification of the running Enso program.
  - Caching of intermediate values in computations, ensuring that only necessary
    parts of the program are recomputed on a change.
  - Intelligent suggestions based on semantic analysis of the code.
  - Attaching visualisation code to values in the running Enso program.

## Libraries

- The initial version of `Base`, the core library, supporting:
  - Functionality for working with core types like `Integer`, `Decimal`, and
    `Text`.
  - Common data structures such as `List`, `Vector`, and `Map`.
  - Support for working with `JSON` data.
  - Support for working with `HTTP` endpoints.
  - Support for interacting with files and processes on the local machine.
  - Support for working with polyglot entities.
  - Support for metaprogramming the Enso language.
- The initial version of the Enso `Table` library for working with tabular data.
- The initial version of the Enso `Test` library, containing testing and
  benchmarking utilities.

## Stabilised Features

- A list of stabilised APIs and/or features.

## Misc

- Nothing.

## Known Issues

- This is a beta release, so please see the issue tracker for issues opened
  before the release date.

## Internal Only

- Nothing
