# Samples

One file, or one set of files, per thing the tool does. Open any of them with **File ▸ Open…**,
by dropping it on the page, or on the command line: `scripts/run.sh samples/purchaseOrder.xsd`.

| Sample | What to look at |
|---|---|
| `purchaseOrder.xsd` | The classic purchase order, exercising every kind of link: nested elements with their occurrences, a type extending another (`InternationalAddress` extends `USAddress`), a group, an attribute group, a list and a union, enumerations, a recursive type (`Category` holds its sub-categories). Start from `PurchaseOrderType` in the Model view; switch to Graph for the map. It imports `ext.xsd`, whose declaration is followed when the file is opened from disk. |
| `purchaseOrder.xml` | A document valid against it, for **Help ▸ Validate an XML file…** — together with the Schematron below. |
| `wsdl/purchaseOrderService.wsdl` | A WSDL 1.1 service over the purchase order: the service, its port, portType, operations and messages form a chain of their own, drawn with rounded corners, which the Model view opens down to the schema elements the messages carry. |
| `schematron/purchaseOrder.sch` | Rules over the purchase order: phases, patterns, rules, assertions and a diagnostic, and abstract patterns instantiated with parameters. Validate `purchaseOrder.xml` against it to see reports at every severity; pick a phase in the validation tab. |
| `longnames.xsd` | A schema whose declarations have deliberately long names: open `InternationalPurchaseOrderConfirmationType` in the Model view to see each name fill the top line of its box, up to the edge, with the type on the line below. |
| `import/order.xsd` | A schema importing and including three others (`address.xsd`, `items.xsd`, `types.xsd`): open `order.xsd` alone and follow the links into the other files — they are found beside it and open in their own tabs, the graph resolving its external targets from them. |
| `compare/` | Two versions of a small catalog schema set, one workspace each: what the comparison shows, file by file and declaration by declaration. See `compare/README.md`. |
| `workspace.xsdviewer.json` | Every file of both catalog versions in one workspace, as a saved workspace file opens: `scripts/run.sh samples/workspace.xsdviewer.json`. |
