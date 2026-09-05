/**
 * The "business" lines of a schema text: what remains once XML comments and xs:annotation blocks
 * (documentation, appinfo) are removed, the wiring tags dropped (the XML declaration, the xs:schema
 * root tags, xs:import and xs:include), blank lines dropped and indentation ignored — the lines
 * that define the schema, for comparing two versions without the noise.
 */
const COMMENT_START = '<!--', COMMENT_END = '-->';
const ANNOTATION_START = /<(?:[\w.-]+:)?annotation(?=[\s>/])/;
const ANNOTATION_END = /<\/(?:[\w.-]+:)?annotation\s*>/;
/** A tag dropped up to its ">" (its content, for xs:schema, stays): "<?xml", "<xs:schema", "</xs:schema>", "<xs:import", "<xs:include". */
const DROPPED_TAG_START = /<(?:\?xml|\/?(?:[\w.-]+:)?(?:schema|import|include))(?=[\s>/?])/;
const SELF_CLOSING_END = '/>';
const TAG_END = '>';
const WHITESPACE_RUN = /\s+/g;
const LINE_BREAK = /\r?\n/;

/** [{n, text}]: the business lines with their 1-based number in the original text, whitespace runs collapsed. */
export function businessLines(text) {
  const out = [];
  let inComment = false, inAnnotation = false, inDroppedTag = false;
  text.split(LINE_BREAK).forEach((line, i) => {
    let kept = '';
    let rest = line;
    while (rest.length) {
      if (inComment) {
        const end = rest.indexOf(COMMENT_END);
        if (end < 0) { rest = ''; break; }
        rest = rest.slice(end + COMMENT_END.length);
        inComment = false;
      } else if (inAnnotation) {
        const m = ANNOTATION_END.exec(rest);
        if (!m) { rest = ''; break; }
        rest = rest.slice(m.index + m[0].length);
        inAnnotation = false;
      } else if (inDroppedTag) {
        const end = rest.indexOf(TAG_END);
        if (end < 0) { rest = ''; break; }
        rest = rest.slice(end + TAG_END.length);
        inDroppedTag = false;
      } else {
        const comment = rest.indexOf(COMMENT_START);
        const annotation = ANNOTATION_START.exec(rest);
        const dropped = DROPPED_TAG_START.exec(rest);
        const next = Math.min(comment < 0 ? Infinity : comment, annotation ? annotation.index : Infinity, dropped ? dropped.index : Infinity);
        if (next === Infinity) { kept += rest; rest = ''; break; }
        kept += rest.slice(0, next);
        rest = rest.slice(next);
        if (next === comment) { inComment = true; rest = rest.slice(COMMENT_START.length); }
        else if (dropped && next === dropped.index) inDroppedTag = true;   // dropped up to its ">" above, on this line or a later one
        else {
          const close = rest.indexOf(TAG_END);
          if (close >= 0 && rest.slice(0, close + 1).endsWith(SELF_CLOSING_END)) rest = rest.slice(close + 1);   // <xs:annotation/>
          else { inAnnotation = true; rest = close >= 0 ? rest.slice(close + 1) : ''; }
        }
      }
    }
    const business = kept.replace(WHITESPACE_RUN, ' ').trim();
    if (business) out.push({ n: i + 1, text: business });
  });
  return out;
}
