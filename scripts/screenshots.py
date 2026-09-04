#!/usr/bin/env python3
"""
A visual smoke test of the page: opens the samples in the built jar, drives the page (a selection,
a view, the theme), checks a few facts on it and saves a screenshot of each scene.

    scripts/screenshots.py                 # after mvn package: target/screenshots/*.png, checks on stdout
    scripts/screenshots.py --keep-going    # every scene even after a failed check
    scripts/screenshots.py --docs          # only the scenes of the README, saved as JPEG in screenshots/
    scripts/screenshots.py --only a,b      # only these scenes
    FIREFOX=/path/to/firefox scripts/screenshots.py

Needs Firefox (its headless --screenshot) and the jar in target/. The page is reached through a
small proxy that injects the scene's script and holds the page's load event until the script has
run (a hidden image answered late), which is when Firefox takes the screenshot. A scene's script may
await (it runs in an async function) and fetch a file of the repository from /__sample/<path>. Exit
code 1 when a check fails or a scene cannot be shot.
"""
import http.client
import http.server
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / 'target' / 'xsdviewer.jar'
OUT = ROOT / 'target' / 'screenshots'
DOCS = ROOT / 'screenshots'   # the pictures of the README, written by --docs from the scenes carrying a "doc" name
FIREFOX = os.environ.get('FIREFOX', 'firefox')
APP_PORT, PROXY_PORT = 8765, 8766
SIZE = '1500,800'
DOC_SIZE = '1920,1048'    # the pictures of the README: a window wide enough for the graph's second level
HOLD_SECONDS = 6          # the load event is held this long: the scene's script runs at 1.5 s (the comparison scene opens a workspace first)
ACTION_DELAY_MS = 1500

# a second workspace, "v2" of the comparison sample, opened from files fetched through the proxy as if a folder had been dropped
OPEN_V2 = ("const names = ['common.xsd', 'catalog.xsd', 'product.xsd', 'shipping.xsd'];"
           "const files = await Promise.all(names.map(async n => new File([await (await fetch('/__sample/samples/compare/v2/' + n)).text()], n)));"
           "const wa = await import('/js/workspace-actions.js'), st = await import('/js/state.js'), cmp = await import('/js/compare.js'), pg = await import('/js/page.js');"
           "await wa.openBrowserFolder(files, f => 'v2/' + f.name, 'v2');"
           "for (const ws of st.session.workspaces) cmp.toggleSelection(ws);"
           "cmp.startCompare(); pg.renderPage();")

