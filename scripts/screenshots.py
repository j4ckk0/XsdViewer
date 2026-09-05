#!/usr/bin/env python3
"""
A visual smoke test of the page: opens the samples in the built jar, drives the page (a selection,
a view, the theme), checks a few facts on it and saves a screenshot of each scene.

    scripts/screenshots.py                 # after mvn package: target/screenshots/*.png (the jar: app/target/xsdviewer.jar), checks on stdout
    scripts/screenshots.py --keep-going    # every scene even after a failed check
    scripts/screenshots.py --docs          # only the scenes of the README, saved as JPEG in screenshots/
    scripts/screenshots.py --only a,b      # only these scenes
    FIREFOX=/path/to/firefox scripts/screenshots.py

Needs Firefox (its headless --screenshot) and the jar in app/target/. The page is reached through a
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
JAR = ROOT / 'app' / 'target' / 'xsdviewer.jar'
OUT = ROOT / 'target' / 'screenshots'
DOCS = ROOT / 'screenshots'   # the pictures of the README, written by --docs from the scenes carrying a "doc" name
FIREFOX = os.environ.get('FIREFOX', 'firefox')
APP_PORT, PROXY_PORT = 8765, 8766
SIZE = '1500,800'
DOC_SIZE = '1920,1048'    # the pictures of the README: a window wide enough for the graph's second level
HOLD_SECONDS = 6          # the longest the load event is held: it is released as soon as the scene has posted its checks
ACTION_DELAY_MS = 1500    # the longest the scene's script waits for the page to have drawn its file before it runs

# a second workspace, "v2" of the comparison sample, opened as if a folder had been dropped, and both
# workspaces selected: what the Files section of the comparison compares
OPEN_V2 = ("const names = ['common.xsd', 'catalog.xsd', 'product.xsd', 'shipping.xsd'];"
           "const files = await Promise.all(names.map(async n => new File([await (await fetch('/__sample/samples/compare/v2/' + n)).text()], n)));"
           "const wa = await import('/js/workspace-actions.js'), st = await import('/js/state.js'), cmp = await import('/js/workspace-selection.js'), pg = await import('/js/page.js');"
           "await wa.openBrowserFolder(files, f => 'v2/' + f.name, 'v2');"
           "for (const ws of st.session.workspaces) cmp.toggleSelection(ws);"
           "pg.renderPage();")

# the Model view draws once the server has answered: a scene clicking into it waits for the canvas to stop loading
MODEL_DRAWN = "await new Promise(r => { const c = document.getElementById('modelCanvas'); const tick = () => (c.dataset.loading ? setTimeout(tick, 20) : r()); setTimeout(tick, 20); });"
# the graph asks for the model of the selection when it is not at hand, and draws itself again once it comes
GRAPH_DRAWN = "await new Promise(r => { const c = document.getElementById('graphCanvas'); const tick = () => (c.dataset.loading ? setTimeout(tick, 20) : r()); setTimeout(tick, 20); });"

# the comparison is a place of its own, opened from the bar; it holds two sections
OPEN_COMPARISON = "document.getElementById('compareBtn').click();"
DECLARATIONS = OPEN_COMPARISON + "document.querySelector('#comparisonSections [data-section=\"objects\"]').click();"
FILES = OPEN_COMPARISON + "document.querySelector('#comparisonSections [data-section=\"files\"]').click();"

# name, file, theme, the script run on the page (may use the page's DOM and await), the checks (an expression per check name)
SCENES = [
    dict(name='first-launch', file=None, theme='light',
         # nothing open: the page says what to do, and About says what a bug report needs
         action="document.getElementById('helpMenuBtn').click(); document.getElementById('menuAbout').click();"
                "await new Promise(r => setTimeout(r, 200));"
                "const caps = await (await fetch('/api/capabilities')).json();"
                "window.__aboutVersion = document.getElementById('aboutVersion').textContent.includes(caps.version);"
                "window.__aboutLog = document.getElementById('aboutLog').textContent.length > 0;"
                "window.__aboutJava = document.getElementById('aboutJava').textContent.includes(caps.javaVersion);",
         checks={'empty': "!document.getElementById('empty').classList.contains('hidden')",
                 'title': "document.querySelector('#empty h2, #empty h1, #empty .title, #empty [data-i18n=\"empty.title\"]').textContent",
                 'sidebar': "!document.getElementById('sidebar').classList.contains('hidden')",
                 'details': "document.getElementById('details').classList.contains('hidden')",
                 'aboutOpen': "document.getElementById('aboutDialog').open",
                 'aboutVersion': "window.__aboutVersion", 'aboutJava': "window.__aboutJava", 'aboutLog': "window.__aboutLog"},
         expect={'empty': True, 'title': 'Open a schema', 'sidebar': True, 'details': True,
                 'aboutOpen': True, 'aboutVersion': True, 'aboutJava': True, 'aboutLog': True}),
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
                 'handles': 1, 'cards': '0..*|0..1|0..1|0..1', 'legend': '3:25', 'svgButton': False}),   # ItemExtras (a group) opens on demand; comment refers to a global element of a built-in type: nothing inside
    # what the graph knows, drawn on the model's boxes: comment is used by two objects; the declared ones carry a handle to the graph
    dict(name='model-shared', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"complexType:Items\"]').click();"
                "document.querySelector('.tab[data-view=\"model\"]').click();" + MODEL_DRAWN,
         checks={'shared': "[...document.querySelectorAll('#modelCanvas .mshared')].map(t => t.closest('.mbox').dataset.id + '=' + t.textContent).join('|')",
                 'toGraph': "[...document.querySelectorAll('#modelCanvas .mgraph')].map(h => h.closest('.mbox').dataset.id).join('|')",
                 'sharedTitle': "document.querySelector('#modelCanvas .mbox[data-id=\"element:comment\"] > title').textContent"},
         expect={'shared': 'element:comment=×3', 'toGraph': 'element:comment|group:ItemExtras',
                 'sharedTitle': 'element comment — 0..1 — comment — Used by 3 objects: changing it changes them all'}),
    # the ◎ handle of a box: the graph, centred on what the box stands for
    dict(name='model-to-graph', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"complexType:Items\"]').click();"
                "document.querySelector('.tab[data-view=\"model\"]').click();" + MODEL_DRAWN
                + "document.querySelector('#modelCanvas .mbox[data-id=\"element:comment\"] .mgraph').dispatchEvent(new MouseEvent('click', {bubbles: true}));" + GRAPH_DRAWN,
         checks={'view': "document.querySelector('#viewTabs .tab.active').dataset.view",
                 'centre': "document.querySelector('#graphCanvas .node.center').dataset.id",
                 'nodes': "document.querySelectorAll('#graphCanvas .node').length + ':' + document.querySelectorAll('#graphCanvas .nmodel').length",
                 'legend': "!!document.querySelector('#graphLegend .handle') && !!document.querySelector('#graphLegend .inmodel')"},
         expect={'view': 'graph', 'centre': 'element:comment', 'nodes': '5:4', 'legend': True}),
    # the ▤ handle of a node: its model
    dict(name='graph-to-model', file='samples/purchaseOrder.xsd', theme='dark',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();"
                "document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();" + GRAPH_DRAWN
                + "window.__loadingAfterGraph = 'loading' in document.getElementById('graphCanvas').dataset;"
                "document.querySelector('#graphCanvas .node[data-id=\"complexType:Items\"] .nmodel').dispatchEvent(new MouseEvent('click', {bubbles: true}));" + MODEL_DRAWN,
         checks={'view': "document.querySelector('#viewTabs .tab.active').dataset.view",
                 'root': "document.querySelector('#modelCanvas .mbox.center .mname').textContent",
                 'loadingAfterGraph': "window.__loadingAfterGraph"},
         expect={'view': 'model', 'root': 'Items', 'loadingAfterGraph': False}),
    # the model's footprint on the graph: the type opened in the model is tinted on the map
    dict(name='graph-footprint', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();"
                "document.querySelector('.tab[data-view=\"model\"]').click();" + MODEL_DRAWN
                + "document.querySelector('#modelCanvas .mbox[data-id=\"complexType:Items\"] .mhandle').dispatchEvent(new MouseEvent('click', {bubbles: true}));" + MODEL_DRAWN
                + "document.querySelector('.tab[data-view=\"graph\"]').click();" + GRAPH_DRAWN,
         checks={'inModel': "[...document.querySelectorAll('#graphCanvas .node.in-model')].map(g => g.dataset.id).join('|')",
                 'title': "document.querySelector('#graphCanvas .node.in-model > title').textContent.split('\\n')[1]",
                 'loading': "'loading' in document.getElementById('graphCanvas').dataset"},
         expect={'inModel': 'complexType:Items', 'title': 'Opened in the model of the selected object: a document of it goes through this one', 'loading': False}),
    # the details panel points at the two other views of the object
    dict(name='details-views', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"complexType:Items\"]').click();"
                "document.querySelector('#detailsContent a[data-view=\"graph\"]').click();" + GRAPH_DRAWN,
         checks={'links': "[...document.querySelectorAll('#detailsContent .meta a')].map(a => a.textContent).join('|')",
                 'view': "document.querySelector('#viewTabs .tab.active').dataset.view"},
         expect={'links': 'line 50 → show in text|model|graph', 'view': 'graph'}),
    # the exports, from a dark page: a file for each, well-formed, carrying the page's theme so its palette matches its background, and without the page's handles
    dict(name='exports', file='samples/purchaseOrder.xsd', theme='dark',
         action="window.__err = ''; window.addEventListener('error', e => { window.__err += e.message + ' @' + e.filename.split('/').pop() + ':' + e.lineno + ' | '; });"
                "window.__blobs = []; window.__origUrl = URL.createObjectURL.bind(URL); URL.createObjectURL = (bl) => { window.__blobs.push(bl); return window.__origUrl(bl); };"
                "HTMLAnchorElement.prototype.click = function () { window.__downloads = (window.__downloads || []).concat(this.download); };"
                "document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();" + GRAPH_DRAWN
                + "document.getElementById('exportSvgBtn').click(); await new Promise(r => setTimeout(r, 200)); window.__svg = await window.__blobs[0].text();"
                "document.getElementById('exportBtn').click(); await new Promise(r => setTimeout(r, 900));"
                "document.querySelector('.tab[data-view=\"model\"]').click();" + MODEL_DRAWN
                + "document.getElementById('exportSvgBtn').click(); await new Promise(r => setTimeout(r, 200)); window.__svgModel = await window.__blobs[window.__blobs.length - 1].text();"
                "document.getElementById('exportBtn').click(); await new Promise(r => setTimeout(r, 900));",
         checks={'err': "window.__err", 'downloads': "(window.__downloads || []).join('|')",
                 'wellFormed': "['__svg', '__svgModel'].map(k => new DOMParser().parseFromString(window[k], 'image/svg+xml').querySelector('parsererror') ? 'broken' : 'ok').join('|')",
                 'theme': "['__svg', '__svgModel'].map(k => new DOMParser().parseFromString(window[k], 'image/svg+xml').documentElement.dataset.theme).join('|')",
                 'background': "new DOMParser().parseFromString(window.__svg, 'image/svg+xml').querySelector('rect').getAttribute('fill')",
                 'handles': "['__svg','__svgModel'].map(k => new DOMParser().parseFromString(window[k], 'image/svg+xml').querySelectorAll('.nmodel, .mgraph').length).join(':')",
                 'pngBlobs': "window.__blobs.filter(bl => bl.type === 'image/png').length"},
         expect={'err': '', 'downloads': 'purchaseOrder-PurchaseOrderType.svg|purchaseOrder-PurchaseOrderType.png|purchaseOrder-PurchaseOrderType-model.svg|purchaseOrder-PurchaseOrderType-model.png',
                 'wellFormed': 'ok|ok', 'theme': 'dark|dark', 'background': '#0f1216', 'handles': '0:0', 'pngBlobs': 2}),
    # Graph -> Model keeps the node the keyboard rests on: the box standing for it is brought into view and marked
    # (headless Firefox fires no real focus event, so the scene dispatches focusin as a browser would)
    dict(name='graph-to-model-focus', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();" + GRAPH_DRAWN
                + "const gn = document.querySelector('#graphCanvas .node[data-id=\"complexType:USAddress\"]'); gn.focus(); gn.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));"
                "document.querySelector('.tab[data-view=\"model\"]').click();" + MODEL_DRAWN,
         checks={'aimed': "[...document.querySelectorAll('#modelCanvas .mbox.aimed')].map(b => b.dataset.id).join('|')",
                 'view': "document.querySelector('#viewTabs .tab.active').dataset.view"},
         expect={'aimed': 'complexType:USAddress', 'view': 'model'}),
    # a level-2 node lies under a box not opened yet: the box above it is opened first, then the aim is met
    dict(name='graph-to-model-focus-deep', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();"
                "document.getElementById('twoLevels').click();" + GRAPH_DRAWN
                + "const dn = document.querySelector('#graphCanvas .node[data-id=\"simpleType:SKU\"]'); dn.focus(); dn.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));"
                "document.querySelector('.tab[data-view=\"model\"]').click();" + MODEL_DRAWN + MODEL_DRAWN,
         checks={'aimedIsSku': "!!document.querySelector('#modelCanvas .mbox.aimed[data-id=\"simpleType:SKU\"]')",
                 'itemsOpened': "document.querySelector('#modelCanvas .mbox[data-id=\"complexType:Items\"] .mhandle text') ? document.querySelector('#modelCanvas .mbox[data-id=\"complexType:Items\"] .mhandle text').textContent : 'no items box'"},
         expect={'aimedIsSku': True, 'itemsOpened': '\u2212'}),
    # the Help menu: its two dialog openers, its two external links, and About
    dict(name='help-menu', file='samples/purchaseOrder.xsd', theme='light',
         action="document.getElementById('helpMenuBtn').click();",
         checks={'items': "[...document.querySelectorAll('#helpMenu > button, #helpMenu > a')].map(e => e.id).join('|')",
                 'docsHref': "document.getElementById('menuDocs').getAttribute('href')",
                 'issueHref': "document.getElementById('menuIssue').getAttribute('href')",
                 'seps': "document.querySelectorAll('#helpMenu .sep').length"},
         expect={'items': 'menuGuide|menuShortcuts|menuDocs|menuIssue|menuAbout',
                 'docsHref': 'https://github.com/j4ckk0/XsdViewer#readme',
                 'issueHref': 'https://github.com/j4ckk0/XsdViewer/issues', 'seps': 2}),
    # the user guide: its heading, its sections built from the translated texts, and it closes
    dict(name='help-guide', file='samples/purchaseOrder.xsd', theme='light',
         action="document.getElementById('helpMenuBtn').click(); document.getElementById('menuGuide').click();",
         checks={'open': "String(document.getElementById('guideDialog').open)",
                 'title': "document.querySelector('#guideDialog h2').textContent",
                 'sections': "[...document.querySelectorAll('#guideBody h3')].map(h => h.textContent).join('|')",
                 'paras': "document.querySelectorAll('#guideBody p').length"},
         expect={'open': 'true', 'title': 'User guide',
                 'sections': 'The three views of a file|Workspaces|Comparing|Validating a document|Getting around', 'paras': 6}),
    # the keyboard shortcuts: a row per shortcut, the keystroke then what it does
    dict(name='help-shortcuts', file='samples/purchaseOrder.xsd', theme='dark',
         action="document.getElementById('helpMenuBtn').click(); document.getElementById('menuShortcuts').click();",
         checks={'open': "String(document.getElementById('shortcutsDialog').open)",
                 'rows': "document.querySelectorAll('#shortcutsBody table.shortcuts tr').length",
                 'firstKey': "document.querySelector('#shortcutsBody td.keys').textContent",
                 'back': "[...document.querySelectorAll('#shortcutsBody tr')].find(r => r.querySelector('.keys').textContent.includes('Alt')).cells[1].textContent",
                 'clickKey': "[...document.querySelectorAll('#shortcutsBody td.keys')].map(c => c.textContent).find(k => k.startsWith('Ctrl + c'))"},
         expect={'open': 'true', 'rows': 10, 'firstKey': 'Ctrl + O', 'back': 'Back to the declaration selected before', 'clickKey': 'Ctrl + click'}),
    # the Compare group sits above the declaration details, so it stays in view
    dict(name='compare-group-above', file='samples/compare/v1.xsdviewer.json', theme='light',
         action=OPEN_V2
                + "[...document.querySelectorAll('#workspaces .wsgroup:not(.cmpchip)')].find(x => x.textContent.includes('v1')).click();"
                "[...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();",
         checks={'order': "(() => { const kids = [...document.getElementById('details').children].map(c => c.id); return kids.indexOf('compareGroup') < kids.indexOf('detailsContent') ? 'compare-above' : 'compare-below'; })()",
                 'groupShown': "!document.getElementById('compareGroup').classList.contains('hidden')"},
         expect={'order': 'compare-above', 'groupShown': True}),
    # the compare view exports three pictures: the left declaration, the right, then both — for PNG and for SVG
    dict(name='compare-export-three', file='samples/compare/v1.xsdviewer.json', theme='light',
         action=OPEN_V2 + DECLARATIONS
                + "window.__downloads = []; window.__blobs = [];"
                "window.__origUrl = URL.createObjectURL.bind(URL); URL.createObjectURL = (bl) => { window.__blobs.push(bl); return window.__origUrl(bl); };"
                "HTMLAnchorElement.prototype.click = function () { window.__downloads.push(this.download); };"
                "const pick = (w, side) => {"
                "  [...document.querySelectorAll('#workspaces .wsgroup')].find(x => x.textContent.includes(w)).click();"
                "  [...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "  document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "  document.querySelector('#compareSides .cobj-mark.' + side).click(); };"
                "pick('v1', 'left'); pick('v2', 'right');" + DECLARATIONS
                + "await new Promise(res => { const t = () => (document.querySelectorAll('#objectCompareBody .mbox').length ? res() : setTimeout(t, 40)); t(); });"
                "document.getElementById('exportSvgBtn').click(); await new Promise(r => setTimeout(r, 400));"
                "window.__svgFiles = window.__downloads.slice();"
                "window.__svgWellFormed = window.__blobs.map(bl => bl).length && (await Promise.all(window.__blobs.map(b => b.text()))).every(x => !new DOMParser().parseFromString(x, 'image/svg+xml').querySelector('parsererror'));"
                "window.__blobs = []; window.__downloads = [];"
                "document.getElementById('exportBtn').click(); await new Promise(r => setTimeout(r, 2500));"
                "window.__pngFiles = window.__downloads.slice();"
                "window.__pngCount = window.__blobs.filter(bl => bl.type === 'image/png').length;",
         checks={'svgFiles': "window.__svgFiles.join('|')",
                 'svgWellFormed': "String(window.__svgWellFormed)",
                 'pngFiles': "window.__pngFiles.join('|')",
                 'pngCount': "window.__pngCount"},
         expect={'svgFiles': 'ProductType-compared-left.svg|ProductType-compared-right.svg|ProductType-ProductType-compared.svg',
                 'svgWellFormed': 'true',
                 'pngFiles': 'ProductType-compared-left.png|ProductType-compared-right.png|ProductType-ProductType-compared.png',
                 'pngCount': 3}),
    # Stop halts a folder being parsed in the background (a large folder opened by mistake): the files not yet reached stay listed, unparsed
    dict(name='stop-loading', file='samples/purchaseOrder.xsd', theme='light',
         action="const filler = (i) => '<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:f:' + i + '\"><xs:element name=\"f' + i + '\" type=\"xs:string\"/></xs:schema>';"
                "const files = []; for (let i = 0; i < 800; i++) files.push(new File([filler(i)], 'filler' + i + '.xsd'));"
                "const wa = await import('/js/workspace-actions.js');"
                "await wa.openBrowserFolder(files, f => 'many/' + f.name, 'many');"
                # the background parse is running: its Stop is offered — stop it before the queue drains
                "const stop = document.getElementById('busyStop'); let seen = false;"
                "for (let i = 0; i < 300; i++) { if (!stop.classList.contains('hidden')) { seen = true; break; } await new Promise(r => setTimeout(r, 10)); }"
                "window.__seen = seen; stop.click();"
                "await new Promise(r => setTimeout(r, 700));"
                "const st = await import('/js/state.js'); const ws = st.session.workspaces.slice(-1)[0];"
                "window.__unparsed = ws.files.filter(f => !f.model && !f.failed).length;",
         checks={'stopOffered': "String(window.__seen)",
                 'someLeftUnparsed': "String(window.__unparsed > 0)",
                 'toast': "document.getElementById('toast').textContent.trim()",
                 'stopHiddenAfter': "String(document.getElementById('busyStop').classList.contains('hidden'))"},
         expect={'stopOffered': 'true', 'someLeftUnparsed': 'true', 'toast': 'Loading stopped', 'stopHiddenAfter': 'true'}),
    # long declaration names: each name fills its own line up close to the box edge, the type on the line below
    dict(name='model-long-names', file='samples/longnames.xsd', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"complexType:InternationalPurchaseOrderConfirmationType\"]').click();"
                "document.querySelector('.tab[data-view=\"model\"]').click();" + MODEL_DRAWN,
         checks={'names': "[...document.querySelectorAll('#modelCanvas .mbox .mname')].map(t => t.textContent).join('|')",
                 'boxHeight': "document.querySelector('#modelCanvas .mbox rect').getAttribute('height')",
                 # a built-in-typed element carries no handle, so its name reaches further before the ellipsis
                 'stringTypedName': "[...document.querySelectorAll('#modelCanvas .mbox .mname')].map(t => t.textContent).find(n => n.startsWith('customerLoyalty'))"},
         expect={'names': 'InternationalPurchaseOrde…|@purchaseOrderConfirmationReferenceNumber : string|customerLoyaltyProgramMem…|preferredInternation…|alternativeBillingAd…|consolidatedOrderLin…|estimatedDeliveryDateWith…',
                 'boxHeight': '40', 'stringTypedName': 'customerLoyaltyProgramMem…'}),
    # Settings toggle: the cross-view handles (the model's ◎ and the graph's ▤, and the ×N mark) can be hidden
    dict(name='handles-toggle', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"complexType:Items\"]').click();"
                "document.querySelector('.tab[data-view=\"model\"]').click();" + MODEL_DRAWN
                + "window.__before = document.querySelectorAll('#modelCanvas .mgraph').length;"
                "document.getElementById('settingsMenuBtn').click(); document.getElementById('menuHandles').click();" + MODEL_DRAWN
                + "window.__after = document.querySelectorAll('#modelCanvas .mgraph').length;"
                "window.__checkedOff = !document.getElementById('menuHandles').classList.contains('checked');"
                "document.getElementById('settingsMenuBtn').click(); document.getElementById('menuHandles').click();" + MODEL_DRAWN
                + "window.__back = document.querySelectorAll('#modelCanvas .mgraph').length;",
         checks={'before': "String(window.__before > 0)", 'after': "String(window.__after)", 'checkedOff': "String(window.__checkedOff)", 'back': "String(window.__back > 0)"},
         expect={'before': 'true', 'after': '0', 'checkedOff': 'true', 'back': 'true'}),
    # the search field's clear cross: hidden while empty, shown once there is a filter, and it clears it
    dict(name='search-clear', file='samples/purchaseOrder.xsd', theme='light',
         action="const s = document.getElementById('search'); const x = document.getElementById('searchClear');"
                "window.__emptyHidden = getComputedStyle(x).display === 'none';"
                "s.value = 'Address'; s.dispatchEvent(new Event('input', {bubbles: true})); await new Promise(r => setTimeout(r, 200));"
                "window.__shown = getComputedStyle(x).display !== 'none';"
                "x.click(); await new Promise(r => setTimeout(r, 200));"
                "window.__cleared = s.value === '' && getComputedStyle(x).display === 'none';",
         checks={'emptyHidden': "String(window.__emptyHidden)", 'shownWithText': "String(window.__shown)", 'clearsIt': "String(window.__cleared)"},
         expect={'emptyHidden': 'true', 'shownWithText': 'true', 'clearsIt': 'true'}),
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
                "document.querySelector('.tab[data-view=\"model\"]').click();" + MODEL_DRAWN
                + "document.querySelector('#modelCanvas .mhandle').dispatchEvent(new MouseEvent('click', { bubbles: true }));" + MODEL_DRAWN,
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
                "const f = document.getElementById('textFindInput'); f.value = 'xs:attribute '; f.dispatchEvent(new Event('input', {bubbles: true}));"
                # ⤓ PNG of the source: painted line by line onto a canvas, caught before it reaches the disk
                "const exports = await import('/js/png-export.js');"
                "let saved = null; const makeUrl = URL.createObjectURL, click = HTMLAnchorElement.prototype.click;"
                "URL.createObjectURL = (b) => { saved = b; return makeUrl(b); };"
                "HTMLAnchorElement.prototype.click = function () {};"
                "exports.exportPng();"
                "await new Promise(r => setTimeout(r, 400));"
                "HTMLAnchorElement.prototype.click = click; URL.createObjectURL = makeUrl;"
                "window.__png = saved ? saved.type + '/' + (saved.size > 10000) : 'nothing';",
         checks={'count': "document.getElementById('textFindCount').textContent",
                 'current': "document.querySelectorAll('#text .line.found.current').length",
                 'svgButton': "document.getElementById('exportSvgBtn').disabled",
                 'png': "window.__png"},
         expect={'count': '1/7', 'current': 1, 'svgButton': True, 'png': 'image/png/true'}),
    dict(name='listed-files', file='target/screenshots/listed/listed.xsdviewer.json', theme='light', setup='listed',
         # the listed files are parsed in the background: the graph resolves its targets from them once they are
         action="const st = await import('/js/state.js'); const ws = st.session.active.workspace;"
                "for (let i = 0; i < 40 && ws.files.some(f => !f.model && !f.failed); i++) await new Promise(r => setTimeout(r, 100));"
                "document.querySelector('.tab[data-view=\"graph\"]').click();document.querySelector('#nodeList .item[data-id=\"complexType:OrderType\"]').click();",
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
                "const fl = await import('/js/file-list.js'); fl.filesPanel.set(true);"   # folded: a search must still reach the other files
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
    dict(name='compare', file='samples/compare/v1.xsdviewer.json', theme='light', action=OPEN_V2 + FILES,
         checks={'rows': "document.querySelectorAll('#compareTable .crow').length",
                 'summary': "document.getElementById('compareSummary').textContent.slice(0, 30)",
                 'section': "document.querySelector('#comparisonSections .csection-tab.active').textContent",
                 'chip': "document.querySelectorAll('#workspaces .cmpchip.active').length",
                 'tabbar': "document.getElementById('tabbar').classList.contains('hidden')",
                 'viewTabs': "document.getElementById('viewTabs').classList.contains('hidden')",
                 # the right end of the bar stays against the edge although the views it holds are hidden here
                 'languageAtRight': "Math.round(document.getElementById('topbar').getBoundingClientRect().right"
                                    " - document.getElementById('language').getBoundingClientRect().right)"},
         expect={'rows': 5, 'summary': '5 files: 2 identical, 1 differ', 'section': 'Files', 'chip': 1,
                 'tabbar': True, 'viewTabs': True, 'languageAtRight': 12}),   # a place of its own: no file tabs, no views
    dict(name='compare-opens-on', file='samples/compare/v1.xsdviewer.json', theme='light',
         # the button opens the section the selection is ready for: two workspaces the files, else the objects
         action=OPEN_V2 + OPEN_COMPARISON
                + "const section = () => document.querySelector('#comparisonSections .csection-tab.active').textContent;"
                "window.__selected = section();"
                # its × takes the selection with it, so the next opening has none
                "document.querySelector('#workspaces .cmpchip .wsclose').click();"
                + OPEN_COMPARISON
                + "window.__none = section();",
         checks={'names': "[...document.querySelectorAll('#comparisonSections .csection-tab')].map(b => b.textContent).join('|')",
                 'twoSelected': "window.__selected", 'noneSelected': "window.__none"},
         expect={'names': 'Objects|Files', 'twoSelected': 'Files', 'noneSelected': 'Objects'}),
    dict(name='selection-in-panels', file='samples/purchaseOrder.xsd', theme='light',
         # a click in the Model view marks the object in the Files panel and in the object list
         action="document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();" + MODEL_DRAWN
                + "const box = [...document.querySelectorAll('#modelCanvas .mbox.clickable')].find(b => b.dataset.id === 'complexType:USAddress');"
                "box.dispatchEvent(new MouseEvent('click', { bubbles: true }));" + MODEL_DRAWN
                + "document.getElementById('toast').classList.add('hidden');",
         checks={'files': "[...document.querySelectorAll('#filesContent .obj.selected')].map(e => e.dataset.id).join('|')",
                 'objects': "[...document.querySelectorAll('#nodeList .item.selected')].map(e => e.dataset.id).join('|')",
                 'title': "document.getElementById('modelTitle').textContent"},
         expect={'files': 'complexType:USAddress', 'objects': 'complexType:USAddress',
                 'title': 'complexType USAddress'}),   # the same object marked in both panels, from a click in the drawing
    dict(name='zoom', file='samples/purchaseOrder.xsd', theme='light',
         # the drawn views scale in their panel: the SVG grows, its viewBox does not, the panel scrolls
         action="document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();" + MODEL_DRAWN
                + "const svg = () => document.querySelector('#modelCanvas svg');"
                "const size = () => svg().getAttribute('width') + 'x' + svg().getAttribute('height');"
                "window.__own = size(); const box = svg().getAttribute('viewBox');"
                "document.getElementById('zoomIn').click(); document.getElementById('zoomIn').click();"
                "window.__zoomed = size(); window.__boxKept = box === svg().getAttribute('viewBox');"
                "document.querySelector('.tab[data-view=\"text\"]').click();"
                "window.__hiddenInText = document.getElementById('zoomControls').classList.contains('hidden');"
                "document.querySelector('.tab[data-view=\"model\"]').click();"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'own': "window.__own",
                 'zoomed': "window.__zoomed",
                 'boxKept': "window.__boxKept",
                 'level': "document.getElementById('zoomLevel').textContent",
                 'shown': "!document.getElementById('zoomControls').classList.contains('hidden')",
                 'hiddenInText': "window.__hiddenInText"},
         expect={'own': '710x623', 'zoomed': '1065x935', 'boxKept': True, 'level': '150%',
                 'shown': True, 'hiddenInText': True}),   # 1.5 times the drawing's own size, the level kept across the views
    dict(name='model-back', file='samples/purchaseOrder.xsd', theme='light',
         # a click on a box selects what it refers to; ← Back returns, and neither leaves the Model view
         action="window.__atStart = document.getElementById('modelBackBtn').disabled;"
                "document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();" + MODEL_DRAWN
                + "const box = [...document.querySelectorAll('#modelCanvas .mbox.clickable')].find(b => b.dataset.id === 'complexType:USAddress');"
                "box.dispatchEvent(new MouseEvent('click', { bubbles: true }));" + MODEL_DRAWN
                + "window.__after = document.getElementById('modelTitle').textContent + ' | ' + document.querySelector('.tab.active').dataset.view;"
                "document.getElementById('modelBackBtn').click();" + MODEL_DRAWN,
         checks={'atStart': "window.__atStart",
                 'after': "window.__after",
                 'back': "document.getElementById('modelTitle').textContent + ' | ' + document.querySelector('.tab.active').dataset.view",
                 'disabled': "document.getElementById('modelBackBtn').disabled"},
         expect={'atStart': True, 'after': 'complexType USAddress | model', 'back': 'complexType PurchaseOrderType | model',
                 'disabled': False}),   # nothing to go back to at first; one step is left after this one, the file's own first selection
    dict(name='compare-object', file='samples/compare/v1.xsdviewer.json', theme='light',
         action=OPEN_V2 + DECLARATIONS
                + "window.__empty = document.getElementById('objectCompareEmpty').textContent.slice(0, 24);"
                # ProductType of v1 on the left, the one of v2 on the right: two workspaces, two files of the same name
                "const pick = (w, side) => {"
                "  [...document.querySelectorAll('#workspaces .wsgroup')].find(x => x.textContent.includes(w)).click();"
                "  [...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "  document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "  document.querySelector('#compareSides .cobj-mark.' + side).click(); };"
                "pick('v1', 'left'); pick('v2', 'right');"
                + DECLARATIONS
                + "await new Promise(r => setTimeout(r, 400));"
                "document.getElementById('toast').classList.add('hidden');"
                "const boxes = () => document.querySelectorAll('#objectCompareBody .mbox').length;"
                "const fold = (sel) => document.querySelector(sel).dispatchEvent(new MouseEvent('click', { bubbles: true }));"
                "window.__open = boxes();"
                "window.__marks = ['removed','added','changed'].map(c => document.querySelectorAll('#objectCompareBody .mbox.' + c).length).join('/');"
                # the sequence of the left model: folding it folds the one matching it on the right too
                "fold('#objectCompareLeft .mbox.sequence .mhandle');"
                "window.__folded = boxes();"
                "document.getElementById('objectCompareCollapseAll').click();"
                "window.__all = boxes();"
                "document.getElementById('objectCompareExpandAll').click();"
                # folded, then away to a workspace and back: the folds are what was left
                "fold('#objectCompareLeft .mbox.sequence .mhandle');"
                "[...document.querySelectorAll('#workspaces .wsgroup:not(.cmpchip)')][0].click();"
                "document.querySelector('#workspaces .cmpchip').click();"
                "await new Promise(r => setTimeout(r, 300));"
                "window.__kept = boxes();"
                "document.getElementById('objectCompareExpandAll').click();",
         checks={'title': "document.getElementById('objectCompareTitle').textContent",
                 'summary': "document.getElementById('objectCompareSummary').textContent",
                 'heads': "[...document.querySelectorAll('#objectCompareBody .cobj-head')].map(h => h.textContent).join('|')",
                 'marks': "window.__marks",
                 'boxesOpen': "window.__open", 'boxesFolded': "window.__folded", 'boxesAllFolded': "window.__all",
                 'kept': "window.__kept",
                 'reopened': "document.querySelectorAll('#objectCompareBody .mbox').length",
                 'guidance': "window.__empty"},
         expect={'title': 'complexType ProductType compared with complexType ProductType',
                 'summary': '1 only on the left, 3 only on the right, 3 changed',
                 'heads': 'complexType ProductType — product.xsd, v1|complexType ProductType — product.xsd, v2',
                 'marks': '1/3/6', 'boxesOpen': 22, 'boxesFolded': 8, 'boxesAllFolded': 2, 'kept': 8, 'reopened': 22,
                 'guidance': 'Put a declaration on eac'}),   # what only each side has, what changed, and the folding
    dict(name='compare-text', file='samples/compare/v1.xsdviewer.json', theme='light',
         # the comparison shows its two declarations as text: their source, line beside line
         action=OPEN_V2 + DECLARATIONS
                + "const pick = (w, side) => {"
                "  [...document.querySelectorAll('#workspaces .wsgroup:not(.cmpchip)')].find(x => x.textContent.includes(w)).click();"
                "  [...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "  document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "  document.querySelector('#compareSides .cobj-mark.' + side).click(); };"
                "pick('v1', 'left'); pick('v2', 'right');"
                + DECLARATIONS
                + "await new Promise(r => setTimeout(r, 400));"
                "document.getElementById('toast').classList.add('hidden');"
                "document.querySelector('#viewTabs .tab[data-view=\"text\"]').click();"
                "await new Promise(r => setTimeout(r, 300));",
         checks={'viewTabs': "document.getElementById('viewTabs').classList.contains('hidden')",
                 'active': "document.querySelector('#viewTabs .tab.active').textContent",
                 'summary': "document.getElementById('objectCompareSummary').textContent",
                 'chips': "document.querySelectorAll('#objectCompareLegend .lg').length",
                 'sides': "document.querySelectorAll('#objectCompareText .cside table.diff').length",
                 'rows': "document.querySelectorAll('#objectCompareText .cside:first-child tr').length",
                 'deleted': "document.querySelectorAll('#objectCompareText td.code.del').length",
                 'inserted': "document.querySelectorAll('#objectCompareText td.code.ins').length",
                 'firstLine': "document.querySelector('#objectCompareText .cside td.ln').textContent",
                 'folds': "document.getElementById('objectCompareFolds').classList.contains('hidden')",
                 'heads': "[...document.querySelectorAll('#objectCompareBody .cobj-head')].map(h => h.textContent).join('|')"},
         # ProductType spans lines 13-25 in both files; four of its lines differ (minOccurs of description,
         # legacyCode against weight, maxOccurs of tag, use of category)
         expect={'viewTabs': False, 'active': 'Text', 'summary': '4 lines only on the left, 4 only on the right', 'chips': 2, 'sides': 2, 'rows': 13, 'deleted': 4, 'inserted': 4,
                 'firstLine': '13', 'folds': True,
                 'heads': 'complexType ProductType — product.xsd, v1|complexType ProductType — product.xsd, v2'}),
    dict(name='compare-graph', file='samples/compare/v1.xsdviewer.json', theme='light',
         # the comparison shows the neighbourhood of each declaration, the links only one side has marked
         action=OPEN_V2 + DECLARATIONS
                + "const pick = (w, side) => {"
                "  [...document.querySelectorAll('#workspaces .wsgroup:not(.cmpchip)')].find(x => x.textContent.includes(w)).click();"
                "  [...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "  document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "  document.querySelector('#compareSides .cobj-mark.' + side).click(); };"
                "pick('v1', 'left'); pick('v2', 'right');"
                + DECLARATIONS
                + "await new Promise(r => setTimeout(r, 400));"
                "document.getElementById('toast').classList.add('hidden');"
                "const view = (v) => document.querySelector('#viewTabs .tab[data-view=\"' + v + '\"]').click();"
                "const count = (sel) => document.querySelectorAll(sel).length;"
                "view('graph');"
                "await new Promise(r => setTimeout(r, 300));"
                "window.__graphs = count('#objectCompareBody .cobj-canvas svg');"
                "window.__center = document.querySelector('#objectCompareLeft .node.center text').textContent;"
                "window.__onlyLeft = count('#objectCompareLeft .node.del');"
                "window.__onlyRight = count('#objectCompareRight .node.ins');"
                "window.__unmarked = count('#objectCompareLeft .node:not(.del):not(.center)');"
                "window.__summary = document.getElementById('objectCompareSummary').textContent;"
                "window.__chips = count('#objectCompareLegend .lg');"
                "window.__folds = document.getElementById('objectCompareFolds').classList.contains('hidden');"
                # and back to the models, which the comparison draws again as it did
                "view('model');"
                "await new Promise(r => setTimeout(r, 300));",
         checks={'active': "document.querySelector('#viewTabs .tab.active').textContent",
                 'graphs': "window.__graphs", 'leftCenter': "window.__center",
                 'onlyLeft': "window.__onlyLeft", 'onlyRight': "window.__onlyRight", 'unmarked': "window.__unmarked",
                 'summary': "window.__summary", 'chips': "window.__chips", 'folds': "window.__folds",
                 'boxesBack': "document.querySelectorAll('#objectCompareBody .mbox').length",
                 'textGone': "document.getElementById('objectCompareText').classList.contains('hidden')",
                 'foldsBack': "document.getElementById('objectCompareFolds').classList.contains('hidden')"},
         # the four links the other side does not have are the four the text view shows as changed lines:
         # description (optional against required), legacyCode, tag (0..* against 0..10), category (optional against required)
         expect={'active': 'Model', 'graphs': 2, 'leftCenter': 'ProductType',
                 'onlyLeft': 4, 'onlyRight': 4, 'unmarked': 5,
                 'summary': '4 links only on the left, 4 only on the right', 'chips': 2, 'folds': True,
                 'boxesBack': 22, 'textGone': True, 'foldsBack': False}),
    dict(name='compare-group', file='samples/purchaseOrder.xsd', theme='light',
         # the two sides and the comparison itself, a group of their own at the foot of the details panel
         action="document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();"
                "const group = () => document.getElementById('compareGroup');"
                "window.__shown = !group().classList.contains('hidden');"
                "window.__buttons = [...group().querySelectorAll('button')].map(b => b.textContent).join('|');"
                "document.getElementById('compareGroupToggle').click();"
                "window.__folded = group().classList.contains('collapsed');"
                "document.getElementById('compareGroupToggle').click();"
                # its Compare button opens the comparison, as the workspace bar's does
                "document.getElementById('detailsCompareBtn').click();"
                "await new Promise(r => setTimeout(r, 200));"
                "window.__chips = document.querySelectorAll('#workspaces .cmpchip').length;"
                "window.__place = document.querySelectorAll('#comparison:not(.hidden)').length;",
         checks={'shown': "window.__shown", 'buttons': "window.__buttons", 'folded': "window.__folded",
                 'chips': "window.__chips", 'place': "window.__place",
                 'unfolded': "document.getElementById('compareGroup').classList.contains('collapsed')",
                 'title': "document.querySelector('#compareGroup .panel-head span').textContent"},
         expect={'shown': True, 'buttons': '▾|◈ Left side|◈ Right side|⇄ Compare', 'folded': True, 'chips': 1, 'place': 1,
                 'unfolded': False, 'title': 'Compare'}),
    dict(name='compare-diff-only', file='samples/compare/v1.xsdviewer.json', theme='light',
         # differences only, in the three views: the boxes on the way to a difference, the changed lines with context, the marked links
         action=OPEN_V2 + DECLARATIONS
                + "const pick = (w, side) => {"
                "  [...document.querySelectorAll('#workspaces .wsgroup:not(.cmpchip)')].find(x => x.textContent.includes(w)).click();"
                "  [...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "  document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "  document.querySelector('#compareSides .cobj-mark.' + side).click(); };"
                "pick('v1', 'left'); pick('v2', 'right');"
                + DECLARATIONS
                + "await new Promise(r => setTimeout(r, 400));"
                "document.getElementById('toast').classList.add('hidden');"
                "const view = (v) => document.querySelector('#viewTabs .tab[data-view=\"' + v + '\"]').click();"
                "const count = (sel) => document.querySelectorAll(sel).length;"
                "const option = document.getElementById('objectCompareDiffOnly');"
                "const set = async (on) => { option.checked = on; option.dispatchEvent(new Event('change')); await new Promise(r => setTimeout(r, 300)); };"
                "window.__allBoxes = count('#objectCompareBody .mbox');"
                "await set(true);"
                "window.__diffBoxes = count('#objectCompareBody .mbox');"
                "window.__sameBoxes = count('#objectCompareBody .mbox.same');"
                "view('text'); await new Promise(r => setTimeout(r, 300));"
                "window.__textRows = count('#objectCompareText .cside:first-child tr');"
                "window.__textFolds = count('#objectCompareText .cside:first-child tr.fold');"
                "view('graph'); await new Promise(r => setTimeout(r, 300));"
                "window.__graphNodes = count('#objectCompareLeft .node');"
                "await set(false);"
                "window.__graphNodesAll = count('#objectCompareLeft .node');"
                "view('model'); await new Promise(r => setTimeout(r, 300));",
         checks={'allBoxes': "window.__allBoxes", 'diffBoxes': "window.__diffBoxes", 'sameBoxes': "window.__sameBoxes",
                 'textRows': "window.__textRows", 'textFolds': "window.__textFolds",
                 'graphNodes': "window.__graphNodes", 'graphNodesAll': "window.__graphNodesAll",
                 'boxesBack': "document.querySelectorAll('#objectCompareBody .mbox').length",
                 'remembered': "localStorage.getItem('xsdviewer.objectCompareDiffOnly')"},
         # of 22 boxes 14 remain, the 4 "same" ones being the two roots and their sequences, on the way to a difference;
         # lines 13-16 fold to one row with a line of context on each side; the left graph keeps its centre and the 4 marked links
         expect={'allBoxes': 22, 'diffBoxes': 14, 'sameBoxes': 4, 'textRows': 12, 'textFolds': 1,
                 'graphNodes': 5, 'graphNodesAll': 10, 'boxesBack': 22, 'remembered': '0'}),
    dict(name='compare-side-states', file='samples/compare/v1.xsdviewer.json', theme='light',
         # a side button in its three states: free, holding this object, holding another one
         action=OPEN_V2 + "window.addEventListener('error', e => { window.__err = e.message; });"
                "const buttons = () => [...document.querySelectorAll('#compareSides .cobj-mark')].map(b => b.className + '|' + b.title).join(' ~~ ');"
                "[...document.querySelectorAll('#workspaces .wsgroup:not(.cmpchip)')].find(x => x.textContent.includes('v1')).click();"
                "[...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "window.__free = buttons();"
                "document.querySelector('#compareSides .cobj-mark.left').click();"
                "window.__mine = buttons();"
                # another object of the same file — the product element: the left side now holds something else
                "document.querySelector('#nodeList .item[data-id=\"element:product\"]').click();"
                "window.__other = buttons();",
         checks={'free': "window.__free", 'mine': "window.__mine", 'other': "window.__other",
                 'takenIsClickable': "!document.querySelector('#compareSides .cobj-mark.taken').disabled"},
         expect={'free': 'cobj-mark left|Draw this object on the left side of the comparison'
                         ' ~~ cobj-mark right|Draw this object on the right side of the comparison',
                 'mine': 'cobj-mark left marked|The left side of the comparison holds this object: click to take it off'
                         ' ~~ cobj-mark right|Draw this object on the right side of the comparison',
                 # another object read: the side is marked as taken and its tooltip says by what, and it still takes a click
                 'other': 'cobj-mark left taken|The left side holds complexType ProductType — product.xsd: click to put this object there instead'
                          ' ~~ cobj-mark right|Draw this object on the right side of the comparison',
                 'takenIsClickable': True}),
    dict(name='compare-sides', file='samples/compare/v1.xsdviewer.json', theme='light',
         # each side is chosen: filling one, taking it off, clearing both, swapping
         action=OPEN_V2 + "const state = await import('/js/state.js');"
                "const pick = (w, id, side) => {"
                "  [...document.querySelectorAll('#workspaces .wsgroup:not(.cmpchip)')].find(x => x.textContent.includes(w)).click();"
                "  [...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "  document.querySelector('#nodeList .item[data-id=\"' + id + '\"]').click();"
                "  document.querySelector('#compareSides .cobj-mark.' + side).click(); };"
                "pick('v1', 'complexType:ProductType', 'left');"
                "window.__afterLeft = [!!state.session.compared.left, !!state.session.compared.right].join('/');"
                "pick('v2', 'complexType:ProductType', 'right');"
                + DECLARATIONS + "await new Promise(r => setTimeout(r, 300));"
                "const heads = () => [...document.querySelectorAll('#objectCompareBody .cobj-head')].map(h => h.textContent.split(', ').pop()).join('|');"
                "window.__heads = heads();"
                "document.getElementById('objectCompareSwap').click();"
                "window.__swapped = heads();"
                # the details panel belongs to a workspace: swapping or clearing must not bring it into the comparison
                "window.__detailsOnSwap = document.getElementById('details').classList.contains('hidden');"
                "document.getElementById('objectCompareSwap').click();"
                "document.getElementById('objectCompareClear').click();"
                "window.__detailsOnClear = document.getElementById('details').classList.contains('hidden');"
                "window.__cleared = [!!state.session.compared.left, !!state.session.compared.right].join('/');"
                "window.__hidden = document.getElementById('objectCompareBody').classList.contains('hidden');"
                # both sides filled again, and one taken off by clicking the side that holds it
                "pick('v1', 'complexType:ProductType', 'left');"
                "pick('v2', 'complexType:ProductType', 'right');"
                "document.querySelector('#compareSides .cobj-mark.right').click();"
                "window.__takenOff = [!!state.session.compared.left, !!state.session.compared.right].join('/');"
                + DECLARATIONS + "await new Promise(r => setTimeout(r, 200));"
                "window.__hiddenAgain = document.getElementById('objectCompareBody').classList.contains('hidden');"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'afterLeft': "window.__afterLeft", 'heads': "window.__heads", 'swapped': "window.__swapped",
                 'detailsOnSwap': "window.__detailsOnSwap", 'detailsOnClear': "window.__detailsOnClear",
                 'cleared': "window.__cleared", 'hidden': "window.__hidden",
                 'takenOff': "window.__takenOff", 'hiddenAgain': "window.__hiddenAgain"},
         expect={'afterLeft': 'true/false', 'heads': 'v1|v2', 'swapped': 'v2|v1',
                 'detailsOnSwap': True, 'detailsOnClear': True,
                 'cleared': 'false/false', 'hidden': True,
                 'takenOff': 'true/false', 'hiddenAgain': True}),   # one side empty: nothing is drawn
    dict(name='compare-two-objects', file='samples/compare/v1.xsdviewer.json', theme='light',
         # two declarations that have nothing to do with one another: different names, different files, one workspace
         action="document.querySelector('#nodeList .item[data-id=\"complexType:CatalogType\"]').click();"
                "document.querySelector('#compareSides .cobj-mark.left').click();"
                "[...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('supplier.xsd')).click();"
                "document.querySelector('#nodeList .item[data-id=\"complexType:SupplierType\"]').click();"
                "document.querySelector('#compareSides .cobj-mark.right').click();"
                + DECLARATIONS
                + "await new Promise(r => setTimeout(r, 400));"
                "document.getElementById('toast').classList.add('hidden');"
                # ⤓ SVG here writes one file holding both models: caught before it reaches the disk
                "const exports = await import('/js/png-export.js');"
                "let saved = null; const makeUrl = URL.createObjectURL, click = HTMLAnchorElement.prototype.click;"
                "URL.createObjectURL = (b) => { saved = b; return makeUrl(b); };"
                "HTMLAnchorElement.prototype.click = function () {};"
                "exports.exportSvg();"
                "HTMLAnchorElement.prototype.click = click; URL.createObjectURL = makeUrl;"
                "const svgText = saved ? await saved.text() : '';"
                "window.__svg = saved ? [saved.type.split(';')[0], svgText.includes('catalog.xsd, v1') && svgText.includes('supplier.xsd, v1'),"
                "  svgText.split('class=\"mbox').length - 1 > 20].join('|') : 'nothing';",
         checks={'title': "document.getElementById('objectCompareTitle').textContent",
                 'summary': "document.getElementById('objectCompareSummary').textContent",
                 'heads': "[...document.querySelectorAll('#objectCompareBody .cobj-head')].map(h => h.textContent).join('|')",
                 'left': "[...document.querySelectorAll('#objectCompareLeft .mbox .mname')].map(t => t.textContent).join('|')",
                 'right': "[...document.querySelectorAll('#objectCompareRight .mbox .mname')].map(t => t.textContent).join('|')",
                 'marks': "['removed','added','changed','same'].map(c => document.querySelectorAll('#objectCompareBody .mbox.' + c).length).join('/')",
                 'svgButton': "document.getElementById('exportSvgBtn').disabled",
                 'svg': "window.__svg"},
         expect={'title': 'complexType CatalogType compared with complexType SupplierType',
                 'summary': '17 only on the left, 9 only on the right, 0 changed',
                 'heads': 'complexType CatalogType — catalog.xsd, v1|complexType SupplierType — supplier.xsd, v1',
                 'left': 'CatalogType|@issued : date|publisher|street|city|postalCode|country|product|@sku : Code|@category : string ?|name|description|price|discount|legacyCode|tag',
                 'right': 'SupplierType|@code : Code|name|address|street|city|postalCode|country|rating',
                 'marks': '17/9/0/4',   # nothing matches but the two roots and their sequences, the two subjects
                 'svgButton': False, 'svg': 'image/svg+xml|true|true'}),   # one file, both headings, the boxes of both models
    dict(name='doc-compare-view', file='samples/compare/v1.xsdviewer.json', theme='light', size=DOC_SIZE, doc='XsdViewer-compare-view.jpg',
         action=OPEN_V2 + "const pick = (w, side) => {"
                "  [...document.querySelectorAll('#workspaces .wsgroup:not(.cmpchip)')].find(x => x.textContent.includes(w)).click();"
                "  [...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "  document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "  document.querySelector('#compareSides .cobj-mark.' + side).click(); };"
                "pick('v1', 'left'); pick('v2', 'right');"
                + DECLARATIONS
                + "await new Promise(r => setTimeout(r, 400));"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'summary': "document.getElementById('objectCompareSummary').textContent",
                 'marks': "['removed','added','changed'].map(c => document.querySelectorAll('#objectCompareBody .mbox.' + c).length).join('/')"},
         expect={'summary': '1 only on the left, 3 only on the right, 3 changed', 'marks': '1/3/6'}),
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
    dict(name='doc-compare-text', file='samples/compare/v1.xsdviewer.json', theme='light', size=DOC_SIZE, doc='XsdViewer-compare-text.jpg',
         action=OPEN_V2 + "const pick = (w, side) => {"
                "  [...document.querySelectorAll('#workspaces .wsgroup:not(.cmpchip)')].find(x => x.textContent.includes(w)).click();"
                "  [...document.querySelectorAll('#tabs .dtab')].find(t => t.textContent.includes('product.xsd')).click();"
                "  document.querySelector('#nodeList .item[data-id=\"complexType:ProductType\"]').click();"
                "  document.querySelector('#compareSides .cobj-mark.' + side).click(); };"
                "pick('v1', 'left'); pick('v2', 'right');"
                + DECLARATIONS
                + "await new Promise(r => setTimeout(r, 400));"
                "document.querySelector('#viewTabs .tab[data-view=\"text\"]').click();"
                "await new Promise(r => setTimeout(r, 400));"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'active': "document.querySelector('#viewTabs .tab.active').textContent",
                 'rows': "document.querySelectorAll('#objectCompareText .cside:first-child tr').length",
                 'marked': "document.querySelectorAll('#objectCompareText td.code.del').length"},
         expect={'active': 'Text', 'rows': 13, 'marked': 4}),
    dict(name='doc-compare-workspaces', file='samples/compare/v1.xsdviewer.json', theme='light', size=DOC_SIZE, doc='XsdViewer-compare-workspaces.jpg',
         action=OPEN_V2 + FILES
                # the listed files finish parsing in the background, and their end redraws the table
                + "await new Promise(r => setTimeout(r, 600));"
                "document.querySelector('#compareTable .crow.different').click();"
                "await new Promise(r => setTimeout(r, 400));"
                "document.getElementById('toast').classList.add('hidden');",
         checks={'sides': "[...document.querySelectorAll('#compareTable .crow .cpath')].map(t => t.textContent).join('|')",
                 'detail': "document.querySelectorAll('#compareTable .cdetail').length"},
         expect={'sides': 'catalog.xsd|catalog.xsd|common.xsd|common.xsd|product.xsd|product.xsd||shipping.xsd|supplier.xsd|',
                 'detail': 1}),   # the file name, never the path (these pictures are published)
]


class Proxy(http.server.BaseHTTPRequestHandler):
    scene = None
    results = {}
    reported = threading.Event()   # set when the scene has posted its checks: the page may be photographed

    def do_GET(self):
        self.proxy()

    def do_POST(self):
        if self.path == '/__check':
            body = self.rfile.read(int(self.headers.get('Content-Length') or 0))
            Proxy.results[Proxy.scene['name']] = json.loads(body.decode('utf-8'))
            Proxy.reported.set()
            self.send_response(204); self.end_headers()
            return
        self.proxy()

    def proxy(self):
        if self.path == '/__hold':
            # the page's load event, which Firefox photographs at, waits on this image: released once the checks are in, or at the ceiling
            Proxy.reported.wait(HOLD_SECONDS); self.send_response(204); self.end_headers()
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
        # the script runs once the page says it is ready (data-ready on the root: wired, drawn, the start-up file or workspace open) and shows its object list, a validation tab or the empty state — at the latest after ACTION_DELAY_MS;
        # the checks are posted 300 ms after the action, once what it changed has been drawn
        return ('<script>(() => { const started = Date.now();'
                ' const ready = () => document.documentElement.dataset.ready && (document.querySelector("#nodeList .item") || document.querySelector("#validation:not(.hidden)") || document.querySelector("#empty:not(.hidden)"));'
                ' const run = () => (async () => { %s })().catch(e => console.error(e)).then(() =>'
                ' setTimeout(() => fetch("/__check", {method: "POST", body: JSON.stringify({%s})}), 300));'
                ' const go = () => (ready() || Date.now() - started > %d ? run() : setTimeout(go, 50)); go(); })();</script>'
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
    # a scene without a file is the first launch: nothing open, the page saying what to do
    initial = [str(ROOT / scene['file'])] if scene.get('file') else []
    app = subprocess.Popen(['java', '-jar', str(JAR), '--no-browser', '--keep-alive', '--port', str(APP_PORT)] + initial,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        if not wait_for(APP_PORT):
            return 'the server did not start'
        Proxy.scene = scene
        Proxy.reported.clear()
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
    names = [s['name'] for s in SCENES]
    for name in names:
        if names.count(name) > 1:
            sys.exit('two scenes are named %s: a name is a file name and a result key' % name)
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
