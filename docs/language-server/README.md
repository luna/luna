---
layout: section-summary
title: Language Server
category: language-server
tags: [language-server, readme]
order: 0
---

# Language Server

The Enso Language Server is responsible for providing language services to the
Enso IDE (and other clients). This mainly involves speaking the Enso protocol
and orchestrating the runtime in response to this. It is responsible for:

- **Introspection Services:** Giving clients the ability to observe information
  about their running code including values, types, profiling information, and
  debugging.
- **Code Execution:** The ability for clients to execute arbitrary Enso code in
  arbitrary scopes. This can be used in conjunction with the above to provide a
  REPL with an integrated debugger.
- **Code Completion:** Sophisticated completion functionality that refines
  suggestions intelligently based on context.
- **Node Management:** Tracking and providing the language server's internal
  node representation of the Enso program.
- **Code Analysis:** Analysis functionality for Enso code (e.g. find usages,
  jump-to-definition, and so on).
- **Refactoring:** Refactoring functionality for Enso code (e.g. rename, move,
  extract, and so on).
- **Type Interactions:** Features for type-driven-development that allow users
  to interact with the types of their programs.

This folder contains all documentation pertaining to the Language Server, which
is broken up as follows:

- [**The Enso Protocol Architecture:**](./protocol-architecture.md) The
  architecture of the Enso protocol.
- [**Streaming File Transfer:**](./streaming-file-transfer.md) Documentation on
  how the streaming file transfer mechanism works.

The protocol messages are broken up into documents as follows:

- [**Common Message Specification:**](./protocol-common.md) The common messages
  and types
- [**Project Manager Message Specification:**](./protocol-project-manager.md)
  The messages and types pertaining to the project manager component.
- [**Language Server Message Specification:**](./protocol-language-server.md)
  The messages and types pertaining to the language server component.
- [**Language Server Http Endpoints Specification**](./language-server-http-endoints.md)
  Specification of the Language Server Http endpoints.
