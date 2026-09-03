# Changelog

What each version brought, newest first. The GitHub Releases carry the same notes with the downloads and their checksums.

## Unreleased

- **The constructs of a type are told apart in the graph.** A link to the item type of an `xs:list` ends in a filled diamond, one to each member of an `xs:union` in a hollow diamond, instead of the plain arrowhead. A nested element that is a branch of an `xs:choice` is marked `◇` before its name (a member of an `xs:all` `○`; unmarked, it follows an `xs:sequence`), and the details panel writes the word after the cardinality — the compositor is new in the model (`compositor` on a link). A declaration that enumerates its values shows how many at the bottom of its node (`≡ 3`), the values in its tooltip. Three legend entries name them.

- **A search finds the objects of every file of the workspace, and says what it cannot see.** The Files panel — where the files other than the shown one answer — opens by itself when a search starts, instead of leaving the results hidden when it had been folded, and its head counts the files that answer (`3 of 120`). A file that could not be parsed at all (an XML file that is not a schema, a fragment) used to disappear from every search without a word; it is now counted in a last row, naming the files in its tooltip, next to the row counting the files still being parsed.

- **Links ▾ in the graph toolbar** switches categories of link on and off — content, attributes, types, base types, references, and the file's own chain for a WSDL or a Schematron — to clear a crowded graph. The button is marked while a category is hidden, *Show them all* brings them back, and the choice is remembered; the menu offers only what the shown file can have.
- **Selecting an object in a view shows it in the left panel**: its group in the object list opens when it was folded, instead of the selection staying hidden.

- **A WSDL's service, and a Schematron's rules, read apart from the schema.** Their objects are drawn with rounded corners where the schema's own keep square ones, and every link with one of them at either end is drawn in the family's colour and heavier — dark blue for a service, teal for a set of rules — its word (`operation`, `input`, `fault`, `assert`…) in that colour instead of the small grey of the XSD words. So an operation, and what leads from it to the elements its messages carry, no longer looks like a type reference; the legend names the chain for such a file.

- **The side panels are resizable.** A grip along the inner edge of the left panel and of the details panel: drag it to set the width, double-click it for the default, or focus it and use the arrow keys. The widths are remembered by the browser, and the graph is redrawn to the room it has once the drag ends.

## 3.6.1 — 2026-09-03

- **Fixes after a review of 3.6.0.** A Schematron holding an element of no namespace (or of a foreign one) under a pattern or a rule was refused with an internal error; every missing `include` is reported, not only the first; a rule with an empty `context` no longer risks a crash; switching the *XSD* list of a validation tab keeps the Schematron phase. A firing assertion's message is followed by the `diagnostics` it names, rendered on the same node; a nested `include` resolves relative to the file that includes it.
- **The Schematron evaluator tidied for maintenance**: split into the evaluator proper, the DOM walking, the message rendering and the include resolution shared with the parser; the evaluation state made explicit; a test pinning that what a problem names is a node of the graph.

## 3.6.0 — 2026-09-03

