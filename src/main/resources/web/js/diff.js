/** Line diff of two texts (longest common subsequence), for the side-by-side comparison of two files. */

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
  return ops;
}
