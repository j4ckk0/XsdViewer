/** Syntax colouring of XML text, without any parsing: comments, processing instructions, tags, attributes, values. */
import { esc } from './dom.js';
import { TOKEN_CLS } from './dom-names.js';

const COMMENT_START = '<!--', COMMENT_END = '-->';
const PI_START = '<?', PI_END = '?>';
const CDATA_START = '<![CDATA[', CDATA_END = ']]>';

/** Tokenises XML into highlighted HTML, one entry per source line (spans never cross a line). */
export function highlightXml(text) {
  const out = [];
  const push = (cls, s) => {
    const parts = s.split('\n');
    for (let k = 0; k < parts.length; k++) {
      if (k) out.push('\n');
      if (parts[k]) out.push(cls ? '<span class="' + cls + '">' + esc(parts[k]) + '</span>' : esc(parts[k]));
    }
  };
  const n = text.length;
  const reName = /<\/?[^\s>\/]*/y, reWs = /\s+/y, reAttr = /[^\s=>\/]+/y;
  let i = 0;
  while (i < n) {
    if (text.startsWith(COMMENT_START, i)) {
      let j = text.indexOf(COMMENT_END, i + COMMENT_START.length); j = j < 0 ? n : j + COMMENT_END.length;
      push(TOKEN_CLS.COMMENT, text.slice(i, j)); i = j; continue;
    }
    if (text.startsWith(PI_START, i)) {
      let j = text.indexOf(PI_END, i + PI_START.length); j = j < 0 ? n : j + PI_END.length;
      push(TOKEN_CLS.PI, text.slice(i, j)); i = j; continue;
    }
    if (text.startsWith(CDATA_START, i)) {
      let j = text.indexOf(CDATA_END, i); j = j < 0 ? n : j + CDATA_END.length;
      push(TOKEN_CLS.VALUE, text.slice(i, j)); i = j; continue;
    }
    if (text[i] === '<') {
      reName.lastIndex = i;
      const m = reName.exec(text);
      push(TOKEN_CLS.TAG, m[0]); i += m[0].length;
      while (i < n && text[i] !== '>') {
        if (text[i] === '/') { push(TOKEN_CLS.TAG, '/'); i++; continue; }
        reWs.lastIndex = i;
        const ws = reWs.exec(text);
        if (ws) { push(null, ws[0]); i += ws[0].length; continue; }
        reAttr.lastIndex = i;
        const a = reAttr.exec(text);
        if (a) { push(TOKEN_CLS.ATTRIBUTE, a[0]); i += a[0].length; continue; }
        if (text[i] === '=') {
          push(null, '='); i++;
          if (text[i] === '"' || text[i] === "'") {
            let j = text.indexOf(text[i], i + 1); j = j < 0 ? n : j + 1;
            push(TOKEN_CLS.VALUE, text.slice(i, j)); i = j;
          }
          continue;
        }
        push(null, text[i]); i++;
      }
      if (i < n) { push(TOKEN_CLS.TAG, '>'); i++; }
      continue;
    }
    let j = text.indexOf('<', i); if (j < 0) j = n;
    push(null, text.slice(i, j)); i = j;
  }
  return out.join('').split('\n');
}
