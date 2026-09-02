# Changelog

What each version brought, newest first. The GitHub Releases carry the same notes with the downloads and their checksums.

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
