/**
 * File ▸ Validate an XML file…: a document checked against the shown schema in a tab of its own —
 * the XSD (the server's JDK validator, from the schema's file on disk so that its imports resolve),
 * the Schematron (the server's XPath 1.0 evaluator), or both when the workspace holds the other
 * one too. The problems are listed next to the document with their lines; a Schematron problem
 * names the assertion, rule and pattern that fired, which the page can select in the schema.
 */
import { chooseFiles, openLocation, validateXml } from './api.js';
import { busy } from './busy.js';
import { ALL_PHASES, ID_SEPARATOR, NODE_KIND, PROBLEM_SEVERITY, PROBLEM_SOURCE, SCHEMATRON_FILE_PATTERN, STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE, TEXT, XSD_ONLY_FILE_PATTERN, isSchematron, isWsdl, kindOfId } from './constants.js';
import { $, CLS, DATA, ID, dataAttr, esc, selector } from './dom.js';
import { plural, t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';
import { activateTab, newTab, validationStatus } from './tabs.js';
import { toast, toastServerError } from './toast.js';
import { highlightXml } from './xml-highlighter.js';

const SEVERITY_ORDER = [PROBLEM_SEVERITY.ERROR, PROBLEM_SEVERITY.WARNING, PROBLEM_SEVERITY.INFO, PROBLEM_SEVERITY.UNSUPPORTED];
const LINK_SEPARATOR = ' · ';

/** What redraws the page when a validation starts or ends: set by the wiring (page.js draws this view and depends on this module). */
let redraw = () => {};
export function onChange(fn) { redraw = fn; }

/** True when the active tab shows a schema the server can validate against: an XSD or a Schematron (not a WSDL) with a location on disk. */
export function canValidate() {
  const st = session.active;
  return !!(st.model && st.path && !st.compare && !st.validation && !isWsdl(st.model));
}

/** True when a workspace file is a Schematron / an XSD: by its model when parsed, by its name otherwise. */
const isSchematronFile = (f) => (f.model ? isSchematron(f.model) : SCHEMATRON_FILE_PATTERN.test(f.name));
const isXsdFile = (f) => (f.model ? !isSchematron(f.model) && !isWsdl(f.model) : XSD_ONLY_FILE_PATTERN.test(f.name));

/** The schemas a validation started from the active tab uses: the tab's file, and the first located file of the other kind in the workspace. */
function schemasOf(st) {
  const self = { name: st.fileName, path: st.path };
  const partner = (test) => st.workspace.files.find(f => f.path && f.path !== st.path && test(f));
  const other = partner(isSchematron(st.model) ? isXsdFile : isSchematronFile);
  const otherRef = other ? { name: other.name, path: other.path } : null;
  return isSchematron(st.model) ? { xsd: otherRef, sch: self } : { xsd: self, sch: otherRef };
}

/** Asks for the XML file — the server's dialog when it has a display, else the browser's — and validates it. */
export async function validateFile() {
  if (!canValidate()) { toast(t(MSG.VALIDATE_NEEDS_LOCATION)); return; }
  if (!session.dialogs) { $(ID.VALIDATE_INPUT).click(); return; }
  try {
    const files = await chooseFiles();   // the files chosen: [{name, path, text}], empty when cancelled
    if (files.length) await validateText(files[0].name, files[0].text, files[0].path);
  } catch (e) {
    toastServerError(e);
  }
}

/** Validates {@code xml} (named {@code name}, at {@code path} on disk when known) against the active tab's schema(s), in a validation tab (reused for the same document and schemas). */
export async function validateText(name, xml, path = null) {
  const st = session.active;
  if (!canValidate()) { toast(t(MSG.VALIDATE_NEEDS_LOCATION)); return; }
  const { xsd, sch } = schemasOf(st);
  const same = (v) => v && v.name === name && (v.xsd && v.xsd.path) === (xsd && xsd.path) && (v.sch && v.sch.path) === (sch && sch.path);
  let tab = session.tabs.find(x => x.workspace === st.workspace && same(x.validation));
  if (!tab) {
    tab = newTab();
    tab.validation = { name, path, text: xml, xsd, sch, phase: null, result: null, error: null, selected: -1 };
  } else {
    Object.assign(tab.validation, { text: xml, path: path || tab.validation.path, result: null, error: null, selected: -1 });
  }
  activateTab(tab);
  await run(tab);
}

/** Runs (again) the validation of {@code tab}; the page is redrawn when it is the active tab. */
async function run(tab) {
  const v = tab.validation;
  redraw();
  try {
    v.result = await busy(t(MSG.BUSY_VALIDATING), validateXml(v.xsd ? v.xsd.path : '', v.sch ? v.sch.path : '', v.phase || '', v.text));
    v.error = null;
    // the first error is selected, else the first problem shown
    const first = v.result.problems.findIndex(p => p.severity === PROBLEM_SEVERITY.ERROR && visible(p));
    v.selected = first >= 0 ? first : v.result.problems.findIndex(p => visible(p));
  } catch (e) {
    v.result = null;
    v.error = e.message;
  }
  if (session.active === tab) redraw();
}

/** ↻ Run again: the document read again from disk when the server knows where it is (it may have been edited), then validated. */
export async function rerun() {
  const v = session.active.validation;
  if (!v) return;
  if (v.path) {
    try {
      const f = await openLocation(v.path, v.name, true);
      if (f && f.text != null) v.text = f.text;
    } catch (e) { /* not readable any more: the text at hand is validated */ }
  }
  v.selected = -1;
  await run(session.active);
}

/** Another document…: the same schemas, a new document (through the server's dialog, else the browser's). */
export async function chooseAnotherDocument() {
  const v = session.active.validation;
  if (!v) return;
  if (!session.dialogs) { $(ID.VALIDATE_INPUT).click(); return; }
  try {
    const files = await chooseFiles();
    if (files.length) await replaceDocument(files[0].name, files[0].text, files[0].path);
  } catch (e) {
    toastServerError(e);
  }
}

/** The document of the active validation tab replaced by another one (the browser's file input, the server's dialog), then validated. */
export async function replaceDocument(name, xml, path = null) {
  const v = session.active.validation;
  if (!v) return;
  Object.assign(v, { name, text: xml, path, result: null, error: null, selected: -1 });
  await run(session.active);
}

/** The schema of one kind ({@code source}: xsd / schematron) replaced by the workspace file at {@code path} (empty: none), then the document validated again. */
export async function setSchema(source, path) {
  const tab = session.active, v = tab.validation;
  if (!v) return;
  const entry = path ? tab.workspace.files.find(f => f.path === path) : null;
  const ref = entry ? { name: entry.name, path: entry.path } : null;
  if (source === PROBLEM_SOURCE.XSD) v.xsd = ref;
  else { v.sch = ref; v.phase = null; }   // another Schematron has its own phases
  if (!v.xsd && !v.sch) return;   // the lists never offer that: one schema at least
  v.selected = -1;
  await run(tab);
}

export async function setPhase(phase) {
  const v = session.active.validation;
  if (!v) return;
  v.phase = phase === ALL_PHASES ? ALL_PHASES : phase;
  v.selected = -1;
  await run(session.active);
}

export const isErrorsOnly = () => $(ID.VALIDATE_ERRORS_ONLY).checked;

export function initOptions() {
  let on = false;
  try { on = localStorage.getItem(STORAGE_KEY.VALIDATE_ERRORS_ONLY) === STORAGE_TRUE; } catch (e) { /* storage unavailable */ }
  $(ID.VALIDATE_ERRORS_ONLY).checked = on;
}

export function rememberOptions() {
  try { localStorage.setItem(STORAGE_KEY.VALIDATE_ERRORS_ONLY, isErrorsOnly() ? STORAGE_TRUE : STORAGE_FALSE); } catch (e) { /* storage unavailable */ }
}

const visible = (p) => !isErrorsOnly() || p.severity === PROBLEM_SEVERITY.ERROR;

/** The workspace entry of the Schematron a validation used, and the node ids a problem row links to. */
export function schematronEntryOf(tab) {
  const v = tab.validation;
  return v && v.sch ? tab.workspace.files.find(f => f.path === v.sch.path) || null : null;
}

/** Draws the active validation tab: the verdict and the schemas in the header, the problems on the left, the document on the right. */
export function renderValidation() {
  const tab = session.active, v = tab.validation;
  const r = v.result;
  const count = (sev) => (r ? r.problems.filter(p => p.severity === sev).length : 0);
  const errors = count(PROBLEM_SEVERITY.ERROR);
  $(ID.VALIDATE_TITLE).textContent = v.error ? t(MSG.VALIDATE_VERDICT_FAILED, v.name)
    : !r ? t(MSG.VALIDATE_VERDICT_PENDING, v.name)
      : r.valid ? t(MSG.VALIDATE_VERDICT_VALID, v.name) : plural(errors, MSG.VALIDATE_VERDICT_INVALID_ONE, MSG.VALIDATE_VERDICT_INVALID_OTHER, v.name);
  $(ID.VALIDATE_TITLE).className = validationStatus(tab);
  // where the document is: known when the server's dialog chose it (a file from the browser comes without its folder)
  $(ID.VALIDATE_PATH).textContent = v.path || t(MSG.VALIDATE_NO_PATH);
  $(ID.VALIDATE_PATH).title = v.path ? t(MSG.VALIDATE_PATH_TITLE) : '';
  let summary = v.error ? v.error : r ? t(MSG.VALIDATE_SUMMARY, errors, count(PROBLEM_SEVERITY.WARNING), count(PROBLEM_SEVERITY.UNSUPPORTED)) : '';
  if (r && v.sch) summary += TEXT.TOAST_SEPARATOR + t(MSG.VALIDATE_CHECKS, r.checked, r.phase === ALL_PHASES ? t(MSG.VALIDATE_PHASE_ALL) : r.phase);
  if (r && r.truncated) summary += ' ' + t(MSG.VALIDATE_TRUNCATED);
  $(ID.VALIDATE_SUMMARY).textContent = summary;
  // one chip per kind of schema: a list of the workspace's located files of that kind (none, unless the other kind is none too), and its own verdict
  const chip = (schema, other, source, label, test) => {
    const own = r && schema ? r.problems.filter(p => p.source === source && p.severity === PROBLEM_SEVERITY.ERROR).length : null;
    const state = !schema ? '' : !r ? CLS.PENDING : own ? CLS.INVALID : CLS.VALID;
    const files = tab.workspace.files.filter(f => f.path && test(f));
    if (schema && !files.some(f => f.path === schema.path)) files.unshift(schema);
    const options = (other ? ['<option value=""' + (schema ? '' : ' selected') + '>' + esc(t(MSG.VALIDATE_SCHEMA_NONE)) + '</option>'] : [])
      .concat(files.map(f => '<option value="' + esc(f.path) + '"' + (schema && f.path === schema.path ? ' selected' : '') + ' title="' + esc(f.path) + '">' + esc(f.name) + '</option>'));
    return '<span class="' + CLS.VALIDATE_CHIP + ' ' + state + '" title="' + esc(t(MSG.VALIDATE_SCHEMA_TITLE)) + '"><span class="' + CLS.VALIDATE_SOURCE + '">' + esc(label) + '</span>'
      + '<select' + dataAttr(DATA.SOURCE, source) + '>' + options.join('') + '</select>'
      + '<span class="' + CLS.VALIDATE_VERDICT + '">' + (r && schema ? (own ? t(MSG.VALIDATE_CHIP_INVALID, own) : t(MSG.VALIDATE_CHIP_VALID)) : '') + '</span></span>';
  };
  $(ID.VALIDATE_SCHEMAS).innerHTML = chip(v.xsd, v.sch, PROBLEM_SOURCE.XSD, t(MSG.VALIDATE_SOURCE_XSD), isXsdFile)
    + chip(v.sch, v.xsd, PROBLEM_SOURCE.SCHEMATRON, t(MSG.VALIDATE_SOURCE_SCHEMATRON), isSchematronFile);
  // the phases of the Schematron, when it declares some
  const phases = r && r.phases ? r.phases : [];
  $(ID.VALIDATE_PHASE_BOX).classList.toggle(CLS.HIDDEN, !phases.length);
  if (phases.length) {
    $(ID.VALIDATE_PHASE).innerHTML = [ALL_PHASES, ...phases].map(p => '<option value="' + esc(p) + '"' + (p === r.phase ? ' selected' : '') + '>' + esc(p === ALL_PHASES ? t(MSG.VALIDATE_PHASE_ALL) : p) + '</option>').join('');
  }
  $(ID.VALIDATE_PROBLEMS).innerHTML = r ? problemsHtml(tab) : '';
  $(ID.VALIDATE_DOC).innerHTML = documentHtml(v);
  selectProblem(v.selected, false);
}

/** The problem rows, in the order the validators found them; a Schematron row links to what fired. */
function problemsHtml(tab) {
  const v = tab.validation, r = v.result;
  const entry = schematronEntryOf(tab);
  const nodeName = (id) => {
    const n = entry && entry.model ? entry.model.nodes.find(x => x.id === id) : null;
    return n ? n.name : id.slice(id.indexOf(ID_SEPARATOR) + 1);
  };
  // an assertion named by its id gets its test after the link; one named by its test already shows it
  const testOf = (p) => (p.test && nodeName(p.assertion) !== p.test ? ' <code>' + esc(p.test) + '</code>' : '');
  const link = (id) => '<a class="' + CLS.VALIDATE_LINK + '"' + dataAttr(DATA.ID, id) + ' title="' + esc(t(MSG.VALIDATE_IN_SCHEMA)) + '">'
    + '<span class="' + CLS.DOT + ' ' + esc(kindOfId(id)) + '"></span>' + esc(nodeName(id)) + '</a>';
  const rows = [];
  r.problems.forEach((p, i) => {
    if (!visible(p)) return;
    const where = p.line > 0 ? (p.source === PROBLEM_SOURCE.XSD && p.column > 0 ? t(MSG.VALIDATE_WHERE, p.line, p.column) : t(MSG.VALIDATE_LINE, p.line)) : '';
    const unsupported = p.severity === PROBLEM_SEVERITY.UNSUPPORTED;
    const message = unsupported ? t(MSG.VALIDATE_UNSUPPORTED, p.test || '') + (p.message ? ' ' + TEXT.TOAST_SEPARATOR + ' ' + p.message : '') : p.message;
    let links = '';
    if (p.source === PROBLEM_SOURCE.SCHEMATRON) {
      const parts = [];
      if (p.assertion) parts.push(link(p.assertion) + (unsupported ? '' : testOf(p)));
      if (p.rule) parts.push('<span class="' + CLS.META + '">' + esc(kindLabel(NODE_KIND.RULE)) + '</span> ' + link(p.rule));
      if (p.pattern) parts.push('<span class="' + CLS.META + '">' + esc(kindLabel(NODE_KIND.PATTERN)) + '</span> ' + link(p.pattern));
      if (parts.length) links = '<div class="' + CLS.VALIDATE_LINKS + '">' + parts.join(LINK_SEPARATOR) + '</div>';
    }
    rows.push('<div class="' + CLS.VALIDATE_PROBLEM + ' ' + esc(p.severity) + (i === v.selected ? ' ' + CLS.SELECTED : '') + '"' + dataAttr(DATA.PROBLEM_INDEX, i)
      + (p.location ? ' title="' + esc(t(MSG.VALIDATE_LOCATION, p.location)) + '"' : '') + ' tabindex="0">'
      + '<span class="' + CLS.VALIDATE_SEVERITY + '"></span>'
      + '<span class="' + CLS.VALIDATE_SOURCE + '">' + esc(p.source === PROBLEM_SOURCE.XSD ? t(MSG.VALIDATE_SOURCE_XSD) : t(MSG.VALIDATE_SOURCE_SCHEMATRON)) + '</span>'
      + '<span class="' + CLS.VALIDATE_WHERE + '">' + esc(where) + '</span>'
      + '<span class="' + CLS.VALIDATE_MESSAGE + '">' + esc(message) + links + '</span></div>');
  });
  return rows.length ? rows.join('') : '<div class="' + CLS.META + ' ' + CLS.VALIDATE_NONE + '">' + esc(t(r.problems.length ? MSG.VALIDATE_NONE_SHOWN : MSG.VALIDATE_NONE)) + '</div>';
}

/** The document, line by line with the syntax colours of the text view; the lines with a problem carry its worst severity. */
function documentHtml(v) {
  const worst = new Map();
  if (v.result) {
    for (const p of v.result.problems) {
      if (p.line <= 0 || !visible(p)) continue;
      const rank = SEVERITY_ORDER.indexOf(p.severity);
      if (!worst.has(p.line) || rank < SEVERITY_ORDER.indexOf(worst.get(p.line))) worst.set(p.line, p.severity);
    }
  }
  const lines = highlightXml(v.text);
  let html = '';
  for (let i = 0; i < lines.length; i++) {
    const ln = i + 1, sev = worst.get(ln);
    html += '<div class="' + CLS.LINE + (sev ? ' ' + CLS.VALIDATE_LINE + ' ' + sev : '') + '"' + dataAttr(DATA.LINE_NUMBER, ln) + '>'
      + '<span class="' + CLS.LINE_NUMBER + '">' + ln + '</span><span class="' + CLS.CODE + '">' + (lines[i] || ' ') + '</span></div>';
  }
  return html;
}

/** Selects problem {@code i} (-1: none): its row is marked, the document line highlighted and (when {@code scroll}) brought into view. */
export function selectProblem(i, scroll = true) {
  const v = session.active.validation;
  if (!v) return;
  v.selected = i;
  const problems = $(ID.VALIDATE_PROBLEMS);
  problems.querySelectorAll(selector(CLS.VALIDATE_PROBLEM)).forEach(row => row.classList.toggle(CLS.SELECTED, +row.dataset[DATA.PROBLEM_INDEX] === i));
  const doc = $(ID.VALIDATE_DOC);
  doc.querySelectorAll(selector(CLS.LINE) + selector(CLS.LINE_HIGHLIGHT)).forEach(el => el.classList.remove(CLS.LINE_HIGHLIGHT));
  const p = v.result && i >= 0 ? v.result.problems[i] : null;
  if (!p || p.line <= 0) return;
  const el = doc.querySelector(selector(CLS.LINE) + '[data-' + DATA.LINE_NUMBER + '="' + p.line + '"]');
  if (!el) return;
  el.classList.add(CLS.LINE_HIGHLIGHT);
  if (scroll) {
    el.scrollIntoView({ block: 'center' });
    const row = problems.querySelector(selector(CLS.VALIDATE_PROBLEM) + '[data-' + DATA.PROBLEM_INDEX + '="' + i + '"]');
    if (row && document.activeElement !== row) row.scrollIntoView({ block: 'nearest' });
  }
}

/** The next / previous visible problem from the selected one ({@code step} 1 / -1). */
export function stepProblem(step) {
  const v = session.active.validation;
  if (!v || !v.result) return;
  const shown = [...$(ID.VALIDATE_PROBLEMS).querySelectorAll(selector(CLS.VALIDATE_PROBLEM))].map(row => +row.dataset[DATA.PROBLEM_INDEX]);
  if (!shown.length) return;
  const at = shown.indexOf(v.selected);
  const next = shown[Math.min(shown.length - 1, Math.max(0, (at < 0 ? (step > 0 ? -1 : shown.length) : at) + step))];
  selectProblem(next);
  const row = $(ID.VALIDATE_PROBLEMS).querySelector(selector(CLS.VALIDATE_PROBLEM) + '[data-' + DATA.PROBLEM_INDEX + '="' + next + '"]');
  if (row) row.focus();
}

/** The first visible problem at document line {@code line}, or -1. */
export function problemAtLine(line) {
  const v = session.active.validation;
  if (!v || !v.result) return -1;
  return v.result.problems.findIndex(p => p.line === line && visible(p));
}
