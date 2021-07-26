---
layout: developer-doc
title: Release Policy
category: distribution
tags: [distribution, release, release-policy, policy]
order: 3
---

# Release Policy

As an open-source project and programming language, it is incredibly important
that we have a well-defined release policy. This document defines said policy.

> **Once a release has been made it is immutable. The release should only ever
> be edited to mark it as broken. Nothing else should ever be changed.**
>
> **No two release workflows can be running at once, to avoid race conditions
> since releases
> [must update files in S3](fallback-launcher-release-infrastructure.md#updating-the-release-list).
> Make sure that tags which trigger release builds are pushed sequentially, only
> pushing the next one after the previous build has finished.**

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Versioning](#versioning)
  - [Launcher Versioning](#launcher-versioning)
- [Release Branches](#release-branches)
- [Release Workflow](#release-workflow)
  - [Breaking Release Workflow](#breaking-release-workflow)
  - [Tag Naming](#tag-naming)
  - [Manifest Files](#manifest-files)
  - [Breaking Changes to Launcher Upgrade](#breaking-changes-to-launcher-upgrade)
  - [GitHub Releases](#github-releases)
  - [Release Notes](#release-notes)
- [Version Support](#version-support)
- [Working on the Current Release](#working-on-the-current-release)
- [Backporting Fixes](#backporting-fixes)

<!-- /MarkdownTOC -->

## Versioning

Releases of Enso are versioned using [semantic versioning](https://semver.org).
Where `a.b.c-tag` is the version string, `a` is the major version, `b`, is the
minor version, `c` is the patch version, and `tag` is additional metadata, the
following hold:

- Breaking changes to language behaviour or the public API will result in a
  major version increase.
- Addition of functionality in a backwards-compatible manner will result in a
  minor version increase.
- Backwards-compatible bug fixes will result in a patch version increase.
- The tag will indicate pre-release or beta versions, and will increase when any
  pre-release change is made. These are not intended to be stable.

### Launcher Versioning

The launcher is released alongside Enso releases, so the launcher version is
tied to the Enso version that it is released with.

## Release Branches

A release branch in the Enso repository is a branch prefixed with `release/`.
Release branches obey the following rules:

- One release branch exists per major version, and is named `release/n.x`, where
  `n` is the major version, and the rest is literal.
- A release branch must contain _tags_ corresponding to released versions of
  Enso. Once a release has been made, no further changes may be made to that
  release.
- A tagged release must contain a `RELEASES.md` file that describes the changes
  contained in that release.

It should be noted that general development still takes place on the `main`
branch of the repository.

## Release Workflow

Cutting a release for Enso proceeds as follows:

1.  If no release branch exists for the current major version, one should be
    created.
2.  Create a branch called `wip/<initials/release-bump`. On this branch, ensure
    that the release notes are up to date in `RELEASES.md` (follow the existing
    format), and that the new version number and edition name have been set in
    `build.sbt`. This version and edition name should _not_ contain `SNAPSHOT`.
3.  Open a PR for this branch into `main`.
4.  Once the changes have been reviewed, merge the PR into main (getting commit
    hash `xxxxxxx`). The message should be `Prepare for the $version release`.
    Just before merging, remember to notify the team on Discord to suppress any
    other merges to the `main` branch until the next step (bumping versions) is
    completed.
5.  Immediately push a commit to `main` that updates the version and edition in
    `build.sbt` to the new snapshot version. If unclear, bump the patch version
    by one and append `-SNAPSHOT` (e.g. `0.2.10` becomes `0.2.11-SNAPSHOT`). The
    edition name should have the number after the dot increased and `-SNAPSHOT`
    appended, so that `2021.3` becomes `2021.4-SNAPSHOT`. The only exception is
    when making the first release in a new year, where the first number should
    be bumped to the next year and the second number should be set to 1, for
    example `2022.1`. The message should be `Bump the snapshot version`.
6.  Find the commit hash of the last "Bump the snapshot version" commit. Let's
    say this is `yyyyyyy`.
7.  Run a `rebase --onto` the release branch from the `yyyyyyy` commit to the
    `xxxxxxx` commit. For example:

```
git rebase --onto origin/release/0.x yyyyyyy~1 xxxxxxx
```

8.  This will put you into a "detached HEAD" state at commit `zzzzzzz`, so you
    need to make a new branch: `git branch release-update zzzzzzz`
9.  This new branch is a fast-forward merge away from the release branch. Check
    out the release branch and then fast-forward merge `release-update` into it.
    For example:

```
git checkout release/0.x
git merge --ff-only release-update
```

10. As long as the fast-forward proceeds cleanly, you can push the updated
    release branch to the origin.
11. Create a tag for the commit at the HEAD of the release branch. It should be
    named as above. As the tag is signed, it must contain a message. The message
    should be `Enso <version>`. For example:

```
git tag --sign enso-0.2.11
```

12. Push the tag to the remote. This will start the release build.
13. CI will create a draft release for this tag, as well as build and upload the
    appropriate artefacts. **Do not** create a release for this tag manually.
14. The release notes for the version being released should be copied from the
    new section in `RELEASES.md` into the GitHub release description with the
    line breaks removed.
15. The title of the release should be `Enso Engine <version>` (e.g.
    `Enso Engine 0.2.11`).
16. The release must be verified by at least one member of the engine team and
    the QA team.
17. Once verification has been performed, the release can be published. It
    should _not_ be a pre-releases as we reserve these for nightly builds.

### Breaking Release Workflow

If, however, the engine needs to release but the `HEAD` of `main` is not in a
compatible state with the IDE, the process has to differ a little bit. Please
note that the instructions here are more vague than the above, as exactly what
is required may vary based on the situation.

Consider a scenario where there are four new commits since the last release:
`wwwwwww`, `xxxxxxx`, `yyyyyyy`, `zzzzzzz`. The commit `yyyyyyy` contains
breaking changes that are _not yet integrated with the IDE_. Releasing a package
containing that commit (and those that depend on it) would break the IDE, but we
nevertheless want to release as much as possible:

1.  If no release branch exists for the current major version, one should be
    created.
2.  Rebase the commits that are wanted onto the release branch:

```
git rebase --onto origin/release/0.x wwwwwww~1 xxxxxxx
```

3.  This will put you into a "detached HEAD" state at commit `aaaaaaa`, so you
    need to make a new branch: `git branch release-update aaaaaaa`, whose `HEAD`
    commit is the same as `xxxxxxx`.
4.  This new branch is a fast-forward merge away from the release branch. Check
    out the release branch and then fast-forward merge `release-update` into it.
    For example:

```
git checkout release/0.x
git merge --ff-only release-update
```

5.  On the release branch, ensure that the release notes are up to date in
    `RELEASES.md` (follow the existing format), and that the new version number
    and edition name have been set in `build.sbt`. This version and edition name
    should _not_ contain `SNAPSHOT`.
6.  Once this is done, create a tag for the commit at the HEAD of the release
    branch. It should be named as above. The tag message should be
    `Enso <version>`. For example:

```
git tag --sign enso-0.2.11
```

7.  Push the tag to the remote. This will start the release build.
8.  CI will create a draft release for this tag, as well as build and upload the
    appropriate artefacts. **Do not** create a release for this tag manually.
9.  The release notes for the version being released should be copied from the
    new section in `RELEASES.md` into the GitHub release description with the
    line breaks removed.
10. The title of the release should be `Enso Engine <version>` (e.g.
    `Enso Engine 0.2.11`).
11. Check out the main branch, and then synchronise the changes to `RELEASES.md`
    on the release branch with the changes on `main`.
12. In the same commit, Update the build version number in `build.sbt` to the
    new snapshot version. If unclear, bump the patch version by one and append
    `-SNAPSHOT` (e.g. `0.2.10` becomes `0.2.11-SNAPSHOT`). The edition name
    should have the number after the dot increased and `-SNAPSHOT` appended, so
    that `2021.3` becomes `2021.4-SNAPSHOT`. The message should be
    `Bump the snapshot version`.
13. Push this commit into `origin/main`, or merge via PR if unable to directly
    push.

It is recommended that you instigate a freeze on merges to `main` whilst
performing this process.

### Tag Naming

Tags for releases are named as follows `enso-version`, where `version` is the
semver string (see [versioning](#versioning)) representing the version being
released.

### Manifest Files

Manifest files are used to describe metadata about various releases for use by
the Enso tooling.

#### Engine Manifest

Each GitHub release contains an asset named `manifest.yaml` which is a YAML file
containing metadata regarding the release. The manifest is also included in the
root of an Enso version package. It has at least the following fields:

- `minimum-launcher-version` - specifies the minimum version of the launcher
  that should be used with this release of Enso,
- `minimum-project-manager-version` - specifies the minimum version of the
  project manager that should be used with this release of Enso; currently it is
  the same as the launcher version but this may change in the future,
- `graal-vm-version` - specifies the exact version of GraalVM that should be
  used with this release of Enso,
- `graal-java-version` - as GraalVM versions may have different variants for
  different Java versions, this specifies which variant to use.

The minimum launcher and project manager versions are kept as separate fields,
because at some point the same runtime version management logic may be
associated with different versions of these components.

It can also contain the following additional fields:

- `jvm-options` - specifies a list of options that should be passed to the JVM
  running the engine. These options can be used to fine-tune version specific
  optimization settings etc. Each option must have a key called `value` which
  specifies what option should be passed. That value can include a variable
  `$enginePackagePath` which is substituted with the absolute path to the root
  of the engine package that is being launched. Optionally, the option may
  define `os` which will restrict this option only to the provided operating
  system. Possible `os` values are `linux`, `macos` and `windows`.
- `broken` - can be set to `true` to mark this release as broken. This field is
  never set in a release. Instead, when the launcher is installing a release
  marked as broken using the `broken` file, it adds this property to the
  manifest to preserve that information.

For example:

```yaml
minimum-launcher-version: 0.0.1
minimum-project-manager-version: 0.0.1
jvm-options:
  - value: "-Dpolyglot.engine.IterativePartialEscape=true"
  - value: "-Dtruffle.class.path.append=$enginePackagePath\\component\\runtime.jar"
    os: "windows"
  - value: "-Dtruffle.class.path.append=$enginePackagePath/component/runtime.jar"
    os: "linux"
  - value: "-Dtruffle.class.path.append=$enginePackagePath/component/runtime.jar"
    os: "macos"
graal-vm-version: 20.2.0
graal-java-version: 11
```

The `minimum-launcher-version` should be updated whenever a new version of Enso
introduces changes that require a more recent launcher version. This value is
stored in
[`distribution/manifest.template.yaml`](../../distribution/manifest.template.yaml)
and other values are added to this template at build time.

#### Launcher Manifest

Additionally, each release should contain an asset named
`launcher-manifest.yaml` which contains launcher-specific release metadata.

It contains the following fields:

- `minimum-version-for-upgrade` - specifies the minimum version of the launcher
  that is allowed to upgrade to this launcher version. If a launcher is older
  than the version specified here it must perform the upgrade in steps, first
  upgrading to an older version newer than `minimum-version-for-upgrade` and
  only then, using that version, to the target version. This logic ensures that
  if a newer launcher version required custom upgrade logic not present in older
  versions, the upgrade can still be performed by first upgrading to a newer
  version that does not require the new logic but knows about it and continuing
  the upgrade with that knowledge.
- `files-to-copy` - a list of files that should be copied into the
  distribution's data root. This may include the `README` and similar files, so
  that after the upgrade these additional files are also up-to-date. These files
  are treated as non-essential, i.e. an error when copying them will not cancel
  the upgrade (but it should be reported).
- `directories-to-copy` - a list of directories that should be copied into the
  distribution's data root. Acts similarly to `files-to-copy`.

A template manifest file, located in
[`distribution/launcher-manifest.yaml`](../../distribution/launcher-manifest.yaml),
is automatically copied to the release. If any new files or directories are
added or a breaking change to the upgrade mechanism is being made, this manifest
template must be updated accordingly.

### Breaking Changes to Launcher Upgrade

If at any point the launcher's upgrade mechanism needs an update, i.e.
additional logic must be added that was not present before, special action is
required.

First, the additional logic has to be implemented and a new launcher version
should be released which includes this additional logic, but does not require it
yet. Then, another version can be released that can depend on this new logic and
its `minimum-version-for-upgrade` has to be bumped to that previous version
which already includes new logic but does not depend on it.

This way, old launcher versions can first upgrade to a version that contains the
new logic (as it does not depend on it yet, the upgrade is possible) and using
that new version, upgrade to the target version that depends on that logic.

### GitHub Releases

A release is considered _official_ once it has been made into a release on
[GitHub](https://github.com/enso-org/enso/releases). Once official, a release
may not be changed in any way, except to mark it as broken.

#### Release Assets Structure

Each release contains a build of the Enso engine and native launcher binaries
for each supported platform. Moreover, for convenience, it should include
bundles containing native launcher binaries and the latest engine build for each
platform. So each release should contain the following assets:

- `enso-bundle-<version>-linux-amd64.tar.gz`
- `enso-bundle-<version>-macos-amd64.tar.gz`
- `enso-bundle-<version>-windows-amd64.zip`
- `enso-engine-<version>-linux-amd64.tar.gz`
- `enso-engine-<version>-macos-amd64.tar.gz`
- `enso-engine-<version>-windows-amd64.zip`
- `enso-launcher-<version>-linux-amd64.tar.gz`
- `enso-launcher-<version>-macos-amd64.tar.gz`
- `enso-launcher-<version>-windows-amd64.zip`
- `manifest.yaml`

#### Marking a Release as Broken

We intend to _never_ delete a release from GitHub, as users may have projects
that depend on specific versions of Enso. Instead, we provide a mechanism for
marking releases as broken that works as follows:

- An empty file named `broken` is uploaded to the release.
- The release description is edited to visibly mark the release as broken.

A broken release is one that _must not_ be downloaded by the launcher unless a
project specifies _an exact version match_, and it _must not_ be used in new
projects by the launcher unless _explicitly_ specified by the user as an exact
version match.

When the release is marked as broken at GitHub, a GitHub Actions
[Workflow](fallback-launcher-release-infrastructure.md#marking-the-release-as-broken)
is triggered that also updates the release in the fallback mechanism. Given its
current implementation is prone to race conditions when updating releases, the
`broken` file should be added to releases one by one, making sure that only one
update workflow is running at the same time and that no release workflows are
running in parallel with it.

In an unusual situation in which you want to upload a release that is marked as
broken from the start, you should first publish it in a non-broken state and
only mark it as broken after publishing. That is because the GitHub Workflow
that will persist the broken mark to S3 is not triggered for release drafts.

> **When marking the release as broken, you should make sure that the workflow
> persisting the broken mark to Se has succeeded and re-run it if necessary.**

### Release Notes

Release notes should contain a summary of the changes made between the last
release and the current release. They should follow the template given below,
and are contained in the `RELEASES.md` file in the repository root.

```md
# Enso x.y.z (YYYY-MM-DD)

## Language

- A list of language-level changes.

## Interpreter/Runtime

- A list of changes to the Enso interpreter.

## Type System

- A list of type-system changes.

## Tooling

- A list of changes to the Enso language tooling.

## Libraries

- A list of changes to the Enso core libraries.

## Stabilised Features

- A list of stabilised APIs and/or features.

## Misc

- A list of miscellaneous changes.

## Internal Only

- A list of changes that do not have user-facing impact, but represent
  significant improvements to the internals of Enso and related tools.
```

If there are no changes for a section, the section may be removed.

The releases file is an ongoing record of changes, and may diverge between
`main` and the various release branches.

## Version Support

We aim to support a given major version for some period of time after the
release of the next major version. For a detailed breakdown of the major
versions that are currently supported, please see the [security](./security.md)
document.

## Working on the Current Release

When working on the current release, development should take place against the
`main` branch. When it is time to cut a release, the new commits on the main
branch are cherry-picked onto the current release branch. From there, the
release proceeds as described in [release workflow](#release-workflow) above.

## Backporting Fixes

Supporting a major version for some time after the release of the next major
version will sometimes require backporting a fix to the previous major version
from the current version or from `main`.

Backporting should only be used for applying _fixes_, not the addition of new
features.

The process for performing such a backport is as follows:

1.  Create a new branch called `backport/version/fix-name`, where `version`
    matches the version string of the corresponding release branch. This branch
    should branch off the corresponding release branch.
2.  Back-port the fix to the newly created `backport` branch. This can be done
    by:
    - Cherry-picking the commit and performing fixups (preferred).
    - Re-implementing the fix manually (if cherry-picking will not work due to
      progression of the codebase).
3.  Submit your `backport/version/fix-name` branch for review as a pull-request
    into the `release/version` branch.
4.  Once the PR has passed CI and been approved by the appropriate reviewers, it
    can be merged into the release branch.
