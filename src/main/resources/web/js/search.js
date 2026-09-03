/** What the search box matches: an object's name, the names inside its declaration (nested elements and attributes, a message's parts), its expression (a Schematron rule's context, an assertion's test), its documentation. */
import { t } from './i18n.js';
import { MSG } from './message-keys.js';

const includes = (s, filter) => !!s && s.toLowerCase().includes(filter);

/** True when {@code n} answers the lower-cased {@code filter} (an empty filter matches everything). */
export const matches = (n, filter) => !filter || includes(n.name, filter) || (n.members || []).some(m => includes(m, filter)) || includes(n.xpath, filter) || includes(n.doc, filter);

/** Why {@code n} is listed when its name does not match: the first matching member, else the word for the expression or the documentation; null when the name matches. */
export function matchedBy(n, filter) {
  if (!filter || includes(n.name, filter)) return null;
  const member = (n.members || []).find(m => includes(m, filter));
  if (member) return member;
  if (includes(n.xpath, filter)) return t(MSG.SEARCH_IN_XPATH);
  return includes(n.doc, filter) ? t(MSG.SEARCH_IN_DOC) : null;
}
