---
layout: section-summary
title: Enso Runtime
category: runtime
tags: [runtime, readme]
order: 0
---

# Enso Runtime

The Enso runtime refers to both the compiler and the optimising JIT runtime for
Enso. While this might seem like a strange choice, it makes sense internally as
the components are integrated to a level not seen in most languages. It
encompasses the following functionality:

- **Parsing:** Taking Enso code as input and generating an AST that maintains a
  sophisticated set of information about the input.
- **Desugaring:** Reducing the user-facing Enso code into a simplified language
  known as `Core`.
- **Type Inference:** Inferring the types of bindings in the user's code.
- **Type Checking:** Checking that the inferred and provided types for bindings
  match up across the codebase.
- **Optimisation:** Static optimisation processes to improve the performance of
  the user's program.
- **Code Execution:** Actually running the Enso code.
- **Introspection Hooks:** Providing hooks into the running code to allow the
  language server to inspect information about the code as it runs.

This folder contains all of the documentation related to the runtime, which is
broken up as follows:

- [**Caching:**](./caching.md) A description of the runtime's value caching
  mechanism.
- [**Demand Analysis:**](./demand-analysis.md) A specification for the demand
  analysis process in the Enso compiler that assists users with working with
  suspended computations.
- [**Function Calling Flow:**](./function-call-flow.md) A description of the
  involved logic that goes into a calling a function performantly in the Enso
  runtime, while also supporting the flexible function syntax.
- [**Runtime Features:**](./runtime-features.md) A description of (and plan for)
  the features of the Enso runtime.
- [**Unbounded Recursion:**](./unbounded-recursion.md) An exploration of
  techniques for achieving unbounded recursion on the JVM.
- [**Instruments:**](./instruments.md) A description of instrumentation in the
  language runtime.
- [**Execution Server Flow:**](./execution-server-flow.md) An explanation of how
  the execution server orchestrates Enso's execution.
- [**Builtin Base Methods:**](./builtin-base-methods.md) An explanation of the
  DSL we use for defining builtin types and methods.
- [**Searcher:**](./searcher.md) An explanation of how the searcher works.
- [**Instruments:**](./instruments.md) An explanation of how we compile Truffle
  instrumentation.
- [**IR Caching:**](./ir-caching.md) An explanation of how we cache the compiler
  IR to reduce startup times.
