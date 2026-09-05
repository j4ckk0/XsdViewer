/**
 * A menu of switches in the graph toolbar (the *Links* and *Types* menus): one entry per value,
 * a check mark on those that are on, the values left off remembered across sessions, and a last
 * entry putting them all back. The menu stays open while entries are switched (several in a row);
 * a click elsewhere, or Escape, closes it. It offers only the entries the shown file can have: an
 * entry carrying a family class (`wsdl`, `schematron`, `xsd`) is shown for that family only. The
 * button is marked while a value is off.
 */
import { TEXT } from './constants.js';
import { FAMILY } from './link-categories.js';
import { $, selector } from './dom.js';
import { CLS } from './dom-names.js';

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
  /** The value of the entry a click landed on, or null. */
  const valueAt = (target) => {
    const entry = target.closest('[data-' + name + ']');
    return entry ? entry.dataset[name] : null;
  };
  const store = () => {
    try {
      if (hidden.size) localStorage.setItem(storage, [...hidden].join(TEXT.STORED_SEPARATOR));
      else localStorage.removeItem(storage);
    } catch (e) { /* storage unavailable */ }
  };

  return {
    /** Reads what a previous session left off and wires the clicks; {@code onChange} is called once a value has been switched. */
    init(onChange) {
      let stored = null;
      try { stored = localStorage.getItem(storage); } catch (e) { /* storage unavailable */ }
      for (const v of (stored || '').split(TEXT.STORED_SEPARATOR)) if (values.includes(v)) hidden.add(v);
      $(menu).addEventListener('click', (e) => {
        const value = valueAt(e.target);
        if (value) { if (!hidden.delete(value)) hidden.add(value); }
        else if (e.target.closest(selector(CLS.SHOW_ALL))) hidden.clear();
        else return;
        store();
        e.stopPropagation();   // the menu stays open, to switch another entry
        onChange();
      });
    },

    isOn: (value) => !hidden.has(value),

    /** The check marks, the mark on the button, and the entries a file of {@code family} can have (null: a schema, which has no family). */
    render(family) {
      for (const entry of entries()) entry.classList.toggle(CLS.CHECKED, !hidden.has(entry.dataset[name]));
      $(button).classList.toggle(CLS.FILTERED, hidden.size > 0);
      $(menu).classList.toggle(CLS.WSDL, family === FAMILY.WSDL);
      $(menu).classList.toggle(CLS.SCHEMATRON, family === FAMILY.SCHEMATRON);
    },
  };
}
