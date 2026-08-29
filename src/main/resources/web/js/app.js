/** Start-up of the page: texts, event wiring, first drawing, and the file given on the command line. */
import { wireEvents } from './events.js';
import { applyCapabilities, loadCapabilities } from './capabilities.js';
import { loadInitialFile } from './file-actions.js';
import { initI18n } from './i18n.js';
import { initLanguageSelector } from './language-selector.js';
import { renderPage } from './page.js';
import { session } from './state.js';

await loadCapabilities();                    // first: the machine's language is the default language
await initI18n(session.serverLanguage);
applyCapabilities();
await initLanguageSelector();
wireEvents();
renderPage();
loadInitialFile();
