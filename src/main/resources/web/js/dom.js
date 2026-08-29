/** Element ids, CSS class names and data attributes of index.html / style.css, plus DOM helpers. */

export const $ = (id) => document.getElementById(id);

/** HTML-escapes a value for insertion in markup. */
export const esc = (s) => String(s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export const ID = {
  FILE_MENU_BUTTON: 'fileMenuBtn',
  FILE_MENU: 'fileMenu',
  MENU_OPEN: 'menuOpen',
  MENU_OPEN_FOLDER: 'menuOpenFolder',
  MENU_NEW_TAB: 'menuNewTab',
  MENU_CLOSE: 'menuClose',
  MENU_QUIT: 'menuQuit',
  FILE_NAME: 'fileName',
  EXPORT_BUTTON: 'exportBtn',
  SHOW_BUILTINS: 'showBuiltins',
  TABS: 'tabs',
  NEW_TAB_BUTTON: 'newTabBtn',
  SCHEMA_INFO: 'schemaInfo',
  SEARCH: 'search',
  NODE_LIST: 'nodeList',
  EMPTY: 'empty',
  GRAPH: 'graph',
  BACK_BUTTON: 'backBtn',
  GRAPH_TITLE: 'graphTitle',
  TWO_LEVELS: 'twoLevels',
  GRAPH_CANVAS: 'graphCanvas',
  TEXT: 'text',
  DETAILS: 'details',
  DROP_OVERLAY: 'dropOverlay',
  TOAST: 'toast',
  FILE_INPUT: 'fileInput',
  FOLDER_INPUT: 'folderInput',
};

export const CLS = {
  HIDDEN: 'hidden',
  ACTIVE: 'active',
  SELECTED: 'selected',
  COLLAPSED: 'collapsed',
  VIEW: 'view',
  VIEW_TAB: 'tab',
  EMPTY_BOX: 'emptybox',
  BIG: 'big',
  // document tabs
  DOC_TAB: 'dtab',
  DOC_TAB_NAME: 'tname',
  DOC_TAB_CLOSE: 'tclose',
  // sidebar
  GROUP_HEADER: 'group-h',
  GROUP_ITEMS: 'group-items',
  ITEM: 'item',
  NO_MATCH: 'nomatch',
  DOT: 'dot',
  COUNT: 'count',
  // graph (SVG)
  NODE: 'node',
  CENTER: 'center',
  EDGE: 'edge',
  EDGE_LABEL: 'edge-label',
  EDGE_LABEL_BG: 'edge-label-bg',
  NODE_NAME: 'name',
  NODE_KIND: 'kind',
  // details
  BADGE: 'badge',
  META: 'meta',
  DOC: 'doc',
  LINK: 'link',
  LINK_LABEL: 'lbl',
  LINK_TARGET: 'tgt',
  // text view
  LINE: 'line',
  LINE_DECLARATION: 'decl',
  LINE_HIGHLIGHT: 'hl',
  LINE_NUMBER: 'ln',
  CODE: 'code',
};

/** Classes of the syntax-highlighted XML tokens (colours in style.css). */
export const TOKEN_CLS = { COMMENT: 'c', PI: 'pi', VALUE: 'v', TAG: 't', ATTRIBUTE: 'a' };

/** data-* attribute names (without the "data-" prefix, as in element.dataset). */
export const DATA = {
  ID: 'id',
  TAB: 'tab',
  KIND: 'kind',
  LINE: 'line',
  LINE_NUMBER: 'n',
  TAB_INDEX: 'i',
  VIEW: 'view',
};
export const dataAttr = (name, value) => ' data-' + name + '="' + esc(value) + '"';

/** Ids inside the graph SVG. */
export const SVG_ID = { ARROW: 'arrow' };

export const selector = (cls) => '.' + cls;
