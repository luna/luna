---
layout: developer-doc
title: Comments
category: syntax
tags: [syntax, comments]
order: 13
---

# Comments

Enso supports a variety of types of comments:

- **Disable Comments:** TODO
- **Documentation Comments:** Documentation comments allow users to attach
  documentation to language constructs. This documentation can later be rendered
  to produce user-accessible HTML documentation, similar to tools included with
  most programming languages.

> The actionables for this section are:
>
> - Solidify exactly how each type of comment should behave.

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Disable Comments](#disable-comments)
- [Freeze Comments](#freeze-comments)
- [Documentation Comments](#documentation-comments)
  - [Tags](#tags)
  - [Sections](#sections)
  - [Links](#links)
  - [Lists](#lists)
  - [Code](#code)
  - [Text Formatting](#text-formatting)

<!-- /MarkdownTOC -->

## Disable Comments

Disable comments are the standard form of comment seen in a programming language
in that they prevent a given piece of code from executing. In Enso, they are
created by prefixing the expression to disable with the `#` character.

Disable comments in Enso do not have their contents validated, and continue from
the `#` character to the end of the line.

```ruby
x = y + z # here is some commented text
```

Disable comments are _not_ allowed inside textual interpolations.

## Freeze Comments

Freeze comments are a special type of comment used to enable the 'freezing' or
caching of expensive computations in Enso. When used, they cache the result of
an expression, reusing the value instead of recomputing it even if the
underlying data changes.

A portion of code that is frozen has the following properties:

- It is still lexed as if it were code, and validated by the parser to check for
  validity.
- No identifier resolution takes place.

These are very important as they still allow the frozen expression to be
displayed properly in the visual syntax.

> The actionables for this section are:
>
> - Work out what they should look like visually.
> - Work out how best to implement this.

## Documentation Comments

Documentation comments allow users to attach documentation to Enso language
constructs that can later be displayed in a rich format for users of the API.
Such comments are automatically connected to the language construct, and can be
used both for displaying static documentation as well as providing dynamic help
to the user in Enso Studio itself.

A documentation comment in Enso is a _block_, and the block is started with a
double `#` character. The block ends when the indentation returns to the
baseline indentation for that block (see [blocks](./functions.md#code-blocks)
for more information). By way of example:

```
## My documentation comment
   continues all the way down
   until I unindent again.
```

Documentation comments are _not_ allowed inside textual interpolations.

The tool that generates this documentation aims to be fairly robust, and tries
to assign produce sensible results even if the user makes a mistake. Such
mistakes will be highlighted to the user.

The documentation syntax is broken down into the following elements.

### Tags

Tags allow users to annotate their construct with information about its usage
state. Tags may only appear _once_ in a documentation block unless otherwise
noted. The documentation syntax supports the following tags:

- `ADDED`: Used to describe when a given construct was added to the library.
- `ADVANCED`: Items that are _not_ private, but are for power users.
- `ALIAS`: A name under which the documented entity will display in the
  searcher. This tag may occur _multiple times_ to provide multiple aliases.
- `DEPRECATED`: Used for constructs that should no longer be used and that may
  be removed in the future.
- `MODIFIED`: Used for constructs that have had their behaviour change after a
  certain version of the library.
- `PRIVATE`: Used to describe constructs that are private in the language.
- `REMOVED`: Used to describe constructs that have been removed and are no
  longer functional.
- `TEXT_ONLY`: Items that do not apply to the graphical mode.
- `UNSTABLE`: Used for items that are not yet considered stable.
- `UPCOMING`: Used to describe constructs that will be added in future versions
  of the library.

Tags are added at the _top_ of the documentation block, and may also be
accompanied by a description. This description directly follows the tag
declaration with one space.

```ruby
## DEPRECATED Use `seeFoo` instead
```

If the user provides an unknown tag the documentation will contain that tag, but
it will be undefined.

### Sections

Documentation comments can be broken up into sections, with each section
delineated by significant whitespace.

The first section that the user writes will be attributed to the 'synopsis' part
of the documentation, and the second section becomes the 'body'. They should be
used as follows:

- **Synopsis:** A brief summary of the function's behaviour.
- **Body:** More in-depth documentation where details of usage can be provided.

Sections may also have a title. If the whitespace before the section is _three_
newlines instead of _two_, then the first line of the section will be understood
to be a title.

The body can be broken down into multiple sections, with support for four
different types of section:

- **Raw:** A block of text, delineated purely by two blank lines before it.
- **Important:** A block of text describing important details about the
  functionality of the construct. To create an important section, prefix the
  title with `!`.
- **Info:** An information section that should be used to provide non-crucial
  details about the construct's usage. To create an info section, prefix the
  title with `?`.
- **Example:** For providing usage examples to the user. To create an example
  section, prefix the title with `>`.

### Links

Users are able to embed links and images into their documentation. These links
can serve to provide access to external resources or demonstrations, and also
link between various program constructs.

- **URLs:** `[Link title](URI)`
- **Images:** `![Image name](URI)`

Linked images are rendered in the generated documentation, and URLs will be
displayed like standard hyperlinks.

> The actionables for this section are:
>
> - We probably want a construct that lets you reference other API constructs.

### Lists

The Enso documentation syntax also supports ordered and unordered lists. These
can be nested, and the nesting may swap the types. Both list types must be
intended some multiple of 2 spaces from the left margin of the documentation
comment.

- **Unordered:** List items are indicated by the `-` character.
- **Ordered:** List items are indicated by the `*` character.

To nest a list inside another list, add another 2-character indent to the nested
list.

### Code

The Enso documentation syntax allows users to write code that will be displayed
as code rather than prose. It supports two types of code.

- **Inline Code:** Text enclosed in `` ` `` will be formatted as inline code.
- **Multi-Line Code:** A block that is indented from the baseline of the current
  section will be formatted as a code block.

### Text Formatting

Enso's documentation syntax also supports some basic syntax for adding rich text
formatting to the documentation.

- **Italics:** Enclosing text in `_` (e.g. `_Italics_`).
- **Bold:** Enclosing text in `*` (e.g. `*Bold*`).
- **Strikethrough:** Enclosing text in `~` (e.g. `~Strikethrough~`).

These syntaxes may be combined, and the order of opening need not equal the
order of closing. However, if the formatting syntaxes are not closed, this will
result in an error.
