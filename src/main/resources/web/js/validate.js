/**
 * File ▸ Validate an XML file…: an XML document checked against the shown schema by the server
 * (from the schema's file on disk, so that its imports resolve), the problems listed in a dialog.
 */
import { chooseFiles, validateXml } from './api.js';
import { busy } from './busy.js';
import { WSDL_KINDS } from './constants.js';
import { $, CLS, ID, esc } from './dom.js';
import { plural, t } from './i18n.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';
import { toast, toastServerError } from './toast.js';

/** True when the active tab shows a schema the server can validate against: an XSD (not a WSDL) with a location on disk. */
export function canValidate() {
  const st = session.active;
  return !!(st.model && st.path && !st.compare && !st.model.nodes.some(n => WSDL_KINDS.has(n.kind)));
}

/** Asks for the XML file — the server's dialog when it has a display, else the browser's — and validates it. */
export async function validateFile() {
  if (!canValidate()) { toast(t(MSG.VALIDATE_NEEDS_LOCATION)); return; }
  if (!session.dialogs) { $(ID.VALIDATE_INPUT).click(); return; }
  try {
    const files = await chooseFiles();   // the files chosen: [{name, path, text}], empty when cancelled
    if (files.length) await validateText(files[0].name, files[0].text);
  } catch (e) {
    toastServerError(e);
  }
}

/** Validates {@code xml} (named {@code name}) against the active tab's schema and shows the outcome. */
export async function validateText(name, xml) {
  const st = session.active;
  if (!canValidate()) { toast(t(MSG.VALIDATE_NEEDS_LOCATION)); return; }
  let result;
  try {
    result = await busy(t(MSG.BUSY_VALIDATING), validateXml(st.path, xml));
  } catch (e) {
    toastServerError(e);
    return;
  }
  const errors = result.problems.filter(p => p.severity === PROBLEM_ERROR).length;
  $(ID.VALIDATE_SUMMARY).className = result.valid ? CLS.VALID : CLS.INVALID;
  $(ID.VALIDATE_SUMMARY).textContent = result.valid
    ? t(MSG.VALIDATE_VALID, name, st.fileName)
    : plural(errors, MSG.VALIDATE_INVALID_ONE, MSG.VALIDATE_INVALID_OTHER, name, st.fileName) + (result.truncated ? ' ' + t(MSG.VALIDATE_TRUNCATED) : '');
  $(ID.VALIDATE_PROBLEMS).innerHTML = result.problems.map(p =>
    '<div class="' + CLS.PROBLEM + ' ' + esc(p.severity) + '"><span class="' + CLS.META + '">' + esc(t(MSG.VALIDATE_WHERE, p.line, p.column)) + '</span>' + esc(p.message) + '</div>').join('');
  $(ID.VALIDATE_DIALOG).showModal();
}

const PROBLEM_ERROR = 'error';

export function closeValidation() {
  $(ID.VALIDATE_DIALOG).close();
}
