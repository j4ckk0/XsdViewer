/** Reading and writing the cardinality a link carries (min / max of an edge; absent on type links). */
import { CARDINALITY } from './constants.js';

export const hasCardinality = (edge) => edge.min != null && edge.max != null;

/** True for a link whose target may be absent (minOccurs 0, an optional attribute, a choice branch). */
export const isOptional = (edge) => hasCardinality(edge) && edge.min === 0;

/** "1", "0..1", "1..*", "0..*", "2..6"; "" when the link has no cardinality. */
export function cardinalityText(edge) {
  if (!hasCardinality(edge)) return '';
  const max = edge.max === CARDINALITY.UNBOUNDED ? CARDINALITY.MANY : String(edge.max);
  return edge.min === edge.max ? String(edge.min) : edge.min + CARDINALITY.RANGE + max;
}
