/** Start-up of the page: texts, event wiring, first drawing, and the file given on the command line. */
import { wireEvents } from './events.js';
import { applyCapabilities, loadCapabilities } from './capabilities.js';
import { loadInitialFile } from './file-actions.js';
import { initI18n } from './i18n.js';
import { initLanguageSelector } from './language-selector.js';
import { renderPage } from './page.js';
import { startPresence } from './presence.js';
import { initTheme } from './theme.js';
import { applySettings, loadSettings } from './settings.js';
import { session } from './state.js';

startPresence();                             // first: the server counts this page from now on
await Promise.all([loadCapabilities(), loadSettings()]);   // first: the machine's language is the default language
await initI18n(session.serverLanguage);
applyCapabilities();
applySettings();
initTheme();
await initLanguageSelector();
wireEvents();
renderPage();
loadInitialFile();