- **Schematron files.** A `.sch` file (ISO Schematron `sch:schema`, the older Schematron 1.5 namespace too, or a fragment meant for `sch:include` — a `pattern`, a `rule`) opens like a schema, wherever an XSD does: File ▸ Open…, a dropped file, a folder, a workspace, the command line. Its phases, patterns, rules, asserts, reports and diagnostics are the objects (six new kinds, their own colours, listed in the legend for such a file), the links follow phase → pattern (`active`) → rule (`rule`) → assertion (`assert` / `report`) → diagnostic (`diagnostic`), plus `is a` from a pattern to the abstract pattern it instantiates and `extends` from a rule to the abstract rule it extends (both drawn as derivations). A rule is named by its context, an assertion by its id else its test, a pattern by its id, name or title; the expression itself (a rule's context, an assertion's test) is shown whole in a box of the details panel and searched by the search box. An assertion's message is its documentation, `[role]` first, a `value-of` shown as `{select}`. `samples/schematron/purchaseOrder.sch` is an example over the purchase order schema.
- **A validation tab, for XSD and Schematron.** *File ▸ Validate an XML file…* (or an `.xml` file dropped on a schema's tab) opens the outcome in a tab named `order.xml ⇢ purchaseOrder.xsd` instead of a dialog: the verdict and one chip per schema in the header, the problems on the left with their line, the document on the right with the lines marked (a click on either shows the other, the arrow keys walk the list), *errors only*, *↻ Run again* (the document read again from disk when the server chose it) and *Another XML document…*. A Schematron is validated too, with a new evaluator over the JDK's XPath 1.0 engine (no dependency): phases (a list in the header), abstract patterns and parameters, `extends`, `let`, `include`, messages with `value-of` and `name` filled in; a test the engine cannot compile (XPath 2 and later) is listed as *not evaluated* rather than passed. Each Schematron problem names the assertion, rule and pattern that fired, each a link selecting it in the Schematron's tab. When the workspace holds both an XSD and a Schematron with locations, a document is checked against both; the header's *XSD* and *SCH* lists switch to any located file of the workspace (or none). The document's path is shown when the server knows it. `POST /api/validate` takes `schema` and / or `schematron`, plus `phase`.
- **Samples that validate.** `samples/purchaseOrder.xml` is a document valid against `samples/purchaseOrder.xsd` and passing every rule of the Schematron; the schema now imports `samples/ext.xsd` for the `ext:Label` type it referred to without declaring the prefix, so File ▸ Validate works on it (the imported schema opens in its own tab, as any linked schema).

## 3.5.0 — 2026-09-03

- **A file's differences in a tab of their own.** In a comparison, the *⧉ In a tab* button next to the status of a *different* or *moved lines only* row (or a double-click on the row) opens that file pair's differences — the declarations and links on one side only, the two sources side by side — in a tab named `file.xsd (v1 ⇄ v2)`, next to the comparison, with the full height of the page; the same two options apply, and opening the same pair again brings its tab to front.

## 3.4.0 — 2026-09-02

- **The graph and the details resolve declarations across the whole workspace**, not only the open tabs: the files listed in the Files panel (parsed in the background) count too. An external object declared elsewhere shows as what it is there (its kind, its file) at both levels, level-2 targets are expanded from where they are declared, the users of the centre in other files appear on the left and in the details panel, and following a link opens a listed file's tab when needed.
- **File ▸ Validate an XML file…** checks a document against the shown schema with the JDK's validator, from the schema's file on disk (imports included); the problems are listed with their line and column.
- **Search inside declarations**: the search box also matches the names of the elements and attributes inside a declaration (a message's parts) and the documentation, the reason shown in grey after a listed object.
- **A find bar in the Text view** (Ctrl+F there): the lines holding a text are marked, Enter / Shift+Enter walk them.
- **⤓ SVG** saves the graph as a vector image.
- **Links for `xs:keyref`** (to the element declaring the key it refers to, labelled `keyref name`), and `xs:any` / `xs:anyAttribute` listed among a declaration's members (`any`, `anyAttribute`, with their namespace constraint).
- **Trimmed runtimes and a macOS build.** The distributions bundle a `jlink` image of Temurin 21 (the modules the tool needs: about a third of a full JRE — the Linux archive goes from 53 MB to 38 MB) instead of a whole JRE, and a macOS (Apple silicon) archive joins the Windows and Linux ones. `src/build/runtimes.xml` (Ant) links and packs them from the Temurin JDK archives in `jre/`.
- **The release is built by GitHub Actions**: a pushed tag `vX.Y.Z` downloads the JDKs, packages, and creates the release with the archives, the notes from this changelog's section and the checksums (`release.yml`). `scripts/release.sh` stays the way by hand.
- **File ▸ Open all listed files** opens in tabs the files a large folder left listed only (asked first from 30 files).
- **The graph from the keyboard**: Tab into it, the arrow keys walk the nodes, Home is the centre, Enter or Space acts as a click; landmarks and roles for assistive technology.
- Tests of the page's modules under Node (`src/test/js`), a contract test between the server's and the page's vocabularies, and `scripts/screenshots.py`, a visual smoke test with headless Firefox.

## 3.3.1 — 2026-09-02

- **One entry for the theme.** *Settings ▸ Dark theme* while the page is light, *Light theme* while it is dark: a single entry flips it. The system's light or dark setting is followed until you flip it once; the choice is then remembered by the browser.

## 3.3.0 — 2026-09-02

- **Dark theme.** *Settings ▸ Theme* chooses the page's colours: the system's (its light or dark setting, followed as it changes — the default), light, or dark; the browser remembers it, and the page never flashes white on load. Every view follows — panels, menus, graph, source text with its syntax colours, comparison, dialogs —, a few object colours are brightened for the dark ground, and the PNG export takes the page's background.

## 3.2.0 — 2026-09-02

- **The schema header is on the right.** The *Schema* panel (target namespace, imports, object and link counts) moves from the left column to the top of the details panel, above the selected object; still foldable, its state still remembered. The right panel now shows as soon as a file is loaded. The left column is the search box, the Files panel and the object list.
- **A clear button in the search box**: a small × at its right end, shown while it holds a text, empties it and redraws the Files panel and the object list — as Escape does.

## 3.1.0 — 2026-09-02

- **Large folders are searchable sooner.** The files listed beyond the ten opened are parsed in the background four at a time, the Files panel is redrawn at most every 300 ms instead of once per file, and while a search runs before they are all parsed a last row counts the files it cannot see yet (they were silently missing before). A folder of 1 500 schemas nine levels deep is fully searchable a few seconds after opening.
- **A busy wheel** in the top bar, with what is going on, while a long action runs: opening files, reading a folder, opening a workspace, looking for linked schemas, parsing the listed files (with the count left). It appears after 200 ms, so quick actions do not flash it.
- **The desktop's own file dialogs on Linux**: File ▸ Open…, Open folder…, Open / Save workspace… go through `kdialog` (KDE, LXQt) or `zenity` (GNOME and others) when one is installed, else a Swing chooser in the system look and feel — no more X11 relic or Metal chooser. Windows and macOS keep their native dialogs.
- **Any encoding**: schema files are read from their byte order mark, else as UTF-8, else by the encoding of the XML declaration, else as ISO-8859-1 — one Latin-1 schema no longer makes a whole folder unreadable.

## 3.0.0 — 2026-09-02

- **WSDL 1.1 files.** A `.wsdl` opens like a schema — from the command line, the file dialogs, a dropped folder or a workspace — and adds five kinds of object: *service*, *portType*, *operation*, *binding*, *message* (their chips join the legend for a WSDL). The links follow the chain service → portType (labelled with the port) → operation → message (`input` / `output` / `fault`) → element or type (labelled with the part), plus binding → portType. The schemas inline in `wsdl:types` are parsed as the file's own, their `xs:import`s followed like a schema's: an element declared in a separate `.xsd` resolves in that file's tab, and **2 levels** from an operation reaches the elements its messages carry. A WSDL opens on its first service. Sample: `samples/wsdl/purchaseOrderService.wsdl`.
- **Enumerations in the details panel.** An object restricted to `xs:enumeration` values (a simpleType, an element or attribute with an anonymous enumerated type, a complexType with an enumerated simpleContent) lists its values on the right, each with its own documentation.
- **Search box on top of the left panel**, above Files and Schema, staying in view while the panel scrolls; the object list gets an *Objects* header carrying its expand / collapse buttons.

## 2.8.0 — 2026-09-02

- **Graph: derivations look different from content links.** A type's link to its base type (`extends`, `restricts`) now ends in a hollow arrowhead, as a UML generalisation; a content link (a nested element, a `ref`, a `type`) keeps the filled one. The header legend gains a *base type* entry.

## 2.7.1 — 2026-08-30

- **Search covers the whole folder.** The search box (Ctrl+F) now also filters the Files panel: every schema of the workspace is searched, open in a tab or not — only the objects whose name contains the text are listed, in the files holding one (unfolded), empty folders hidden. A way to find a type across a whole folder without opening its files; clicking a match opens the file and selects the object.
- **Business lines only** also ignores the wiring: the XML declaration, the `xs:schema` root tags (however many lines their attributes take), `xs:import` and `xs:include` — they are no longer counted as differences between two versions.
- **Comparison: each side scrolls sideways on its own.** The line by line comparison is one table per side; a long line on one side no longer pushes the whole page, and the "N identical lines" fold labels stay in view.
- **Comparison: a colour legend** in the header — *only in v1* (red), *only in v2* (green), *moved lines* (blue).

## 2.6.0 — 2026-08-30

- **The server stops by itself** 15 seconds after the last page showing it has been closed — no orphan process when you close the browser. Each page holds a connection open for its whole life and the server only counts it gone when that connection breaks, so an idle page, even for hours or in a background tab, keeps the server up; a reload, a browser restart or a laptop waking up reconnect within the grace.
- **Settings menu** — *Stop the server when the last page is closed*, on by default; switch it off for a server that other computers reach or that you open pages on now and then. The choice is kept for the next runs. On the command line, `--keep-alive` turns it off for a run, and `--no-browser` implies it.
- README: how to get and install Java 21 (Windows, Linux, macOS), three screenshots.
- Build: the scripts live in `scripts/`, the distributions are built into `releases/`, GitHub Actions builds and tests every push.

## 2.5.0 — 2026-08-29

- **Open folder** lists the whole sub-tree (depth 64, up to 2000 files, symbolic links followed).
- **Comparison** works over every file the workspaces know, whether open in a tab or only listed.
- **Expand all / collapse all** buttons on every tree: the Files panel, the object list, the comparison rows.
