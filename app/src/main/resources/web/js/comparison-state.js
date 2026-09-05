/**
 * The state of the comparison: a place of its own on the workspace bar, not a view of a file nor a workspace. It
 * holds two sections — the Objects one comparing two declarations, the Files one comparing two
 * workspaces — and, for its Objects section, a view of its own (model, text or graph), so switching
 * there leaves every tab's view where its reader left it.
 *
 * A declaration is put on the left or on the right from its details panel, each side chosen, so
 * which side it lands on is never a matter of the order things were picked in. A side keeps the
 * tab, the workspace and the file of its declaration: it survives its tab closing, and is emptied
 * when its workspace goes ({@code tabs.js}). The boxes put aside in the drawn models belong to the
 * pair: they are forgotten whenever a side changes.
 */
import { COMPARE_SECTION, VIEW } from './constants.js';
import { clearSelection, canCompare } from './workspace-selection.js';
import { placeOfEntry } from './declaration-lookup.js';
import { session } from './state.js';

/** The two sides, each holding one declaration or nothing. */
export const SIDE = { LEFT: 'left', RIGHT: 'right' };
export const SIDES = [SIDE.LEFT, SIDE.RIGHT];

/** Where a marked declaration lives and which one it is; the tab may close, the file of its workspace stays. */
const markOf = (tab, id) => ({ tab, ws: tab.workspace, entry: tab.file, fileName: tab.fileName, id });

const holds = (mark, tab, id) => !!mark && mark.tab === tab && mark.id === id;

/** What a side holds, or null: the declaration itself, wherever it lives. */
export const heldBy = (side) => session.compared[side];

/** The side holding this declaration, or null: what the details panel says of it. */
export const sideOf = (tab, id) => SIDES.find(side => holds(session.compared[side], tab, id)) || null;

/** The boxes of the drawn models put aside, by the trail the server gives each box. */
export const foldedBoxes = () => session.comparedFolded;

/** Puts a declaration on one side, replacing what that side held; clicking the side it already holds takes it off. */
export function markSide(side, tab, id) {
  session.compared[side] = holds(session.compared[side], tab, id) ? null : markOf(tab, id);
  foldedBoxes().clear();
}

export const clearMarks = () => { session.compared = { left: null, right: null }; foldedBoxes().clear(); };

export function swapSides() {
  const { left, right } = session.compared;
  session.compared = { left: right, right: left };
  foldedBoxes().clear();   // what only one side had swapped sides with it
}

/** The two declarations to draw, or null while either side is empty. */
export function comparedPair() {
  const { left, right } = session.compared;
  return left && right ? [left, right] : null;
}

/** The place a marked declaration is read from: its tab while it is open and parsed, else its file in the workspace. */
export function placeOf(mark) {
  if (mark.tab && session.tabs.includes(mark.tab) && mark.tab.model) return mark.tab;
  return mark.entry && mark.entry.model ? placeOfEntry(mark.entry, mark.ws) : null;
}

/**
 * ⇄ Compare: the chip appears on the workspace bar if it is not there, and the comparison is the
 * place shown. It opens on the section the selection is ready for: two workspaces picked on the bar
 * are a file-by-file comparison, anything else the objects, which the details panel fills.
 */
export function openComparison() {
  session.comparison.open = true;
  session.comparison.shown = true;
  session.comparison.section = canCompare() ? COMPARE_SECTION.FILES : COMPARE_SECTION.OBJECTS;
}

/** Its × : the place goes and takes what it was comparing with it, so it opens on nothing next time. */
export function closeComparison() {
  session.comparison = { open: false, shown: false, section: COMPARE_SECTION.OBJECTS, view: VIEW.MODEL };
  clearSelection();
  clearMarks();
}

/** Which of the two comparisons the place draws. */
export function showSection(section) {
  session.comparison.section = section;
}
