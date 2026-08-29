/** Start-up of the page: texts, event wiring, first drawing, and the file given on the command line. */
import { wireEvents } from './events.js';
import { initCapabilities, loadInitialFile } from './file-actions.js';
import { initI18n } from './i18n.js';
import { initLanguageSelector } from './language-selector.js';
import { renderPage } from './page.js';

await initI18n();
await initCapabilities();
await initLanguageSelector();
wireEvents();
renderPage();
loadInitialFile();
