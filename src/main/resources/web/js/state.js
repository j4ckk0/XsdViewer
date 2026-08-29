import { VIEW } from './constants.js';

/** Scroll positions of a tab's views, restored when the tab is shown again. */
export const newScroll = () => ({ text: 0, graphTop: 0, graphLeft: 0 });

/** One document tab: the file, its graph and the UI state of that tab (view, selection, history...). */
export function newTabState() {
  return {
    fileName: null,
    path: null,         // file path on the server, when the server read it (initial file, followed links)
    rel: null,          // path in an opened folder, when the file came from the library
    located: null,      // promise of the server's search for the path of a file opened in the browser
    text: '',
    model: null,        // { targetNamespace, imports, nodes, edges } as answered by /api/parse
    nodes: new Map(),   // id -> node
    outEdges: new Map(),// id -> [edge]
    inEdges: new Map(), // id -> [edge]
    lineToNode: new Map(),
    selected: null,
    history: [],
    view: VIEW.GRAPH,
    filter: '',
    collapsed: new Set(),
    scroll: newScroll(),
  };
}

/** Everything the page holds: the document tabs, the active one, and what is shared between them. */
export const session = {
  /** The tab being displayed. */
  active: null,
  tabs: [],
  /** Set when a link to an external declaration could not be resolved: checked whenever a file gets loaded. */
  pendingJump: null,
  /** Schema files of the folders opened in the browser (File ▸ Open folder…, or a dropped folder),
   *  by relative path "folder/sub/x.xsd" -> File: where links are followed without asking. */
  library: new Map(),
};
session.active = newTabState();
session.tabs.push(session.active);
