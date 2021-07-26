---
layout: developer-doc
title: Repositories
category: libraries
tags: [repositories, libraries, editions]
order: 2
---

# Editions

This document describes the format of repositories that are providing Enso
libraries and Editions.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [General Repository Design](#general-repository-design)
- [Libraries Repository](#libraries-repository)
  - [The Manifest File](#the-manifest-file)
  - [The Sub-Archives](#the-sub-archives)
  - [Example](#example-1)
- [Editions Repository](#editions-repository)
  - [Naming the Editions](#naming-the-editions)
  - [Example Edition Provider Repository](#example-edition-provider-repository)

<!-- /MarkdownTOC -->

## General Repository Design

Library and Edition providers are based on the HTTP(S) protocol. They are
designed in such a way that they can be backed by a simple file storage exposed
over the HTTP protocol, but of course it is also possible to implement the
backend differently as long as it conforms to the specification.

It is recommended that the server should support sending text files with
`Content-Encoding: gzip` to more effectively transmit the larger manifest files,
but this is optional and sending them without compression is also acceptable.
Nonetheless, Enso tools will send `Accept-Encoding: gzip` to indicate that they
support compressed transmission.

## Libraries Repository

The library repository should contain separate 'directories' for each prefix and
inside of them each library has its own directory named after the library name.

Inside that directory are the following files:

- `manifest.yaml` - the helper file that tells the tool what it should download,
  it is explained in more detail below;
- `package.yaml` -
  [the package file](../distribution/packaging.md#the-packageyaml-file) of the
  library;
- `meta` - an optional metadata directory, that may be used by the marketplace;
- `LICENSE.md` - a license associated with the library; in our official
  repository the license is required, but internal company repositories may skip
  this, however if the file is not present a warning will be emitted during
  installation;
- `*.tgz` packages containing sources and other data files of the library, split
  into components as explained below.

The directory structure is as below:

```
root
└── Prefix                      # The author's username.
    └── Library_Name            # The name of the library.
        └── 1.2.3               # Version of a particular library package.
            ├── meta            # (Optional) Library metadata for display in the marketplace.
            │   ├── preview.png
            │   └── icon.png
            ├── main.tgz        # The compressed package containing sources of the library.
            ├── tests.tgz       # A package containing the test sources.
            ├── LICENSE.md
            ├── package.yaml
            └── manifest.yaml
```

### The Manifest File

The manifest file is a YAML file with the following fields:

- `archives` - a list of archive names that are available for the given library;
  at least one archive must be present (as otherwise the package would be
  completely empty);
- `dependencies` - a list of dependencies, as described below;
- `description` - an optional description of the library that is displayed in
  the `info` and `search` command results;
- `tag-line` - an optional tagline that will be displayed in the marketplace
  interface.

As the protocol does not define a common way of listing directories, the primary
purpose of the manifest file is to list the available archive packages, so that
the downloader can know what archives it should try downloading.

Additionally, the manifest may contain a list of (direct) dependencies the
library relies on. This list is in a way redundant, because the dependencies may
be inferred from libraries' imports, but its presence is desirable, because the
downloader would need to download the whole sources package (which may be large)
before being able to deduce the dependencies, where if they are defined in the
manifest file, the manifest files of all transitive dependencies may be
downloaded up-front, allowing to give a better estimate of how much must be
downloaded before the library can be actually loaded, improving the user's
experience.

It is not an error for an imported dependency to not be included in the manifest
(in fact the manifest may list no dependencies at all) - in such a case the
dependency will be downloaded when the library is first being loaded. However,
it is strongly recommended that these dependencies shall be included, as it
greatly improves the user's experience.

The dependencies consist only of library names, with no version numbers, as the
particular version of each dependency that should be used will be ruled by the
edition that is used in a given project.

> The upload tool will automatically parse the imports and generate the manifest
> containing the dependencies.

#### Example

An example `manifest.yaml` file may have the following structure:

```yaml
archives:
  - main.tgz
  - tests.tgz
dependencies:
  - Standard.Base
  - Foo.Bar
```

### The Sub-Archives

The published library consists of sub-archives that allow to selectively
download only parts of the library.

Each downloaded archive will be extracted to the libraries' root directory in
such a way that common directories from multiple archives are merged on
extraction. However, different packages should not contain overlapping files as
there would be no way which of the files should be kept when the packages are
extracted.

> It is not an error if multiple downloaded packages contain conflicting files,
> but there are no guarantees as to which of the conflicting files is kept.

The package called `tests` is treated specially - it will not be downloaded by
default, as tests (which may contain heavy data files) are not necessary to use
the library.

> In the future, we will introduce platform specific sub-archives. The initial
> idea is that if an archive name has format `<prefix>-<os>-<arch>.tgz` where
> `os` is one of `windows`, `macos`, `linux` and `arch` is `amd64` (or in the
> future other values may be available here), the package is only downloaded if
> the current machine is running the same OS and architecture as indicated by
> its name. This is however a draft and the particular logic may be modified
> before it is implemented. Since the current behaviour is to download all
> packages (except for `test`), adding this feature will be backwards
> compatible, because the older versions will just download packages for every
> system (which will be unnecessary, but not incorrect).

All other packages are always downloaded by default. This may however change in
the future with additional reserved names with special behaviour being added.

There is no special name for a default package that should always be downloaded,
the only requirement is that the library should consist of at least one package
that is downloaded on every supported operating system (as otherwise it would be
empty). A safe name to choose is `main.tgz` as this name is guaranteed to never
become reserved, and so it will always be downloaded.

The archives should be `tar` archives compressed with the `gzip` algorithm and
should always have the `.tgz` extension (which is a shorthand for `.tar.gz`).

> Other formats may be added in the future if necessary, but current versions of
> the tool will ignore such files.

### Example

For example a library may have the following manifest:

```
archives:
- main.tgz
- tests.tgz
```

With the following directory structure (nodes under archives represent what the
archive contains):

```
root/Foo/Bar/1.2.3
├── main.tgz
│   ├── src
│   │   ├── Main.enso
│   │   └── Foo.enso
│   ├── polyglot
│   │   └── java
│   │       └── native-helper.jar
│   ├── THIRD-PARTY
│   │   ├── native-component-license.txt
│   │   └── native-component-distribution-notice.txt
│   └── data
│       └── required-constants.csv
├── tests.tgz
│   ├── tests
│   │   └── MainSpec.enso
│   └── data
│       └── tests
│           └── big-test-data.csv
├── package.yaml
├── LICENSE.md
└── manifest.yaml
```

Then if both `maing.tgz` and `tests.tgz` packages are downloaded (normally we
don't download the tests, but there may be special settings that do download
them), it will result in the following merged directory structure:

```
<downloaded-libraries-cache>/Foo/Bar/1.2.3
├── src
│   ├── Main.enso
│   └── Foo.enso
├── polyglot
│   └── java
│       └── native-helper.jar
├── THIRD-PARTY
│   ├── native-component-license.txt
│   └── native-component-distribution-notice.txt
├── tests
│   └── MainSpec.enso
├── data
│   ├── required-constants.csv
│   └── tests
│       └── big-test-data.csv
├── package.yaml
└── LICENSE.md
```

## Editions Repository

The Editions repository has a very simple structure.

Firstly, it must contain a `manifest.yaml` file at its root. The manifest
contains a single field `editions` which is a list of strings specifying the
editions that this provider provides.

For each entry in the manifest, there should be a file `<edition-name>.yaml` at
the root which corresponds to that entry.

### Naming the Editions

The edition files are supposed to be immutable, so once published, an edition
should not be updated - instead a new edition should be created if changes are
necessary. In particular, once an edition with a particular name has been
downloaded, it is cached and will never be downloaded again (unless the user
manually deletes its file in the cache).

The edition names should be kept unique, because if multiple repositories
(listed in [the global configuration](./editions.md#updating-the-editions))
provide editions with the same name, the edition file from the first repository
on that list providing it will take precedence when the editions are being
updated, but once the editions are cached, modifying the list order will not
cause a re-download.

Each organization should try to make sure that their users will not encounter
edition names conflicts when using their custom edition repository. In
particular, it is recommended that custom published editions are prefixed with
organization name.

Official editions will use the following sets of names:

- the year and month format `<year>.<month>`, for example `2021.4`;
- `nightly-<year>-<month>-<day>` for nightly releases, for example
  `nightly-2021-04-25`.

### Example Edition Provider Repository

For example for a manifest file with the following contents, we will have a
directory structure as shown below.

```yaml
editions:
  - "2021.1"
  - "foo"
  - "bar"
```

```
root
├── manifest.yaml
├── 2021.1.yaml
├── foo.yaml
└── bar.yaml
```

## The Simple Library Server

We provide a simple webserver for hosting custom library and edition
repositories.

Currently it relies on Node.js, but that may change with future updates.

See
[`tools/simple-library-server/README.md`](../../tools/simple-library-server/README.md)
for more details.