# name, file, theme, the script run on the page (may use the page's DOM and await), the checks (an expression per check name)
SCENES = [
    dict(name='graph-light', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();",
         checks={'title': "document.getElementById('graphTitle').textContent",
                 'nodes': "document.querySelectorAll('#graphCanvas .node').length",
                 'details': "document.querySelector('#detailsContent h2').textContent"},
         expect={'title': 'complexType PurchaseOrderType', 'nodes': 8, 'details': 'PurchaseOrderType'}),   # the centre, 6 links out, 1 user
    dict(name='types-filtered', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();"
                "document.getElementById('typeMenuBtn').click();"
                "document.querySelector('#typeMenu [data-kind=\"builtin\"]').click();"
                "document.querySelector('#typeMenu [data-kind=\"attributeGroup\"]').click();",
         checks={'nodes': "document.querySelectorAll('#graphCanvas .node').length",
                 'marked': "document.getElementById('typeMenuBtn').classList.contains('filtered')",
                 'kept': "localStorage.getItem('xsdviewer.hiddenKinds')",
                 'offered': "[...document.querySelectorAll('#typeMenu [data-kind]')].filter(b => getComputedStyle(b).display !== 'none').length",
                 'checks': "[...document.querySelectorAll('#typeMenu [data-kind].checked')].length"},
         expect={'nodes': 6, 'marked': True, 'kept': 'builtin,attributeGroup', 'offered': 8, 'checks': 17}),   # 8 nodes without the filter: the built-in date and the attribute group go; 2 of the 19 entries are off
    dict(name='links-filtered', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();"
                "document.getElementById('linkMenuBtn').click();"
                "document.querySelector('#linkMenu [data-category=\"attribute\"]').click();"
                "document.querySelector('#linkMenu [data-category=\"type\"]').click();"
                "document.getElementById('linkMenuBtn').click();",
         checks={'nodes': "document.querySelectorAll('#graphCanvas .node').length",
                 'marked': "document.getElementById('linkMenuBtn').classList.contains('filtered')",
                 'kept': "localStorage.getItem('xsdviewer.hiddenLinks')",
                 'offered': "[...document.querySelectorAll('#linkMenu [data-category]')].filter(b => getComputedStyle(b).display !== 'none').length"},
         expect={'nodes': 6, 'marked': True, 'kept': 'attribute,type', 'offered': 5}),   # 8 without the filter: the attribute link and the type link that uses the centre go
    dict(name='select-from-graph', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.getElementById('objectsCollapseAll').click();"
                "document.querySelector('#graphCanvas .node:not(.center)').dispatchEvent(new MouseEvent('click', {bubbles: true}));",
         checks={'selected': "document.querySelector('#nodeList .item.selected span:nth-child(2)').textContent",
                 'centre': "document.getElementById('graphTitle').textContent",
                 'openGroups': "document.querySelectorAll('#nodeList .group-h:not(.collapsed)').length"},
         expect={'selected': 'PurchaseOrderType', 'centre': 'complexType PurchaseOrderType', 'openGroups': 1}),   # every group was folded: the one holding the selection opens
    dict(name='model-view', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"complexType:Items\"]').click();"
                "document.querySelector('.tab[data-view=\"model\"]').click();",
         checks={'boxes': "document.querySelectorAll('#modelCanvas .mbox').length",
                 'compositors': "[...document.querySelectorAll('#modelCanvas .mbox.sequence .mglyph')].length",
                 'names': "[...document.querySelectorAll('#modelCanvas .mbox .mname')].map(t => t.textContent).join('|')",
                 'handles': "document.querySelectorAll('#modelCanvas .mhandle').length",
                 'cards': "[...document.querySelectorAll('#modelCanvas .mbox .card')].map(t => t.textContent).join('|')",
                 'legend': "document.querySelectorAll('#modelLegend .row').length + ':' + document.querySelectorAll('#modelLegend .lg').length",
                 'svgButton': "document.getElementById('exportSvgBtn').disabled"},
         expect={'boxes': 11, 'compositors': 2, 'names': 'Items|item|@partNum : SKU|productName|quantity|USPrice|comment|shipDate|ItemExtras',
                 'handles': 1, 'cards': '0..*|0..1|0..1|0..1', 'legend': '3:23', 'svgButton': False}),   # ItemExtras (a group) opens on demand; comment refers to a global element of a built-in type: nothing inside
    dict(name='model-expanded', file='samples/purchaseOrder.xsd', theme='dark',
         action="document.querySelector('#nodeList .item[data-id=\"complexType:InternationalAddress\"]').click();"
                "document.querySelector('.tab[data-view=\"model\"]').click();"
                "document.getElementById('modelExpandAll').click();",
         checks={'base': "document.querySelector('#modelCanvas .mbox.complexType:not(.center) .mword').textContent",
                 'names': "[...document.querySelectorAll('#modelCanvas .mbox .mname')].map(t => t.textContent).join('|')",
                 'folded': "document.querySelectorAll('#modelCanvas .mhandle text').length + ':' + [...document.querySelectorAll('#modelCanvas .mhandle text')].map(t => t.textContent).join('')"},
         expect={'base': 'extends', 'names': 'InternationalAddress|USAddress|@country : NMTOKEN ?|name|street|city|state|zip|countryName', 'folded': '1:−'}),   # the base type opened: its attribute and its sequence, then the extension's own element
    dict(name='enumeration', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"simpleType:Currency\"]').click();",
         checks={'values': "document.querySelectorAll('#detailsContent .value').length",
                 'badge': "document.querySelector('#graphCanvas .node.center text.enum').textContent"},
         expect={'values': 3, 'badge': '≡ 3'}),
    dict(name='compositors', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();const fa = await import('/js/file-actions.js');"
                "await fa.openFiles([new File(['<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:c\"><xs:complexType name=\"Payment\"><xs:choice><xs:element name=\"card\" type=\"xs:string\"/><xs:element name=\"transfer\" type=\"xs:string\"/></xs:choice><xs:attribute name=\"amount\" type=\"xs:decimal\"/></xs:complexType><xs:complexType name=\"Person\"><xs:all><xs:element name=\"first\" type=\"xs:string\"/></xs:all></xs:complexType></xs:schema>'], 'choice.xsd')]);"
                "document.querySelector('#nodeList .item[data-id=\"complexType:Payment\"]').click();",
         checks={'captions': "[...document.querySelectorAll('#graphCanvas .link-name')].map(t => t.textContent.trim()).sort().join('|')",
                 'marks': "[...document.querySelectorAll('#graphCanvas .link-name .compositor')].map(t => t.textContent.trim()).join('|')",
                 'detail': "document.querySelector('#detailsContent .link .compositor') ? document.querySelector('#detailsContent .link .compositor').textContent : ''"},
         expect={'captions': 'amount 0..1|\u25c7 card 0..1|\u25c7 transfer 0..1', 'marks': '\u25c7|\u25c7', 'detail': 'choice'}),
    dict(name='list-union', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"simpleType:Identifier\"]').click();",
         checks={'unionHead': "document.querySelectorAll('#graphCanvas path[marker-end$=\"unionArrow)\"]').length",
                 'captions': "[...document.querySelectorAll('#graphCanvas .link-name')].map(t => t.textContent.trim()).sort().join('|')"},
         expect={'unionHead': 2, 'captions': 'union of|union of'}),   # SKU and xs:positiveInteger, the members of the union
    dict(name='text-dark', file='samples/purchaseOrder.xsd', theme='dark',
         action="document.querySelector('.tab[data-view=\"text\"]').click();",
         checks={'theme': "document.documentElement.dataset.theme",
                 'highlighted': "document.querySelectorAll('#text .line.hl').length"},
         expect={'theme': 'dark', 'highlighted': 1}),
    dict(name='wsdl-operation', file='samples/wsdl/purchaseOrderService.wsdl', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"operation:PurchaseOrderPortType.submitPurchaseOrder\"]').click();"
                "const two = document.getElementById('twoLevels'); if (!two.checked) two.click();",
         checks={'messages': "document.querySelectorAll('#graphCanvas .node.message').length",
                 'tabs': "document.querySelectorAll('#tabs .dtab').length",
                 'legend': "getComputedStyle(document.querySelector('#graphLegend .lg.service')).display !== 'none'",
                 'chainArrow': "getComputedStyle(document.querySelector('#graphLegend .lg.arrow.wsdl')).display !== 'none'",
                 'chainEdges': "document.querySelectorAll('#graphCanvas .edge.chain.wsdl').length",
                 'plainEdges': "document.querySelectorAll('#graphCanvas .edge:not(.chain)').length",
                 'rounded': "[...document.querySelectorAll('#graphCanvas .node')].filter(g => g.querySelector('rect').getAttribute('rx')).map(g => g.className.baseVal.split(' ')[1]).sort().join(',')"},
         expect={'messages': 3, 'tabs': 3, 'legend': True, 'chainArrow': True, 'chainEdges': 7, 'plainEdges': 0,
                 'rounded': 'message,message,message,operation,portType'}),   # portType -> operation, its 3 messages, and each message to what it carries: every link has a service end   # the WSDL, purchaseOrder.xsd it imports, ext.xsd that one imports
    dict(name='model-wsdl', file='samples/wsdl/purchaseOrderService.wsdl', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"service:PurchaseOrderService\"]').click();"
                "document.querySelector('.tab[data-view=\"model\"]').click();"
                "document.querySelector('#modelCanvas .mhandle').dispatchEvent(new MouseEvent('click', { bubbles: true }));",
         checks={'names': "[...document.querySelectorAll('#modelCanvas .mbox .mname')].map(t => t.textContent).join('|')",
                 'words': "[...document.querySelectorAll('#modelCanvas .mbox .mword')].map(t => t.textContent).join('|')",
                 'kinds': "[...document.querySelectorAll('#modelCanvas .mbox .mtype')].map(t => t.textContent).join('|')",
                 'rounded': "[...document.querySelectorAll('#modelCanvas .mbox')].filter(g => g.querySelector('rect').getAttribute('rx') === '9').length",
                 'legend': "getComputedStyle(document.querySelector('#modelLegend .lg.box.service')).display !== 'none'",
                 'empty': "!document.getElementById('modelEmpty').classList.contains('hidden')"},
         expect={'names': 'PurchaseOrderService|PurchaseOrderPortType|submitPurchaseOrder|getOrderStatus',
                 'words': 'service|PurchaseOrderPort|operation|operation',
                 'kinds': 'portType',
                 'rounded': 4, 'legend': True, 'empty': False}),   # the service, its port to the portType, and that one opened on its two operations (whose word is already their kind, said once)
    dict(name='model-schematron', file='samples/schematron/purchaseOrder.sch', theme='dark',
         action="document.querySelector('#nodeList .item[data-id=\"phase:basic\"]').click();"
                "document.querySelector('.tab[data-view=\"model\"]').click();"
                "document.getElementById('modelExpandAll').click();",
         checks={'words': "[...new Set([...document.querySelectorAll('#modelCanvas .mbox .mword')].map(t => t.textContent))].join('|')",
                 'kinds': "[...new Set([...document.querySelectorAll('#modelCanvas .mbox .mtype')].map(t => t.textContent))].join('|')",
                 'noXsdMarks': "getComputedStyle(document.querySelector('#modelLegend .lg.arrow.xsd')).display === 'none'",
                 'legend': "getComputedStyle(document.querySelector('#modelLegend .lg.box.pattern')).display !== 'none'"},
         expect={'words': 'phase|active|rule|assert|extends|report|diagnostic', 'kinds': 'pattern|rule',
                 'noXsdMarks': True, 'legend': True}),   # a phase, its patterns, their rules and what those assert: a Schematron's chain is its model too
    dict(name='schematron-rule', file='samples/schematron/purchaseOrder.sch', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"rule:structure/po:item\"]').click();"
                "const two = document.getElementById('twoLevels'); if (!two.checked) two.click();",
         checks={'title': "document.getElementById('graphTitle').textContent",
                 'assertions': "document.querySelectorAll('#graphCanvas .node.assert, #graphCanvas .node.report').length",
                 'xpath': "document.querySelector('#detailsContent .xpath code').textContent",
                 'legend': "getComputedStyle(document.querySelector('#graphLegend .lg.rule')).display !== 'none' && getComputedStyle(document.querySelector('#graphLegend .lg.element')).display === 'none'",
                 'groups': "[...document.querySelectorAll('#nodeList .group-h > span:first-child')].map(g => g.textContent).join('|')"},
         expect={'title': 'rule po:item', 'assertions': 2, 'xpath': 'po:item', 'legend': True,
                 'groups': 'Phases|Patterns|Rules|Asserts|Reports|Diagnostics'}),   # its own assert, the report of the abstract rule it extends (level 2)
    dict(name='validation-schematron', file='samples/schematron/purchaseOrder.sch', theme='light',
         action="const xml = (await (await fetch('/__sample/samples/purchaseOrder.xml')).text())"
                ".replace('<po:quantity>1</po:quantity>', '<po:quantity>120</po:quantity>').replace('<po:comment>Confirm this is electric</po:comment>', '');"
                "const v = await import('/js/validate.js'); await v.validateText('purchaseOrder.xml', xml);",
         checks={'tab': "document.querySelector('#tabs .dtab.active').className",
                 'title': "document.getElementById('validateTitle').textContent",
                 'rows': "[...document.querySelectorAll('#validateProblems .vprob')].map(r => r.className.replace('vprob ', '')).join('|')",
                 'links': "document.querySelectorAll('#validateProblems .vlink').length",
                 'marked': "document.querySelectorAll('#validateDoc .line.vline').length",
                 'phases': "[...document.querySelectorAll('#validatePhase option')].map(o => o.value).join('|')",
                 'chips': "[...document.querySelectorAll('#validateSchemas select')].map(s => [...s.options].map(o => o.textContent).join(',')).join('|')"},
         expect={'tab': 'dtab active vtab invalid', 'title': '✗ purchaseOrder.xml does not conform: 1 error', 'rows': 'warning|error selected',
                 'links': 6, 'marked': 1, 'phases': '#ALL|basic|full', 'chips': 'none|purchaseOrder.sch'}),   # no XSD in this workspace; the Schematron cannot be dropped, being the only schema   # the report and the assert both point at item 1: one marked line
    dict(name='validation-xsd', file='samples/purchaseOrder.xsd', theme='dark',
         action="const xml = (await (await fetch('/__sample/samples/purchaseOrder.xml')).text()).replace('<po:zip>90952</po:zip>', '<po:zip>abc</po:zip>');"
                "const v = await import('/js/validate.js'); await v.validateText('purchaseOrder.xml', xml);",
         checks={'chips': "document.querySelectorAll('#validateSchemas .vchip.invalid').length",
                 'lists': "[...document.querySelectorAll('#validateSchemas select')].map(s => [...s.options].map(o => o.textContent).join(',')).join('|')",
                 'rows': "document.querySelectorAll('#validateProblems .vprob.error').length",
                 'where': "document.querySelector('#validateProblems .vprob .vwhere').textContent",
                 'highlighted': "document.querySelectorAll('#validateDoc .line.hl').length"},
         expect={'chips': 1, 'lists': 'purchaseOrder.xsd,ext.xsd|none', 'rows': 2, 'where': 'line 13, column 25', 'highlighted': 1}),   # the datatype facet and the type: two problems on the zip line
    dict(name='validation-switch', file='samples/purchaseOrder.xsd', theme='light',
         action="const xml = await (await fetch('/__sample/samples/purchaseOrder.xml')).text();"
                "const v = await import('/js/validate.js'); await v.validateText('purchaseOrder.xml', xml);"
                "const list = document.querySelector('#validateSchemas select[data-source=\"xsd\"]'); list.value = [...list.options].find(o => o.textContent === 'ext.xsd').value;"
                "list.dispatchEvent(new Event('change', {bubbles: true})); await new Promise(r => setTimeout(r, 800));",
         checks={'tab': "document.querySelector('#tabs .dtab.active .tname').textContent",
                 'valid': "document.getElementById('validateTitle').className",
                 'rows': "document.querySelectorAll('#validateProblems .vprob.error').length"},
         expect={'tab': 'purchaseOrder.xml ⇢ ext.xsd', 'valid': 'invalid', 'rows': 1}),   # ext.xsd declares no element: the document's root is unknown to it
    dict(name='panels-resized', file='samples/purchaseOrder.xsd', theme='light',
         action="const p = await import('/js/panels.js');"
                "const drag = (id, dx) => { const el = document.getElementById(id), r = el.getBoundingClientRect();"
                "  el.setPointerCapture = () => {}; const at = (x) => ({pointerId: 1, clientX: x, preventDefault(){}});"
                "  el.dispatchEvent(new PointerEvent('pointerdown', at(r.left)));"
                "  el.dispatchEvent(new PointerEvent('pointermove', at(r.left + dx)));"
                "  el.dispatchEvent(new PointerEvent('pointerup', at(r.left + dx))); };"
                "drag('sidebarSplitter', 120); drag('detailsSplitter', -110);",
         checks={'sidebar': "Math.round(document.getElementById('sidebar').getBoundingClientRect().width)",
                 'details': "Math.round(document.getElementById('details').getBoundingClientRect().width)",
                 'kept': "localStorage.getItem('xsdviewer.sidebarWidth') + '|' + localStorage.getItem('xsdviewer.detailsWidth')",
                 'splitters': "[...document.querySelectorAll('.splitter')].filter(s => !s.classList.contains('hidden')).length"},
         expect={'sidebar': 390, 'details': 390, 'kept': '390|390', 'splitters': 2}),
    dict(name='search-member', file='samples/purchaseOrder.xsd', theme='light',

         action="const s = document.getElementById('search'); s.value = 'shipTo'; s.dispatchEvent(new Event('input', {bubbles: true}));",
         checks={'listed': "[...document.querySelectorAll('#nodeList .item')].map(i => i.textContent).join('|')"},
         expect={'listed': 'PurchaseOrderTypeshipTo'}),
    dict(name='text-find', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"text\"]').click();"
                "const f = document.getElementById('textFindInput'); f.value = 'xs:attribute '; f.dispatchEvent(new Event('input', {bubbles: true}));",
         checks={'count': "document.getElementById('textFindCount').textContent",
                 'current': "document.querySelectorAll('#text .line.found.current').length",
                 'svgButton': "document.getElementById('exportSvgBtn').disabled"},
         expect={'count': '1/7', 'current': 1, 'svgButton': True}),
    dict(name='listed-files', file='target/screenshots/listed/listed.xsdviewer.json', theme='light', setup='listed',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"complexType:OrderType\"]').click();",
         checks={'resolved': "document.querySelectorAll('#graphCanvas .node.complexType, #graphCanvas .node.simpleType').length",
                 'external': "document.querySelectorAll('#graphCanvas .node.external').length",
                 'fromFiles': "[...document.querySelectorAll('#graphCanvas .node .kind')].filter(k => k.textContent.includes('.xsd')).length"},
         expect={'resolved': 5, 'external': 0, 'fromFiles': 4}),   # the centre and its four targets, resolved from the listed files
    dict(name='search-folder', file='samples/purchaseOrder.xsd', theme='light',
         action="const names = ['order.xsd','address.xsd','items.xsd','types.xsd'];"
                "const files = await Promise.all(names.map(async n => new File([await (await fetch('/__sample/samples/import/' + n)).text()], n)));"
                "const filler = (i) => '<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:f:' + i + '\"><xs:element name=\"f' + i + '\" type=\"xs:string\"/></xs:schema>';"
                "for (let i = 0; i < 8; i++) files.push(new File([filler(i)], 'filler' + i + '.xsd'));"
                "files.push(new File(['<notASchema/>'], 'broken.xsd'));"
                "const fl = await import('/js/file-list.js'); fl.setFilesCollapsed(true);"   # folded: a search must still reach the other files
                "const wa = await import('/js/workspace-actions.js');"
                "await wa.openBrowserFolder(files, f => 'listed/' + f.name, 'listed');"
                "const st = await import('/js/state.js');"   # the listed files are parsed in the background: wait for the queue to drain
                "const ws = st.session.workspaces[st.session.workspaces.length - 1];"
                "for (let i = 0; i < 40 && ws.files.some(f => !f.model && !f.failed); i++) await new Promise(r => setTimeout(r, 100));"
                "const s = document.getElementById('search'); s.value = 'f5'; s.dispatchEvent(new Event('input', {bubbles: true}));"
                "await new Promise(r => setTimeout(r, 300));",
         checks={'objects': "[...document.querySelectorAll('#filesContent .item.obj')].map(i => i.textContent.trim()).join('|')",
                 'count': "document.getElementById('filesCount').textContent",
                 'unfolded': "!document.getElementById('files').classList.contains('collapsed')",
                 'failed': "document.querySelector('#filesContent .item.failed') ? document.querySelector('#filesContent .item.failed').textContent : ''"},
         expect={'objects': 'f5', 'count': '1 of 13', 'unfolded': True, 'failed': '1 file the search cannot see'}),   # the object of a listed file is found; the file that is not a schema is counted, not silently missing
    dict(name='imports', file='samples/import/order.xsd', theme='light', action="",
         checks={'tabs': "document.querySelectorAll('#tabs .dtab').length",
                 'files': "document.getElementById('filesCount').textContent"},
         expect={'tabs': 4, 'files': '4'}),
    dict(name='compare', file='samples/compare/v1.xsdviewer.json', theme='light', action=OPEN_V2,
         checks={'rows': "document.querySelectorAll('#compareTable .crow').length",
                 'openButtons': "document.querySelectorAll('#compareTable .copen').length",
                 'label': "document.querySelector('#compareTable .copen').textContent"},
         expect={'rows': 5, 'openButtons': 1, 'label': '⧉ In a tab'}),   # product.xsd is the one file differing in its business lines
    dict(name='compare-file-tab', file='samples/compare/v1.xsdviewer.json', theme='light',
         action=OPEN_V2 + "document.querySelector('#compareTable .crow.different .copen').click();"
                "await new Promise(r => setTimeout(r, 300));",
         checks={'tab': "document.querySelector('#tabs .dtab.active .tname').textContent",
                 'title': "document.getElementById('compareTitle').textContent",
                 'rows': "document.querySelectorAll('#compareTable .crow').length",
                 'detail': "document.querySelectorAll('#compareTable .cdetail').length",
                 'tools': "document.getElementById('compareTools').classList.contains('hidden')"},
         expect={'tab': 'product.xsd (v1 ⇄ v2)', 'title': 'product.xsd: v1 compared with v2', 'rows': 1, 'detail': 1, 'tools': True}),   # catalog.xsd differs in its documentation only: identical business lines
    dict(name='compare-object', file='samples/compare/v1.xsdviewer.json', theme='light',
         action=OPEN_V2 + "document.getElementById('compareClose').click();"
                # the view explains itself while nothing is marked
                "document.querySelector('.tab[data-view=\"compare\"]').click();"
                "window.__empty = document.getElementById('objectCompareEmpty').textContent.slice(0, 24);"
                # ProductType of v1, marked, then the one of v2: two workspaces, two files of the same name
                "[...document.querySelectorAll('#workspaces .wsgroup')].find(w => w.textContent.includes('v1')).click();"
                "[...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "document.querySelector('#detailsContent .cobj-mark').click();"
                "[...document.querySelectorAll('#workspaces .wsgroup')].find(w => w.textContent.includes('v2')).click();"
                "[...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "document.querySelector('#detailsContent .cobj-mark').click();"
                "document.querySelector('.tab[data-view=\"compare\"]').click();"
                "await new Promise(r => setTimeout(r, 400));"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'title': "document.getElementById('objectCompareTitle').textContent",
                 'summary': "document.getElementById('objectCompareSummary').textContent",
                 'heads': "[...document.querySelectorAll('#objectCompareBody .cobj-head')].map(h => h.textContent).join('|')",
                 'marks': "['removed','added','changed'].map(c => document.querySelectorAll('#objectCompareBody .mbox.' + c).length).join('/')",
                 'marked': "document.querySelectorAll('#detailsContent .cobj-mark.marked').length",
                 'guidance': "window.__empty",
                 'details': "document.getElementById('details').classList.contains('hidden')"},
         expect={'title': 'complexType ProductType compared with complexType ProductType',
                 'summary': '1 only on the left, 3 only on the right, 3 changed',
                 'heads': 'complexType ProductType — product.xsd, v1|complexType ProductType — product.xsd, v2',
                 'marks': '1/3/6', 'marked': 1, 'guidance': 'Mark a declaration for c', 'details': True}),   # legacyCode gone; weight added with its own type; category, description and tag changed
    # the four pictures of the README (screenshots/), on the comparison sample: shot like any other
    # scene, checked like any other, and written as JPEG by --docs
    dict(name='doc-model', file='samples/compare/v1.xsdviewer.json', theme='light', size=DOC_SIZE, doc='XsdViewer-model-view.jpg',
         action="document.querySelectorAll('#tabs .dtab')[1].click();"
                "document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'names': "[...document.querySelectorAll('#modelCanvas .mbox .mname')].map(t => t.textContent).join('|')",
                 'view': "document.querySelector('.tab.active').dataset.view"},
         expect={'names': 'ProductType|@sku : Code|@category : string ?|name|description|price|discount|legacyCode|tag',
                 'view': 'model'}),   # the whole model of a type: its attributes, its sequence, each element with its occurrences and its type
    dict(name='doc-graph', file='samples/compare/v1.xsdviewer.json', theme='light', size=DOC_SIZE, doc='XsdViewer-graph-view.jpg',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();"
                "document.querySelector('#nodeList .item[data-id=\"complexType:CatalogType\"]').click();"
                "const two = document.getElementById('twoLevels'); if (!two.checked) two.click();"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'title': "document.getElementById('graphTitle').textContent",
                 'nodes': "document.querySelectorAll('#graphCanvas .node').length"},
         expect={'title': 'complexType CatalogType', 'nodes': 10}),
    dict(name='doc-text', file='samples/compare/v1.xsdviewer.json', theme='light', size=DOC_SIZE, doc='XsdViewer-xml-view.jpg',
         action="document.querySelector('.tab[data-view=\"text\"]').click();"
                "document.querySelector('#nodeList .item[data-id=\"complexType:CatalogType\"]').click();"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'highlighted': "document.querySelectorAll('#text .line.hl').length",
                 'view': "document.querySelector('.tab.active').dataset.view"},
         expect={'highlighted': 1, 'view': 'text'}),
    dict(name='doc-compare', file='samples/compare/v1.xsdviewer.json', theme='light', size=DOC_SIZE, doc='XsdViewer-compare-view.jpg',
         action=OPEN_V2 + "document.querySelector('#compareTable .crow.different').click();"
                "await new Promise(r => setTimeout(r, 300));"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'sides': "[...document.querySelectorAll('#compareTable .crow .cpath')].map(t => t.textContent).join('|')",
                 'detail': "document.querySelectorAll('#compareTable .cdetail').length"},
         expect={'sides': 'catalog.xsd|catalog.xsd|common.xsd|common.xsd|product.xsd|product.xsd||shipping.xsd|supplier.xsd|',
                 'detail': 1}),   # the file name, never the path (these pictures are published)
]


