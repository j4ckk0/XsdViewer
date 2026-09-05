# Comparing two versions of a schema set

Two versions of a small catalog schema, one workspace file each:

    scripts/run.sh samples/compare/v1.xsdviewer.json      # opens version 1 as a workspace
    File > Open workspace…  ->  samples/compare/v2.xsdviewer.json
    Ctrl+click the "v1" and "v2" chips of the workspace bar, then "⇄ Compare": the Files section
    lists the files of the two, paired by name. In the Objects section, put ProductType of each
    version on a side (◈ Left side / ◈ Right side in the details panel) to see the two as models,
    text or graphs.

What the comparison shows:

| File | Status | Why |
|---|---|---|
| `common.xsd` | identical | same file in both versions |
| `catalog.xsd` | different, same declarations and links | only the documentation and the layout changed: the expanded row says so and shows the line differences |
| `product.xsd` | different | `description` became mandatory (1 instead of 0..1), `tag` is capped to 10, `legacyCode` was dropped, `weight` (type `Weight`, with a `unit` attribute of type `Unit`) was added, `category` became required |
| `supplier.xsd` | only in v1 | dropped in version 2 |
| `shipping.xsd` | only in v2 | new in version 2 |
