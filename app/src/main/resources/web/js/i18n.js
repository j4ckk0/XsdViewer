/**
 * The page's texts, from i18n/<language>.json: ?lang=, else the remembered choice, else the
 * machine's locale, else English; missing keys fall back to English.
 */
import { STORAGE_KEY } from './constants.js';
import { MSG } from './message-keys.js';

const I18N_PATH = '/i18n/';
const FILE_EXTENSION = '.json';
/** The languages that have a file, in the order of the drop-list. */
const LANGUAGES = ['en', 'fr'];
const DEFAULT_LANGUAGE = 'en';
const LANGUAGE_PARAM = 'lang';

/** Attributes binding an element to a key (see index.html). */
const ATTR = { TEXT: 'data-i18n', TITLE: 'data-i18n-title', PLACEHOLDER: 'data-i18n-placeholder' };
const PLACEHOLDER = /\{(\d+)\}/g;

/** code -> texts, for every file loaded so far. */
const loaded = new Map();
let texts = {};
let fallback = {};
export let language = DEFAULT_LANGUAGE;

const known = (code) => LANGUAGES.includes(code);

/** @param machineLanguage the language of the machine's locale, or null when unknown */
function pickLanguage(machineLanguage) {
  const wanted = new URLSearchParams(location.search).get(LANGUAGE_PARAM);
  if (wanted && known(wanted)) return wanted;
  try {
    const stored = localStorage.getItem(STORAGE_KEY.LANGUAGE);
    if (stored && known(stored)) return stored;
  } catch (e) { /* storage unavailable */ }
  if (machineLanguage && known(machineLanguage)) return machineLanguage;
  return DEFAULT_LANGUAGE;
}

async function load(code) {
  if (loaded.has(code)) return loaded.get(code);
  let result = {};
  try {
    const resp = await fetch(I18N_PATH + code + FILE_EXTENSION);
    if (resp.ok) result = await resp.json();
  } catch (e) {
    return result;   // server unreachable: the keys are shown, the error is reported when a file is loaded; not cached
  }
  loaded.set(code, result);
  return result;
}

/** Loads the texts and translates the static page. To be awaited before anything is rendered. */
export async function initI18n(machineLanguage) {
  fallback = await load(DEFAULT_LANGUAGE);
  await setLanguage(pickLanguage(machineLanguage), false);
}

/** Switches the page to {@code code} (a member of LANGUAGES) and re-translates the static labels. */
export async function setLanguage(code, remember = true) {
  if (!known(code)) return;
  language = code;
  texts = code === DEFAULT_LANGUAGE ? fallback : await load(code);
  if (remember) {
    try { localStorage.setItem(STORAGE_KEY.LANGUAGE, code); } catch (e) { /* storage unavailable */ }
  }
  document.documentElement.lang = code;
  translate(document);
}

/** Every language with a file, as {code, name} with the name in that language. */
export async function availableLanguages() {
  const out = [];
  for (const code of LANGUAGES) {
    const file = await load(code);
    out.push({ code, name: file[MSG.LANGUAGE_NAME] || code });
  }
  return out;
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
