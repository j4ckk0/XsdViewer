/** Line diff of two texts (longest common subsequence) with moved blocks recognised, for the side-by-side comparison of two files. */

/** Beyond this many DP cells (after trimming the common start and end) no line diff is computed. */
const MAX_CELLS = 9_000_000;
const LINE_BREAK = /\r?\n/;

export const OP = { EQUAL: '=', DELETE: '-', INSERT: '+' };

export const splitLines = (text) => text.split(LINE_BREAK);

/** The edit script turning lines {@code a} into {@code b}: [{op, a, b}] with the line indexes; null when they are too different to align within MAX_CELLS. */
export function diffLines(a, b) {
  let start = 0;
  while (start < a.length && start < b.length && a[start] === b[start]) start++;
  let endA = a.length, endB = b.length;
  while (endA > start && endB > start && a[endA - 1] === b[endB - 1]) { endA--; endB--; }
  const n = endA - start, m = endB - start;
  if (n * m > MAX_CELLS) return null;

  // lcs[i][j]: length of the longest common subsequence of a[start+i..endA) and b[start+j..endB)
  const w = m + 1;
  const lcs = new Uint16Array((n + 1) * (m + 1));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      lcs[i * w + j] = a[start + i] === b[start + j]
        ? lcs[(i + 1) * w + j + 1] + 1
        : Math.max(lcs[(i + 1) * w + j], lcs[i * w + j + 1]);
    }
  }
  const ops = [];
  for (let k = 0; k < start; k++) ops.push({ op: OP.EQUAL, a: k, b: k });
  let i = 0, j = 0;
  while (i < n || j < m) {
    if (i < n && j < m && a[start + i] === b[start + j]) { ops.push({ op: OP.EQUAL, a: start + i, b: start + j }); i++; j++; }
    else if (j >= m || (i < n && lcs[(i + 1) * w + j] >= lcs[i * w + j + 1])) { ops.push({ op: OP.DELETE, a: start + i }); i++; }
    else { ops.push({ op: OP.INSERT, b: start + j }); j++; }
  }
  for (let k = 0; k < a.length - endA; k++) ops.push({ op: OP.EQUAL, a: endA + k, b: endB + k });
  markMoves(ops, a, b);
  return ops;
}

/** A block is a move from this many lines on; a single line counts when it is the only unmatched line with that text on each side. */
const MIN_MOVED_RUN = 2;
const MAX_MOVE_SEARCH = 200;

/** The runs of consecutive ops of one kind: [{ops: [op...]}]. */
function runsOf(ops, kind) {
  const runs = [];
  let current = null;
  for (const op of ops) {
    if (op.op !== kind) { current = null; continue; }
    if (!current) { current = { ops: [] }; runs.push(current); }
    current.ops.push(op);
  }
  return runs;
}

/** The longest common contiguous sub-run of two runs over their unmarked lines: {length, x, y} (positions in the runs). */
function longestCommon(xs, ys, marked) {
  let best = { length: 0, x: 0, y: 0 };
  let previous = new Int32Array(ys.length + 1);
  for (let i = 1; i <= xs.length; i++) {
    const row = new Int32Array(ys.length + 1);
    for (let j = 1; j <= ys.length; j++) {
      if (!marked(xs[i - 1]) && !marked(ys[j - 1]) && xs[i - 1].text === ys[j - 1].text) {
        row[j] = previous[j - 1] + 1;
        if (row[j] > best.length) best = { length: row[j], x: i - row[j], y: j - row[j] };
      }
    }
    previous = row;
  }
  return best;
}

/**
 * Marks the deleted lines that reappear as inserted lines elsewhere (and those inserted lines):
 * {@code op.moved} = true, {@code op.movedTo} / {@code op.movedFrom} = the counterpart's line
 * index. Greedy on the longest common block first, so that a moved block edited a little shows
 * as a move plus the edit.
 */
function markMoves(ops, a, b) {
  const dels = runsOf(ops, OP.DELETE).map(r => r.ops.map(op => ({ op, text: a[op.a] })));
  const inss = runsOf(ops, OP.INSERT).map(r => r.ops.map(op => ({ op, text: b[op.b] })));
  if (!dels.length || !inss.length) return;
  const marked = (l) => !!l.op.moved;
  const unmatched = (runs, text) => runs.reduce((n, r) => n + r.filter(l => !marked(l) && l.text === text).length, 0);
  const telling = (l) => unmatched(dels, l.text) === 1 && unmatched(inss, l.text) === 1;
  for (let round = 0; round < MAX_MOVE_SEARCH; round++) {
    let best = null;
    for (const d of dels) {
      for (const i of inss) {
        const c = longestCommon(d, i, marked);
        if (c.length && (!best || c.length > best.length)) best = { d, i, ...c };
      }
    }
    if (!best || (best.length < MIN_MOVED_RUN && !telling(best.d[best.x]))) return;
    const from = best.d[best.x].op, to = best.i[best.y].op;
    for (let k = 0; k < best.length; k++) {
      Object.assign(best.d[best.x + k].op, { moved: true, movedTo: to.b + k });
      Object.assign(best.i[best.y + k].op, { moved: true, movedFrom: from.a + k });
    }
  }
}

/** True when the texts differ only by moved blocks. */
export const onlyMoves = (ops) => ops.some(o => o.moved) && ops.every(o => o.op === OP.EQUAL || o.moved);
