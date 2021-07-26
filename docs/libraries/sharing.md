---
layout: developer-doc
title: Sharing Libraries
category: libraries
tags: [libraries, editions, sharing]
order: 3
---

# Sharing Libraries

This document explains how users can share Enso libraries.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Sharing Privately](#sharing-privately)
- [Publishing](#publishing)

<!-- /MarkdownTOC -->

## Sharing Privately

To prepare the project for sharing, make sure that it has a proper `namespace`
field set in `package.yaml`. It should be set to something unique, like your
username.

> **NOTE**: The field `namespace` is a temporary workaround and in the near
> future it will be deprecated and handled mostly automatically. For now however
> you need to set it properly.

To share an Enso library, all you need to do is to package the project into an
archive (for example ZIP) and share it (through e-mail, cloud drive services
etc.) with your peers. Now to be able to use the library that was shared with
you, you need to extract it to the directory
`~/enso/libraries/<namespace>/<Project_Name>` (where on Windows `~` should be
interpreted as your user home directory). To make sure that the library is
extracted correctly, make sure that under the path
`~/enso/libraries/<namespace>/<Project_Name>/package.yaml` and that its
`namespace` field has the same value as the name of the `<namespace>` directory.

Now you need to set up your project properly to be able to use this unpublished
library. The simplest way to do that is to set `prefer-local-libraries` in your
project's `package.yaml` to `true`. This will make all libraries from
`~/enso/libraries` take precedence over published libraries set-up in the
edition. Alternatively, if you do not want to override all libraries, but only
some of them, you can add a local library override, by adding a proper entry in
the `libraries` section of the `edition` in your project's `package.yaml`, like
shown below:

```yaml
edition:
   (...)
   libraries:
     - name: <namespace>.<Project_Name>
       repository: local
```

Now, you can use your library by adding a proper import to your project:

```
import <namespace>.<Project_Name>
```

## Publishing

> Soon it will be possible to share the libraries through the Marketplace, but
> it is still a work in progress.
