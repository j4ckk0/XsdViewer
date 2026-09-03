import { VIEW } from './constants.js';

/** Scroll positions of a tab's views, restored when the tab is shown again. */
export const newScroll = () => ({ text: 0, graphTop: 0, graphLeft: 0, modelTop: 0, modelLeft: 0 });

/** One document tab: the file, its graph and the UI state of that tab (view, selection, history...). */
export function newTabState() {
  return {
    fileName: null,
    path: null,         // file path on the server, when the server read it (initial file, followed links)
    rel: null,          // path in an opened folder, when the file came from the library
    located: null,      // promise of the server's search for the path of a file opened in the browser
    workspace: null,    // always set
    file: null,         // the entry of workspace.files shown, when the tab shows one
    compare: null,      // {left, right} workspaces when the tab is a comparison; file: the name of the one file pair shown, when the tab shows one
    validation: null,   // {name, path, text, xsd, sch, phase, result, error, selected} when the tab shows the validation of a document (validate.js)
    text: '',
    model: null,
    nodes: new Map(),
    outEdges: new Map(),
    inEdges: new Map(),
    lineToNode: new Map(),
    selected: null,
    history: [],
    view: VIEW.GRAPH,
    filter: '',
    collapsed: new Set(),
    modelExpanded: new Set(),   // the boxes of the Model view opened on demand (their paths in the tree)
    scroll: newScroll(),
  };
}

/** A workspace: a group of tabs, named after its file once saved or opened, after its number until then; links are followed within it only. */
export function newWorkspaceState(number) {
  return {
    number,
    path: null,        // its file, once saved or opened from one
    label: null,       // a name given at creation (an opened folder), used while unsaved
    lastActive: null,  // the tab shown when the workspace was last active
    /** The schema files of the workspace, open in a tab or not: {name, path, rel, text, model, failed} (see workspace-files.js). */
    files: [],
  };
}

/** Everything the page holds: the workspaces, their tabs (grouped, in workspace order), the active tab, and what is shared. */
export const session = {
  active: null,
  /** Every tab, grouped by workspace in the order of {@code workspaces}. */
  tabs: [],
  workspaces: [],
  workspaceCounter: 0,
  /** Workspaces selected with Ctrl+click on their chip, oldest first (at most two). */
  compareSelection: [],
  /** Set when a link to an external declaration could not be resolved: checked whenever a file gets loaded. */
  pendingJump: null,
  /** Schema files of the folders opened in the browser (File ▸ Open folder…, or a dropped folder),
   *  by relative path "folder/sub/x.xsd" -> File: where links are followed without asking. */
  library: new Map(),
  /** Whether the server can show native file dialogs (it has a display): files then come with their location. */
  dialogs: false,
  /** Language of the machine's locale, as the server reports it; the page's default language. */
  serverLanguage: null,
  /** Version of the tool and of its Java runtime, as the server reports them (Help > About). */
  serverVersion: null,
  javaVersion: null,
  /** The server's log file, for Help > About; null when it has none. */
  logFile: null,
};
const firstWorkspace = newWorkspaceState(++session.workspaceCounter);
session.workspaces.push(firstWorkspace);
session.active = newTabState();
session.active.workspace = firstWorkspace;
session.tabs.push(session.active);
