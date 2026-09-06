# XsdViewer – rules for Claude sessions

These rules apply to every session working on this repository. The build, the layout and the
architecture are documented in README.md and architecture.md; this file only holds the review rules.

## Code review

A code review answers one question: **can a developer who has never seen this code read it and know
where things belong?** Correctness, speed and security are separate reviews.

1. **Modules with clear responsibilities.** Each unit (Maven module, Java package, JS module) does one
   thing that can be stated in one sentence without "and". Business rules live in the library
   (`core`), not in the HTTP plumbing that serves them (`app`). A file that does two things is a
   finding; so is a responsibility scattered over three files with no home.
2. **Objects a developer understands at a glance.** A name says the subject *and* the kind of object:
   a model class, a helper, a controller, a view, a query service, a writer. A name that does not
   say which of these it is, is a finding. Junk-drawer names (`Utils`, `Common`, `Manager` alone)
   are a finding. One concept has one name across Java, JavaScript and the JSON contract.
3. **Method names are promises.** No `handle`, `process`, `run` without an object; no `validateAndSave`;
   no `get`/`is`/`find` that writes or mutates.
4. **Relationships are predictable.** Dependencies point one way (server → core, page → API). Cycles,
   god objects and pass-through layers are findings.
5. **File size is a signal.** Over 500 lines, name the responsibilities inside the file; propose the
   seam or say there is genuinely one responsibility. Past 1000 lines, report it regardless.

How to run it:

- Gather the whole picture first: every class by package with its size and kind, every JS module with
  its first doc line. Measure line counts, do not estimate them.
- Verify each finding before reporting it. Report the file and the scenario.
- Rank findings by what would cost a new developer the most, not by how easy they are to fix. Report a
  recurring problem once, with a count and examples. Name what is working too.
- Report first, edit only after approval. Then one concern per change, smallest blast radius first,
  tests run after each one. A structural change never fixes a bug on the way.
- Do not spawn subagents for a review. `/code-review ultra` belongs to the user; never launch it.

The full method is the `full-app-code-review` skill when it is installed.

## Comments review

Every comment in this project, inline or API doc (Javadoc, JSDoc), follows two rules:

1. **A comment tells the intention.** It says why the code is as it is, or the constraint that forced
   this approach, so a maintainer understands the purpose. It never narrates what the next line does.
2. **A comment never tells the history.** No "was X", "now uses Y", "since the refactor", dates,
   authors or ticket numbers. Version control keeps the history; the comment describes the present
   state only.

And one on length: one or two lines above a code group, up to about five for a class or module
header. Longer explanations go to architecture.md with a one-line pointer in the source.

How to run it:

- Read the code with the comment; a comment is judged against what the code does today.
- Fix in place: remove history, remove restatements, trim mixed comments to the reason, rewrite stale
  ones, remove commented-out code. Never invent a rationale.
- After a design change, grep the comments for the old vocabulary (a renamed view, a removed concept)
  and rewrite them to describe what is.
- Leave alone: directives (`@SuppressWarnings`, `eslint-disable`), licence headers, TODO/FIXME markers
  (list them in the report instead).
- Report what changed as a table (file, problem, action) and list what is left for the user: a claim
  removed that may hide a lost feature, a code group whose intention only its author can write.

The full method is the `review-code-comments` skill when it is installed.
