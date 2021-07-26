---
layout: developer-doc
title: IR Caching in the Enso Compiler
category: runtime
tags: [runtime, caching]
order: 10
---

# IR Caching in the Enso Compiler

One of the largest pain points for users of Enso at the moment is the fact that
it has to precompile the entire standard library on every project load. This is,
in essence, due to the fact that the current parser is abysmally slow, and
incredibly demanding. The obvious solution to improve this is to take the parser
out of the equation in its entirety, by serialising the parser's output.

To that end, we want to serialise the Enso IR to a format that can later be read
back in, bypassing the parser entirely. Furthermore, we can move the boundary at
which this serialisation takes place to the end of the compiler pipeline,
thereby bypassing doing most of the compilation work, and further improving
startup performance.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Serialising the IR](#serialising-the-ir)
  - [Breaking Links](#breaking-links)
- [Storing the IR](#storing-the-ir)
  - [Metadata Format](#metadata-format)
  - [Portability Guarantees](#portability-guarantees)
- [Loading the IR](#loading-the-ir)
  - [Integrity Checking](#integrity-checking)
  - [Error Handling](#error-handling)
- [Testing the Serialisation](#testing-the-serialisation)

<!-- /MarkdownTOC -->

## Serialising the IR

As the serialised IR doesn't need to be read by anything other than Enso, we
need not use a representation that is portable between platforms. As a result,
we have picked the `Serializable` infrastructure that is _already present_ on
the JVM. It has the following benefits:

- It is able to serialise arbitrary object graphs while maintaining object
  identity and tracking references. This cannot be disabled for `Serializable`,
  but that is fine as we want it.
- It is built into the JVM and is hence guaranteed to be portable between
  instances of the same JVM.
- It copes fine with highly-nested scala types, like our IR.

In order to maximise the benefits of this process, we want to serialise the IR
as _late_ in the compiler pipeline as possible. This means serialising it just
before the code generation step that generates Truffle nodes (before the
`RuntimeStubsGenerator` and `IrToTruffle` run).

This serialisation should take place in an _offloaded thread_ so that it doesn't
block the compiler from continuing.

### Breaking Links

Doing this naïvely, however, means that we can inadvertently end up serialising
the entire module graph. This is due to the `BindingsMap`, which contains a
reference to the associated `runtime.Module`, from which there is a reference to
the `ModuleScope`. The `ModuleScope` may then reference other `runtime.Module`s
which all contain `IR.Module`s. Therefore, done in a silly fashion, we end up
serialising the entire reachable module graph. This is not what we want.

While the ideal way of solving this problem would be to customise the
serialisation and deserialisation process for the `BindingsMap`, the JVM's
`Serializable` does not provide the ability to customise it enough to solve this
problem. Instead, we solve it using a preprocessing step:

- We can modify `BindingsMap` and its child types to be able to contain an
  unlinked module pointer
  `case class ModulePointer(qualifiedName: List[String])` in place of a
  `Module`.
- As the `MetadataStorage` type that holds the `BindingsMap` is mutable it can
  be updated in place without having to reassemble the entire IR graph.
- Hence, we can traverse all the nodes in the `ir.preorder` that have metadata
  consisting of either the `BindingsMap` or `ResolvedName` types (provided by
  the following passes: `BindingAnalysis`, `MethodDefinitions`,
  `UppercaseNames`, `VectorLiterals`, `Patterns`), and perform a replacement.

Having done this, we have broken any links that the IR may hold between modules,
and can serialise each module individually.

This serialisation must take place _after_ codegen has happened as it modifies
the IR in place. The compiler can handle giving it to the offloaded
serialisation thread. It _may_ be necessary to `duplicate` the IR before handing
it to this thread, but this should be checked during development.

## Storing the IR

The serialized IR needs to be stored in a location that is tied to the library
that it serializes. Despite this, we _also_ want to be able to ship cached IR
with libraries. This leads to a two pronged solution where we check two
locations for the cache.

1. **With the Library:** As libraries can have a hidden `.enso` directory, we
   can use a path within that for caching. This should be
   `$package/.enso/cache/ir/enso-$version/`, and can be accessed by extending
   the `pkg` library to be aware of the cache directories.
2. **Globally:** As some library locations may not be writeable, we need to have
   a global out-of-line cache that is used if the first one is not writeable.
   This is located under `$ENSO_DATA` (whose location can be obtained from the
   `RuntimeDistributionManager`), and is located under the path
   `$ENSO_DATA/cache/ir/$hash/enso-$version/`, where `$hash` is the `SHA3-224`
   hash of the tuple `(namespace, library_name, version)`, where
   `version = SemVer | "local"`. This hash is computed by concatenating the
   string representations of these fields.

In each location, the IR is stored with the following assumptions:

- The IR file is located in a directory modelled after its module path, followed
  by a file named after the module itself with the extension `.ir` (e.g. the IR
  for `Standard.Base.Data.Vector` is stored in `Standard/Base/Data/Vector.ir`).
- The [metadata](#metadata-format) file is located in a directory modelled after
  its module path, followed by a file named after the module itself with the
  extension `.meta` (e.g. the metadata for `Standard.Base.Data.Vector.enso` is
  stored in `Standard/Base/Data/Vector.meta`). This is right next to the
  corresponding `.ir` file.

Storage of the IR only takes place iff the intended location for that IR is
_empty_.

### Metadata Format

The metadata is used for integrity checking of the cached IR to prevent loading
corrupted or out of date data from the cache. Due to the fact that engines can
only load IR created by their versions, and cached IR is located in a directory
named after the engine version, this format need not be forward compatible.

It is a JSON file as follows:

```typescript
{
  sourceHash: String; // The hash of the corresponding source file.
  blobHash: String; // The hash of the blob.
  compilationStage: String; // The compilation stage of the IR.
}
```

All hashes are encoded in SHA3-224 format, as is used by other components in the
Engine. The engine version is encoded in the cache path, and hence does not need
to be explicitly specified in the metadata.

### Portability Guarantees

As part of this design we provide only the following portability guarantees:

- The serialised IR must be able to be deserialised by _the same version of
  Enso_ that wrote the original blob.

## Loading the IR

Loading the IR is a multi-stage process that involves performing integrity
checking on the loaded cache. It works as follows.

1. **Find the Cache:** Look in the global cache directory under `$ENSO_DATA`. If
   there is no cached IR here that is valid for the current configuration, check
   the ibrary's `.enso/cache` folder. This should be hooked into in
   `Compiler::parseModule`.
2. **Check Integrity:** Check the module's [metadata](#metadata-format) for
   validity according to the [integrity rules](#integrity-checking).
3. **Load:** If the cache passes the integrity check, load the `.ir` file. If
   deserialisation fails in any way, immediately fall back to parsing the source
   file.
4. **Re-Link:** If loading completed successfully, re-link the `BindingsMap`
   metadata to the proper modules in question.

The main subtlety here is handling the dependencies between modules. We need to
ensure that, when loading multiple cached libraries, we properly handle them
one-by-one. Doing this is as simple as hooking into `Compiler::parseModule` and
setting `AFTER_STATIC_PASSES` as the compilation state after loading the module.
This will tie into the current `ImportsResolver` and `ExportsResolver` which are
run in an un-gated fashion in `Compiler::run`.

In order to prevent the execution of malicious code when deserialising we should
employ a deserialisation filter as built into the JDK.

### Integrity Checking

For a cache to be usable, the following properties need to be satisfied:

1. The `sourceHash` must match the hash of the corresponding source file.
2. The `blobHash` must match the hash of the corresponding `.ir` file.

If any of these fail, the cache file should be deleted where possible, or
ignored if it is in a read-only location.

### Error Handling

It is important, as part of this, that we fail under all circumstances into a
working state. This means that:

- If serialisation fails, we report a low-priority error message and continue.
- If deserialisation fails, we fall back to loading and parsing the original
  source file.

At no point should this mechanism be exposed to the user in any visible way,
other than the fact that they may be seeing the actual files on disk.

## Testing the Serialisation

There are two main elements that need to be tested as part of this feature.

- Firstly, we need to test the serialisation and deserialisation process,
  including the rewrite of `BindingsMap` to work properly.
- We also need to test the discovery of cache locations on the filesystem and
  cache eviction strategies. The best way to do this is to set `$ENSO_DATA` to a
  temporary directory and then directly interact with the filesystem. Caching
  should be disabled for existing tests. This will require adding additional
  runtime options for debugging, but also constructing the `DistributionManager`
  on context creation (removing `RuntimeDistributionManager`).
