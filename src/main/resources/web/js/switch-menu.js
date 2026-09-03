/**
 * A menu of switches in the graph toolbar (the *Links* and *Types* menus): one entry per value,
 * a check mark on those that are on, the values left off remembered across sessions, and a last
 * entry putting them all back. The menu shows only the entries the shown file can have: an entry
 * carrying a family class (`wsdl`, `schematron`, `xsd`) follows the legend, which says which
 * family the file belongs to. The button is marked while a value is off.
 */
import { TEXT } from './constants.js';
import { $, CLS, ID, selector } from './dom.js';

/**
 * @param button   id of the menu's button
 * @param menu     id of the menu
 * @param storage  where the values left off are kept
 * @param values   every value the menu switches (an unknown one in the storage is dropped)
 * @param name     the data attribute an entry carries its value in
 */
export function switchMenu({ button, menu, storage, values, name }) {
  /** The values not shown. */
  const hidden = new Set();
  const entries = () => $(menu).querySelectorAll('[data-' + name + ']');
  const store = () => {
    try {
      if (hidden.size) localStorage.setItem(storage, [...hidden].join(TEXT.STORED_SEPARATOR));
      else localStorage.removeItem(storage);
    } catch (e) { /* storage unavailable */ }
  };

  return {
    /** Reads what a previous session left off. */
    init() {
      let stored = null;
      try { stored = localStorage.getItem(storage); } catch (e) { /* storage unavailable */ }
      for (const v of (stored || '').split(TEXT.STORED_SEPARATOR)) if (values.includes(v)) hidden.add(v);
    },

    isOn: (value) => !hidden.has(value),

    hidden: () => [...hidden],

    /** Switches a value (a click on its entry); the caller redraws. */
    toggle(value) {
      if (!hidden.delete(value)) hidden.add(value);
      store();
    },

    /** Every value on again. */
    showAll() {
      hidden.clear();
      store();
    },

    /** The check marks, the mark on the button, and the entries the shown file can have (the legend says its family). */
    render() {
      for (const entry of entries()) entry.classList.toggle(CLS.CHECKED, !hidden.has(entry.dataset[name]));
      $(button).classList.toggle(CLS.FILTERED, hidden.size > 0);
      const legend = $(ID.GRAPH_LEGEND);
      $(menu).classList.remove(CLS.WSDL, CLS.SCHEMATRON);
      for (const family of [CLS.WSDL, CLS.SCHEMATRON]) if (legend.classList.contains(family)) $(menu).classList.add(family);
    },

    /** The value of the entry a click landed on, or null. */
    valueOf(target) {
      const entry = target.closest('[data-' + name + ']');
      return entry ? entry.dataset[name] : null;
    },

    isShowAll: (target) => !!target.closest(selector(CLS.SHOW_ALL)),
  };
}
