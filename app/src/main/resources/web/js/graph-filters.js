/**
 * What the graph draws, from the two switch menus of its toolbar: *Types* switches the kinds of
 * object on and off (the built-in types being one kind among the others), *Links* the categories
 * of link ({@link linkCategory}). The selected object is always drawn, whatever its kind — the
 * menus say what its links may lead to.
 */
import { KINDS, STORAGE_KEY } from './constants.js';
import { DATA, ID } from './dom.js';
import { LINK_CATEGORIES, linkCategory } from './link-categories.js';
import { switchMenu } from './switch-menu.js';

const kinds = switchMenu({
  button: ID.TYPE_MENU_BUTTON, menu: ID.TYPE_MENU, storage: STORAGE_KEY.HIDDEN_KINDS,
  values: KINDS, name: DATA.KIND,
});
const links = switchMenu({
  button: ID.LINK_MENU_BUTTON, menu: ID.LINK_MENU, storage: STORAGE_KEY.HIDDEN_LINKS,
  values: LINK_CATEGORIES, name: DATA.CATEGORY,
});

/** Reads what a previous session left off and wires both menus; {@code redraw} is called once an entry has been switched. */
export function initGraphFilters(redraw) {
  links.init(redraw);
  kinds.init(redraw);
}

/** Both menus as they stand, for a file of {@code family} (null for a schema, which has no family). */
export function renderGraphFilters(family) {
  links.render(family);
  kinds.render(family);
}

/** True when objects of {@code kind} are drawn. */
export const isKindShown = (kind) => kinds.isOn(kind);

/** True when the link between {@code fromKind} and {@code toKind} belongs to a category being drawn. */
export const isLinkShown = (edge, fromKind, toKind) => links.isOn(linkCategory(edge, fromKind, toKind));
