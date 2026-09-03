#!/usr/bin/env python3
"""
A visual smoke test of the page: opens the samples in the built jar, drives the page (a selection,
a view, the theme), checks a few facts on it and saves a screenshot of each scene.

    scripts/screenshots.py                 # after mvn package: target/screenshots/*.png, checks on stdout
    scripts/screenshots.py --keep-going    # every scene even after a failed check
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
FIREFOX = os.environ.get('FIREFOX', 'firefox')
APP_PORT, PROXY_PORT = 8765, 8766
SIZE = '1500,800'
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
         action="document.querySelector('#nodeList .item[data-id=\"complexType:PurchaseOrderType\"]').click();",
         checks={'title': "document.getElementById('graphTitle').textContent",
                 'nodes': "document.querySelectorAll('#graphCanvas .node').length",
                 'details': "document.querySelector('#detailsContent h2').textContent"},
         expect={'title': 'complexType PurchaseOrderType', 'nodes': 8, 'details': 'PurchaseOrderType'}),   # the centre, 6 links out, 1 user
    dict(name='enumeration', file='samples/purchaseOrder.xsd', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"simpleType:Currency\"]').click();",
         checks={'values': "document.querySelectorAll('#detailsContent .value').length"},
         expect={'values': 3}),
    dict(name='text-dark', file='samples/purchaseOrder.xsd', theme='dark',
         action="document.querySelector('.tab[data-view=\"text\"]').click();",
         checks={'theme': "document.documentElement.dataset.theme",
                 'highlighted': "document.querySelectorAll('#text .line.hl').length"},
         expect={'theme': 'dark', 'highlighted': 1}),
    dict(name='wsdl-operation', file='samples/wsdl/purchaseOrderService.wsdl', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"operation:PurchaseOrderPortType.submitPurchaseOrder\"]').click();"
                "const two = document.getElementById('twoLevels'); if (!two.checked) two.click();",
         checks={'messages': "document.querySelectorAll('#graphCanvas .node.message').length",
                 'tabs': "document.querySelectorAll('#tabs .dtab').length",
                 'legend': "getComputedStyle(document.querySelector('#graphLegend .lg.service')).display !== 'none'"},
         expect={'messages': 3, 'tabs': 3, 'legend': True}),   # the WSDL, purchaseOrder.xsd it imports, ext.xsd that one imports
    dict(name='schematron-rule', file='samples/schematron/purchaseOrder.sch', theme='light',
         action="document.querySelector('#nodeList .item[data-id=\"rule:structure/po:item\"]').click();"
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
         action="document.querySelector('#nodeList .item[data-id=\"complexType:OrderType\"]').click();",
         checks={'resolved': "document.querySelectorAll('#graphCanvas .node.complexType, #graphCanvas .node.simpleType').length",
                 'external': "document.querySelectorAll('#graphCanvas .node.external').length",
                 'fromFiles': "[...document.querySelectorAll('#graphCanvas .node .kind')].filter(k => k.textContent.includes('.xsd')).length"},
         expect={'resolved': 5, 'external': 0, 'fromFiles': 4}),   # the centre and its four targets, resolved from the listed files
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
        r = subprocess.run([FIREFOX, '--headless', '--no-remote', '--profile', str(profile), '--window-size=' + SIZE,
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


def main():
    keep_going = '--keep-going' in sys.argv
    if not JAR.exists():
        sys.exit('%s missing: run mvn package first' % JAR)
    if not shutil.which(FIREFOX):
        sys.exit('firefox not found (set FIREFOX)')
    OUT.mkdir(parents=True, exist_ok=True)
    server = http.server.ThreadingHTTPServer(('127.0.0.1', PROXY_PORT), Proxy)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    failed = 0
    with tempfile.TemporaryDirectory(dir=ROOT / 'target') as profile:   # Firefox as a snap cannot write under /tmp
        for scene in SCENES:
            problem = shoot(scene, profile)
            print('%-16s %s' % (scene['name'], 'ok ' + str(Proxy.results.get(scene['name'])) if problem is None else 'FAILED - ' + problem))
            if problem:
                failed += 1
                if not keep_going:
                    break
    server.shutdown()
    print('%d scene(s) failed; screenshots in %s' % (failed, OUT) if failed else 'all scenes ok; screenshots in %s' % OUT)
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
