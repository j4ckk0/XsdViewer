/**
 * The busy indicator of the top bar: a spinning wheel and a label, shown while long actions run
 * (opening files, reading a folder, parsing listed files, looking for linked schemas). Several
 * actions may run at once: the wheel stays until the last one ends, the label is the latest one's.
 */
import { $ } from './dom.js';
import { CLS, ID } from './dom-names.js';

/** Quick actions do not flash the wheel: it appears only when an action lasts longer than this. */
const SHOW_DELAY_MS = 200;

const tasks = [];
let showTimer = null;

function render() {
  const el = $(ID.BUSY);
  if (!tasks.length) {
    clearTimeout(showTimer); showTimer = null;
    el.classList.add(CLS.HIDDEN);
    return;
  }
  $(ID.BUSY_LABEL).textContent = tasks[tasks.length - 1].label;
  if (!showTimer && el.classList.contains(CLS.HIDDEN)) showTimer = setTimeout(() => { showTimer = null; if (tasks.length) el.classList.remove(CLS.HIDDEN); }, SHOW_DELAY_MS);
}

/** Starts a task: {update(label), end()}. Always end it (in a finally). */
export function beginBusy(label) {
  const task = { label };
  tasks.push(task);
  render();
  return {
    update(newLabel) { task.label = newLabel; render(); },
    end() { const i = tasks.indexOf(task); if (i >= 0) tasks.splice(i, 1); render(); },
  };
}

/** Runs {@code action} (a promise or an async function) with the wheel showing {@code label}; returns its result. */
export async function busy(label, action) {
  const task = beginBusy(label);
  try {
    return await (typeof action === 'function' ? action() : action);
  } finally {
    task.end();
  }
}
