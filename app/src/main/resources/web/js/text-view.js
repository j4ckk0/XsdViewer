/** The text view: the schema source with line numbers, syntax colours and the selected declaration highlighted. */
import { VIEW } from './constants.js';
import { $, dataAttr, esc, selector } from './dom.js';
import { CLS, DATA, ID } from './dom-names.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';
import { refreshFind } from './text-find.js';
import { highlightXml } from './xml-highlighter.js';

export function renderText() {
  const st = session.active;
  const lines = highlightXml(st.text);
  let html = '';
  for (let i = 0; i < lines.length; i++) {
    const ln = i + 1;
    const id = st.lineToNode.get(ln);
    html += '<div class="' + CLS.LINE + (id ? ' ' + CLS.LINE_DECLARATION : '') + '"' + dataAttr(DATA.LINE_NUMBER, ln) + (id ? dataAttr(DATA.ID, id) : '') + '>'
      + '<span class="' + CLS.LINE_NUMBER + '"' + (id ? ' title="' + esc(t(MSG.TEXT_SELECT, id)) + '"' : '') + '>' + ln + '</span>'
      + '<span class="' + CLS.CODE + '">' + (lines[i] || ' ') + '</span></div>';
  }
  $(ID.TEXT).innerHTML = html;
  refreshFind();
}

/** Highlights the line of the selected declaration; scrolls to it when {@code scroll} and the text view is shown. */
export function highlightTextLine(scroll) {
  const st = session.active;
  const container = $(ID.TEXT);
  container.querySelectorAll(selector(CLS.LINE) + selector(CLS.LINE_HIGHLIGHT)).forEach(el => el.classList.remove(CLS.LINE_HIGHLIGHT));
  if (!st.selected) return;
  const n = st.nodes.get(st.selected);
  if (!n || n.line <= 0) return;
  const el = container.querySelector(selector(CLS.LINE) + '[data-' + DATA.LINE_NUMBER + '="' + n.line + '"]');
  if (!el) return;
  el.classList.add(CLS.LINE_HIGHLIGHT);
  if (scroll && st.view === VIEW.TEXT) el.scrollIntoView({ block: 'center' });
}
