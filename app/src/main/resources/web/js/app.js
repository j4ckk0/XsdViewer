/** Start-up of the page: texts, event wiring, first drawing, and the file given on the command line. */
import { wireEvents } from './events.js';
import { DATA } from './dom-names.js';
import { applyCapabilities, loadCapabilities } from './capabilities.js';
import { loadInitialFile } from './file-actions.js';
import { initI18n } from './i18n.js';
import { initLanguageSelector } from './language-selector.js';
import { initGraphFilters } from './graph-filters.js';
import { initModelView } from './model-view.js';
import { select } from './navigation.js';
import { initPanels } from './panels.js';
import { renderPage } from './page.js';
import { renderMainView, showView } from './view-router.js';
import { startPresence } from './presence.js';
import { initTheme } from './theme.js';
import { applySettings, loadSettings } from './settings.js';
import { applyViewOptions } from './view-options.js';
import { session } from './state.js';

startPresence();                             // first: the server counts this page from now on
await Promise.all([loadCapabilities(), loadSettings()]);   // first: the machine's language is the default language
await initI18n(session.serverLanguage);
applyCapabilities();
applySettings();
applyViewOptions();
initTheme();
await initLanguageSelector();
wireEvents();
initGraphFilters(renderMainView);   // the Links and Types menus redraw the graph they filter
initPanels(renderMainView);
initModelView(select, showView);
renderPage();
await loadInitialFile();
document.documentElement.dataset[DATA.READY] = DATA.READY;   // wired, drawn, and the file or workspace given on the command line open: what a script driving the page waits for