class Proxy(http.server.BaseHTTPRequestHandler):
    scene = None
    results = {}

    def do_GET(self):
        self.proxy()

    def do_POST(self):
        if self.path == '/__check':
            body = self.rfile.read(int(self.headers.get('Content-Length') or 0))
            Proxy.results[Proxy.scene['name']] = json.loads(body.decode('utf-8'))
            self.send_response(204); self.end_headers()
            return
        self.proxy()

    def proxy(self):
        if self.path == '/__hold':
            time.sleep(HOLD_SECONDS); self.send_response(204); self.end_headers()
            return
        if self.path.startswith('/__sample/'):
            self.sample(self.path[len('/__sample/'):])
            return
        length = int(self.headers.get('Content-Length') or 0)
        body = self.rfile.read(length) if length else None
        c = http.client.HTTPConnection('127.0.0.1', APP_PORT)
        headers = {k: v for k, v in self.headers.items() if k.lower() not in ('host', 'accept-encoding')}
        c.request(self.command, self.path, body=body, headers=headers)
        r = c.getresponse(); data = r.read()
        if self.path == '/' or self.path.startswith('/index.html'):
            data = data.replace(b'<head>', self.head(), 1).replace(b'</body>', self.tail() + b'</body>')
        self.send_response(r.status)
        for k, v in r.getheaders():
            if k.lower() not in ('content-length', 'transfer-encoding', 'content-encoding'):
                self.send_header(k, v)
        self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)

    def sample(self, rel):
        """A file of the repository (under samples/), for a scene opening files the page cannot read from disk."""
        f = (ROOT / rel).resolve()
        if not rel.startswith('samples/') or not f.is_relative_to(ROOT / 'samples') or not f.is_file():
            self.send_response(404); self.end_headers()
            return
        data = f.read_bytes()
        self.send_response(200); self.send_header('Content-Type', 'application/octet-stream')
        self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)

    @staticmethod
    def head():
        # a clean storage for every scene (the profile is shared): only the theme and the language (English, so that the texts checked do not depend on the machine) are set
        return ("<head><script>try{localStorage.clear();localStorage.setItem('xsdviewer.theme','%s');localStorage.setItem('xsdviewer.language','en')}catch(e){}</script>" % Proxy.scene['theme']).encode()

    @staticmethod
    def tail():
        s = Proxy.scene
        checks = ','.join('%s: (() => { try { return %s; } catch (e) { return "error: " + e.message; } })()' % (json.dumps(k), v) for k, v in s['checks'].items())
        return ('<script>setTimeout(() => (async () => { %s })().catch(e => console.error(e)).then(() =>'
                ' setTimeout(() => fetch("/__check", {method: "POST", body: JSON.stringify({%s})}), 300)), %d);</script>'
                '<img src="/__hold" style="display:none">' % (s['action'], checks, ACTION_DELAY_MS)).encode()

    def log_message(self, *a):
        pass


