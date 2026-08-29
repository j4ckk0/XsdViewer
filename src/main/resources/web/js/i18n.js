/**
 * Texts of the page, read from i18n/<language>.json (one file per language). The language is the
 * "lang" query parameter when given, else the first browser language that has a file, else English.
 * Missing keys fall back to English, then to the key itself.
 */

const I18N_PATH = '/i18n/';
const FILE_EXTENSION = '.json';
const LANGUAGES = ['en', 'fr'];
const DEFAULT_LANGUAGE = 'en';
const LANGUAGE_PARAM = 'lang';
const LANGUAGE_CODE_LENGTH = 2;

/** Attributes binding an element to a key (see index.html). */
const ATTR = { TEXT: 'data-i18n', TITLE: 'data-i18n-title', PLACEHOLDER: 'data-i18n-placeholder' };
const PLACEHOLDER = /\{(\d+)\}/g;

let texts = {};
let fallback = {};
export let language = DEFAULT_LANGUAGE;

function pickLanguage() {
  const wanted = new URLSearchParams(location.search).get(LANGUAGE_PARAM);
  const candidates = wanted ? [wanted] : (navigator.languages || [navigator.language]);
  for (const c of candidates) {
    const code = String(c || '').toLowerCase().slice(0, LANGUAGE_CODE_LENGTH);
    if (LANGUAGES.includes(code)) return code;
  }
  return DEFAULT_LANGUAGE;
}

async function load(lang) {
  try {
    const resp = await fetch(I18N_PATH + lang + FILE_EXTENSION);
    return resp.ok ? await resp.json() : {};
  } catch (e) {
    return {};   // server unreachable: the keys are shown, the error is reported when a file is loaded
  }
}

/** Loads the texts and translates the static page. To be awaited before anything is rendered. */
export async function initI18n() {
  language = pickLanguage();
  fallback = await load(DEFAULT_LANGUAGE);
  texts = language === DEFAULT_LANGUAGE ? fallback : await load(language);
  document.documentElement.lang = language;
  translate(document);
}

export const has = (key) => key in texts || key in fallback;

/** The text for {@code key}, with {0}, {1}... replaced by {@code args}. */
export function t(key, ...args) {
  const s = texts[key] ?? fallback[key] ?? key;
  return args.length ? format(s, args) : s;
}

export const format = (s, args) => s.replace(PLACEHOLDER, (m, i) => (i < args.length ? String(args[i]) : m));

/** {@code oneKey} when n is 1, else {@code otherKey}; n is argument {0}, {@code args} follow. */
export const plural = (n, oneKey, otherKey, ...args) => t(n === 1 ? oneKey : otherKey, n, ...args);

/** Applies the data-i18n* bindings of the elements under {@code root}. */
export function translate(root) {
  root.querySelectorAll('[' + ATTR.TEXT + ']').forEach(el => { el.textContent = t(el.getAttribute(ATTR.TEXT)); });
  root.querySelectorAll('[' + ATTR.TITLE + ']').forEach(el => { el.title = t(el.getAttribute(ATTR.TITLE)); });
  root.querySelectorAll('[' + ATTR.PLACEHOLDER + ']').forEach(el => { el.placeholder = t(el.getAttribute(ATTR.PLACEHOLDER)); });
}
