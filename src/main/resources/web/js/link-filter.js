/**
 * Which categories of link the graph draws: the *Links* menu of its toolbar (a {@link switchMenu}).
 * A link belongs to the chain of a WSDL or a Schematron as soon as one of its ends does.
 */
import { LINK_CATEGORIES, STORAGE_KEY, linkCategory } from './constants.js';
import { DATA, ID } from './dom.js';
import { switchMenu } from './switch-menu.js';

const menu = switchMenu({
  button: ID.LINK_MENU_BUTTON, menu: ID.LINK_MENU, storage: STORAGE_KEY.HIDDEN_LINKS,
  values: LINK_CATEGORIES, name: DATA.CATEGORY,
});

/** True when the link between {@code fromKind} and {@code toKind} belongs to a category being drawn. */
export const isLinkShown = (edge, fromKind, toKind) => menu.isOn(linkCategory(edge, fromKind, toKind));

export const initLinkFilter = () => menu.init();
export const renderLinkMenu = () => menu.render();
export const toggleCategory = (category) => menu.toggle(category);
export const showAllLinks = () => menu.showAll();
export const categoryOfClick = (target) => menu.valueOf(target);
export const isShowAllClick = (target) => menu.isShowAll(target);
