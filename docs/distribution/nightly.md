---
layout: developer-doc
title: Nightly Builds
category: distribution
tags: [distribution, release, nightly]
order: 10
---

# Nightly Builds

This document describes the infrastructure for Enso's automated nightly builds.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Triggering the Build](#triggering-the-build)
- [Nightly Build Versions](#nightly-build-versions)
- [Release Notes](#release-notes)

<!-- /MarkdownTOC -->

## Triggering the Build

The build can be triggered by two possible events:

- automatically, at 4am UTC after each working day (that is, on Tuesday to
  Saturday),
- manually, if a commit with message containing `[release: nightly]` is pushed
  to the `main` branch.

The nightly build is based off of the state of the `main` branch at the moment
when it was triggered.

However, when a nightly build is triggered (by any of the two above conditions),
it will only proceed if there are any changes. That is, if the current commit is
the same as the one used for the previous nightly build, the build will not
proceed because there are no changes.

Thanks to
[GitHub's concurrency settings](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions#concurrency),
only one nightly or regular release job may be running at a given time. If a
build is triggered while another one is in progress, it will be pending until
the first one finishes.

## Nightly Build Versions

The nightly build will modify the version (which is set in `build.sbt`) to
indicate that this is a specific nightly build.

If the version does not include a `SNAPSHOT` suffix, it is added. Then the
current date is appended. Moreover, if the build is not the first nightly build
to be done on a given day, an increasing numeric suffix is appended, to
differentiate between builds from a single day.

For example, if `build.sbt` specifies the version to be `1.2.3` or
`1.2.3-SNAPSHOT`, the nightly build based off of that version, triggered on 1st
of February 2021 will result in version `1.2.3-SNAPSHOT.2021-02-01`. If a
subsequent nightly build is triggered on the same day it will be
`1.2.3-SNAPSHOT.2021-02-01.1` etc.

Only the 3 most recent nightly builds are kept in the repository, any older
builds are removed from the releases page and their corresponding tags are also
removed.

As each release has a default edition associated with it, so do the nightlies.
For the first nightly released on a given date, the edition associated with it
will be named `nightly-2021-02-01`. For subsequent nightly releases on the same
day, a prefix will be added in the same way as with versions. So for example the
second release on that day will be associated with edition
`nightly-2021-02-01.1` etc.

## Release Notes

Each PR should update the first, `Enso Next`, section in `RELEASES.md`. These
changes will be later moved to the specific section for the full release. This
section is also used to fill the release notes for the nightly builds.

Most PRs should update the release notes, so there is a PR check that ensures
the file was modified. However in some situations there is no need to update the
notes. If any commit included as part of a PR includes `[no-changelog]` within
its message, that check is ignored.

The changelog should keep consistent formatting:

- each version should be delimited by a top-level section (`#` in Markdown),
- the first section should always be called `Enso Next`,
- all subsequent sections should be called `Enso <version> (<date>)`.

A heuristic check is ran for PRs, checking that at least the first two sections
satisfy the above requirements - which is necessary for the nightly build
pipeline to be able to correctly infer the release notes.
