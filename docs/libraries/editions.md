---
layout: developer-doc
title: Editions
category: libraries
tags: [libraries, editions]
order: 1
---

# Editions

This document describes the concept of Editions.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [What Is An Edition](#what-is-an-edition)
- [The Edition File](#the-edition-file)
  - [Repositories](#repositories)
  - [Libraries](#libraries)
  - [Extending the Editions](#extending-the-editions)
  - [An Example Configuration](#an-example-configuration)
- [Edition Resolution](#edition-resolution)
  - [Updating the Editions](#updating-the-editions)
- [Library Resolution](#library-resolution)

<!-- /MarkdownTOC -->

## What Is An Edition

An Edition, is in principle, a list of library versions that should be
compatible with each other. An Edition specifies the engine version and a set of
library versions that can be used together.

If a library included in an Edition depends on another library, that other
library must also be included in that Edition and the version at which it is
included must be compatible with the library that depends on it. Each Edition
may only include a single version of each library.

Thus, when a library is to be installed, its version is uniquely determined by
the selected Edition. A curated Edition file will guarantee that all libraries
are compatible which simplifies version resolution - the exact version that is
specified in the Edition is always used.

## The Edition File

The Edition file is a YAML file that can contain the following fields:

- `engine-version` which should be a semantic versioning string specifying the
  engine version that should be associated with that edition,
- `repositories` which defines the repositories which are sources of library
  packages, its format is [described below](#repositories),
- `extends` which can contain a name of another Edition that this Edition
  extends,
- `libraries` which defines the libraries that this Edition should include, its
  format is [described below](#libraries).

Every field is optional, but for an Edition file to be valid it must specify at
least the engine version to be used (either by specifying it directly or
extending another edition that specifies it).

### Repositories

The `repositories` field is a list of repository objects.

Each object must have:

- a `name` field which specifies the name under which this repository will be
  referred to in the rest of the file,
- a `url` field which specifies the URL of the root of that repository.

The `name` can be any string which only needs to be consistent with the names
used in the package definitions. The only reserved name is `local`, which is a
special repository name, as [explained below](#library-resolution).

### Libraries

The `libraries` field defines the set of libraries included in the edition.

Each library is represented by an object that must have:

- a `name` field which is the fully qualified name of the library (consisting of
  its prefix and the name itself),
- a `repository` field which specifies which repository this package should be
  downloaded from. The `repository` field should refer to the `name` of one of
  the repositories defined in the edition or to `local`,
- a `version` field which specifies which exact package version should be used
  when the library is imported; it is normally required, but if the `repository`
  is set to `local`, the version must not be specified as the version will only
  depend on what is available in the local repository,
- an optional `hash` that can be included to verify the integrity of the
  package.

The `hash` field is currently not implemented.

### Extending the Editions

An edition may extend another one by using the `extends` property specifying the
name of the edition that is to be extended. Henceforth we will call the edition
that is being extended 'the parent edition' and the other one 'the local
edition'.

The current edition inherits all configuration of the parent edition, but it can
also override specific settings.

If the `engine-version` is specified in the current edition, it overrides the
engine version that was implied from the parent edition.

If the current edition specifies its libraries, they are added to the set of
available libraries defined by the parent edition. If the current edition
defines a library that has the same fully qualified name as a library that was
already defined in the parent edition, the definition from the current edition
takes precedence. This is the most important mechanism of extending editions
that allows to override library settings.

The libraries defined in the current edition can refer to the repositories
defined both in the current edition and in the parent. However, if the current
edition defines a repository with the same name as some repository defined in
the parent edition, the definition from the current edition takes precedence for
the package definitions of the current definition, **but** the package
definitions in the parent edition are not affected (they still refer to the
definition from the their own edition). So you can shadow a repository name, but
you cannot override it for libraries from the parent edition - instead libraries
whose repository should be changed must all be overridden in the current
edition.

Extending editions can be arbitrarily nested. That is, an edition can extend
another edition that extends another one etc. The only limitation is that
obviously there can be no cycles in the chain of extensions. Multiple extensions
are resolved as follows: first the parent edition is completely resolved (which
may recursively need to first resolve its parents etc.) and only then the
current edition applies its overrides.

### An Example Configuration

```yaml
extends: 2021.4
engine-version: 1.2.3
repositories:
  - name: secondary
    url: https://example.com/
libraries:
  - name: Foo.Bar
    version: 1.0.0
    repository: secondary
```

The edition file shown above extends a base edition file called `2021.4`. It
overrides the engine version set in the parent edition to `1.2.3`. Moreover it
adds a library `Foo.Bar` from the `secondary` repository, or if `2021.4`
included the library `Foo.Bar`, its definition is overridden with the one
provided here.

## Edition Resolution

The edition configuration for a project is loaded from the `edition` section in
its `package.yaml` configuration. This 'per-project' edition has no assigned
names, but it can refer to other editions by their names (when extending them).
These editions are resolved using the logic below:

1. Each `<edition-name>` corresponds to a file `<edition-name>.yaml`.
2. First, the custom edition search paths are scanned for a matching edition
   file. These paths can be defined by the `ENSO_EDITION_PATH` environment
   variable. If it is not defined, it defaults to `<ENSO_HOME>/editions`.
3. If none is found above, the cached/bundled edition search paths are checked.
   These consist of the directory `$ENSO_DATA_DIRECTORY/editions`, `editions`
   directories in installed engines and the `editions` directory in the
   currently running engine.

By default, downloaded editions are downloaded to
`$ENSO_DATA_DIRECTORY/editions`, but also editions bundled with any available
engines can be loaded.

See [The Enso Distribution](../distribution/distribution.md) for definitions of
the directories.

### Updating the Editions

The global user configuration file should contain a list of URLs specifying
edition providers that should be used. By default (if the field is missing), it
will default to our official edition provider, but users may add other providers
or remove the official one.

When `enso update-editions` is called or when requested by the IDE, these
providers are queried and any new edition files are downloaded to the
`$ENSO_DATA_DIRECTORY/editions` directory. Editions are assumed to be immutable,
so edition files that already exist on disk are not redownloaded.

## Library Resolution

Below are listed the steps that are taken when resolving an import of library
`Foo.Bar`:

1. If and only if the project has `prefer-local-libraries` set to `true` and if
   any directory on the library path contains `Foo/Bar`, that local instance is
   chosen as the library that should be used, regardless of the version that is
   there;
2. Otherwise, the list of libraries defined directly in the `edition` section of
   `package.yaml` of the current project is checked, and if the library is
   defined there, it is selected.
3. Otherwise, any parent editions are consulted; if they too do not contain the
   library that we are searching for, an error is reported.
4. Once we know the library version to be used:
   1. If the repository associated with the library is `local`, the library path
      is searched for the first directory to contain `Foo/Bar` and this path is
      loaded. If the library is not present on the library path, an error is
      reported.
   2. Otherwise, the edition must have defined an exact `<version>` of the
      library that is supposed to be used.
   3. If the library is already downloaded in the local repository cache, that
      is the directory `$ENSO_DATA_DIRECTORY/lib/Foo/Bar/<version>` exists, that
      package is loaded.
   4. Otherwise, the library is missing and must be downloaded from its
      associated repository (and placed in the cache as above).

By default, the library path is `<ENSO_HOME>/libraries/` but it can be
overridden by setting the `ENSO_LIBRARY_PATH` environment variable. It may
include a list of directories (separated by the system specific path separator);
the first directory on the list has the highest precedence.

In particular, if `prefer-local-libraries` is `false`, and the edition does not
define a library at all, when trying to resolve such a library, it is reported
as not found even if a local version of it exists. That is because
auto-discovery of local libraries is only done with `prefer-local-libraries` set
to `true`. In all other cases, the `local` repository overrides should be set
explicitly.
