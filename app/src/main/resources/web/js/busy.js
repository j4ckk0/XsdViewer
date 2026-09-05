/**
 * The busy indicator of the top bar: a spinning wheel and a label, shown while long actions run
 * (opening files, reading a folder, parsing listed files, looking for linked schemas). Several
 * actions may run at once: the wheel stays until the last one ends, the label is the latest one's.
 */
import { $ } from './dom.js';
import { CLS, ID } from './dom-names.js';

/** A task shows the Stop button while it carries a way to cancel it and has not been cancelled yet. */
const cancellable = (task) => task.onCancel && !task.cancelled;

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
  // the Stop button appears as soon as a task that can be stopped is running (a folder being read or parsed)
  $(ID.BUSY_STOP).classList.toggle(CLS.HIDDEN, !tasks.some(cancellable));
  if (!showTimer && el.classList.contains(CLS.HIDDEN)) showTimer = setTimeout(() => { showTimer = null; if (tasks.length) el.classList.remove(CLS.HIDDEN); }, SHOW_DELAY_MS);
}

/**
 * Starts a task: {update(label), end()}. Always end it (in a finally). {@code onCancel}, when given,
 * makes the task stoppable — the Stop button shows while it runs, and calls {@code onCancel} once.
 */
export function beginBusy(label, onCancel = null) {
  const task = { label, onCancel, cancelled: false };
  tasks.push(task);
  render();
  return {
    update(newLabel) { task.label = newLabel; render(); },
    end() { const i = tasks.indexOf(task); if (i >= 0) tasks.splice(i, 1); render(); },
  };
}

/** Stop: cancels every running task that can be, each once. The tasks end themselves once they notice. */
export function cancelLoading() {
  for (const task of tasks) if (cancellable(task)) { task.cancelled = true; try { task.onCancel(); } catch (e) { /* a cancel that throws must not stop the others */ } }
  render();
}

/** Runs {@code action} with the wheel showing {@code label}; {@code onCancel} makes it stoppable. Returns the result. */
export async function busy(label, action, onCancel = null) {
  const task = beginBusy(label, onCancel);
  try {
    return await (typeof action === 'function' ? action() : action);
  } finally {
    task.end();
  }
}
