/**
 * Which kinds of object the graph draws: the *Types* menu of its toolbar (a {@link switchMenu}).
 * The built-in types are one kind among the others; the selected object is always drawn, whatever
 * its kind — the menu says what its links may lead to.
 */
import { KINDS, STORAGE_KEY } from './constants.js';
import { DATA, ID } from './dom.js';
import { switchMenu } from './switch-menu.js';

const menu = switchMenu({
  button: ID.TYPE_MENU_BUTTON, menu: ID.TYPE_MENU, storage: STORAGE_KEY.HIDDEN_KINDS,
  values: KINDS, name: DATA.KIND,
});

/** True when objects of {@code kind} are drawn. */
export const isKindShown = (kind) => menu.isOn(kind);

export const initKindFilter = () => menu.init();
export const renderKindMenu = () => menu.render();
export const toggleKind = (kind) => menu.toggle(kind);
export const showAllKinds = () => menu.showAll();
export const kindOfClick = (target) => menu.valueOf(target);
export const isShowAllKindsClick = (target) => menu.isShowAll(target);