def setup_listed():
    """A workspace of more than ten files, the import sample first: the others stay listed, parsed in the background."""
    d = OUT / 'listed'
    d.mkdir(parents=True, exist_ok=True)
    for f in (ROOT / 'samples' / 'import').glob('*.xsd'):
        shutil.copy(f, d / f.name)
    names = ['order.xsd', 'address.xsd', 'items.xsd', 'types.xsd']
    for i in range(8):
        name = 'filler%d.xsd' % i
        (d / name).write_text('<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:filler:%d"><xs:element name="f%d" type="xs:string"/></xs:schema>' % (i, i))
        names.append(name)
    (d / 'listed.xsdviewer.json').write_text(json.dumps({'xsdviewer': 1, 'files': names, 'active': 0}))


SETUPS = {'listed': setup_listed}


def wait_for(port, seconds=15):
    for _ in range(seconds * 10):
        try:
            urllib.request.urlopen('http://127.0.0.1:%d/' % port, timeout=1)
            return True
        except Exception:
            time.sleep(0.1)
    return False


def shoot(scene, profile):
    if scene.get('setup'):
        SETUPS[scene['setup']]()
    app = subprocess.Popen(['java', '-jar', str(JAR), '--no-browser', '--keep-alive', '--port', str(APP_PORT), str(ROOT / scene['file'])],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        if not wait_for(APP_PORT):
            return 'the server did not start'
        Proxy.scene = scene
        png = OUT / (scene['name'] + '.png')
        r = subprocess.run([FIREFOX, '--headless', '--no-remote', '--profile', str(profile), '--window-size=' + scene.get('size', SIZE),
                            '--screenshot', str(png), 'http://localhost:%d/' % PROXY_PORT],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
        if r.returncode != 0 or not png.exists():
            return 'no screenshot (firefox exit %d)' % r.returncode
        got = Proxy.results.get(scene['name'])
        if got is None:
            return 'the scene\'s script did not report (its action probably failed)'
        wrong = {k: (v, got.get(k)) for k, v in scene['expect'].items() if got.get(k) != v}
        return 'checks failed: ' + ', '.join('%s expected %r got %r' % (k, e, g) for k, (e, g) in wrong.items()) if wrong else None
    finally:
        app.terminate()
        try:
            app.wait(timeout=10)
        except subprocess.TimeoutExpired:
            app.kill()


def publish(scene):
    """The shot of a scene of the README, saved as a JPEG in screenshots/ (what the GitHub page shows)."""
    from PIL import Image
    DOCS.mkdir(parents=True, exist_ok=True)
    with Image.open(OUT / (scene['name'] + '.png')) as im:
        im.convert('RGB').save(DOCS / scene['doc'], 'JPEG', quality=88, optimize=True, progressive=True)
    return DOCS / scene['doc']


def main():
    keep_going = '--keep-going' in sys.argv
    docs = '--docs' in sys.argv
    only = next((a.split('=', 1)[1].split(',') for a in sys.argv if a.startswith('--only=')), None)
    scenes = [s for s in SCENES if (not docs or s.get('doc')) and (only is None or s['name'] in only)]
    if not scenes:
        sys.exit('no scene selected')
    if not JAR.exists():
        sys.exit('%s missing: run mvn package first' % JAR)
    if not shutil.which(FIREFOX):
        sys.exit('firefox not found (set FIREFOX)')
    OUT.mkdir(parents=True, exist_ok=True)
    server = http.server.ThreadingHTTPServer(('127.0.0.1', PROXY_PORT), Proxy)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    failed = 0
    with tempfile.TemporaryDirectory(dir=ROOT / 'target') as profile:   # Firefox as a snap cannot write under /tmp
        for scene in scenes:
            problem = shoot(scene, profile)
            saved = '' if problem or not (docs and scene.get('doc')) else ' -> ' + str(publish(scene).relative_to(ROOT))
            print('%-16s %s' % (scene['name'], ('ok ' + str(Proxy.results.get(scene['name']))) + saved if problem is None else 'FAILED - ' + problem))
            if problem:
                failed += 1
                if not keep_going:
                    break
    server.shutdown()
    print('%d scene(s) failed; screenshots in %s' % (failed, OUT) if failed else 'all scenes ok; screenshots in %s' % OUT)
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
