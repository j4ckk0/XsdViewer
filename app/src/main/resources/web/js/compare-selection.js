/**
 * The workspaces selected for the Files section of the comparison: Ctrl+click on a chip selects
 * one, two at most, the oldest giving way to a third. This is the one module that changes the
 * selection; the others read it.
 */
import { session } from './state.js';

const COMPARED_WORKSPACES = 2;

export function toggleSelection(ws) {
  const sel = session.compareSelection;
  const i = sel.indexOf(ws);
  if (i >= 0) sel.splice(i, 1);
  else { sel.push(ws); if (sel.length > COMPARED_WORKSPACES) sel.shift(); }
}

export const isSelected = (ws) => session.compareSelection.includes(ws);

/** Two workspaces are selected: what a file-by-file comparison needs. */
export const canCompare = () => session.compareSelection.length === COMPARED_WORKSPACES;

/** No workspace is selected any more; the caller redraws. */
export function clearSelection() {
  session.compareSelection.length = 0;
}

/** A closed workspace leaves the selection. */
export function pruneSelection() {
  session.compareSelection = session.compareSelection.filter(ws => session.workspaces.includes(ws));
}
