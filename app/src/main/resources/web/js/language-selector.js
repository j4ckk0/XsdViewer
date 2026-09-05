/** The language drop-list of the top bar: one entry per i18n file; changing it re-translates the whole page. */
import { applyCapabilities } from './capabilities.js';
import { compareGroup, detailsPanel } from './details.js';
import { filesPanel } from './file-list.js';
import { $, esc } from './dom.js';
import { ID } from './dom-names.js';
import { availableLanguages, language, setLanguage } from './i18n.js';
import { renderPage } from './page.js';
import { schemaInfo } from './sidebar.js';

export async function initLanguageSelector() {
  const select = $(ID.LANGUAGE);
  select.innerHTML = (await availableLanguages())
    .map(l => '<option value="' + esc(l.code) + '"' + (l.code === language ? ' selected' : '') + '>' + esc(l.name) + '</option>')
    .join('');
  select.addEventListener('change', async (e) => {
    await setLanguage(e.target.value);
    refreshTexts();
  });
}

/** Redraws everything that holds text computed at run time (static labels are re-bound by setLanguage). */
function refreshTexts() {
  renderPage();
  for (const part of [detailsPanel, compareGroup, filesPanel, schemaInfo]) part.refresh();   // their buttons' tooltips
  applyCapabilities();
}
