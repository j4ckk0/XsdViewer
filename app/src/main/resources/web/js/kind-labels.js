import { has, t } from './i18n.js';
import { MSG } from './message-keys.js';

/** The label of a node kind ("built-in", "complexType"...), the kind itself when there is no text for it. */
export const kindLabel = (kind) => (has(MSG.KIND_PREFIX + kind) ? t(MSG.KIND_PREFIX + kind) : kind);

/** The heading of a kind's group in the sidebar ("Elements", "Complex types"...). */
export const groupLabel = (kind) => (has(MSG.GROUP_PREFIX + kind) ? t(MSG.GROUP_PREFIX + kind) : kind);
