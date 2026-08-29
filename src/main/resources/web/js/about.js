/** Help > About: the tool's name, version, Java runtime, licence and project page, in a modal dialog. */
import { LICENSE_URL, PROJECT_URL } from './constants.js';
import { $, ID } from './dom.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

const UNKNOWN = '?';

export function showAbout() {
  $(ID.ABOUT_VERSION).textContent = t(MSG.ABOUT_VERSION, session.serverVersion || UNKNOWN);
  $(ID.ABOUT_JAVA).textContent = t(MSG.ABOUT_JAVA, session.javaVersion || UNKNOWN);
  $(ID.ABOUT_PROJECT_LINK).href = PROJECT_URL;
  $(ID.ABOUT_PROJECT_LINK).textContent = PROJECT_URL;
  $(ID.ABOUT_LICENSE_LINK).href = LICENSE_URL;
  $(ID.ABOUT_DIALOG).showModal();
}

export function closeAbout() {
  $(ID.ABOUT_DIALOG).close();
}
