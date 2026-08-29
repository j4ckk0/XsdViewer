/** Names shared with the server (the API contract) and the fixed vocabulary of the page. */

export const APP_NAME = 'XsdViewer';
export const PROJECT_URL = 'https://github.com/j4ckk0/XsdViewer';
export const LICENSE_URL = 'https://www.apache.org/licenses/LICENSE-2.0';

/** Kinds of node of the schema graph (see NodeKind on the server). */
export const NODE_KIND = {
  ELEMENT: 'element',
  COMPLEX_TYPE: 'complexType',
  SIMPLE_TYPE: 'simpleType',
  GROUP: 'group',
  ATTRIBUTE_GROUP: 'attributeGroup',
  ATTRIBUTE: 'attribute',
  BUILTIN: 'builtin',
  EXTERNAL: 'external',
};
export const KINDS = [
  NODE_KIND.ELEMENT, NODE_KIND.COMPLEX_TYPE, NODE_KIND.SIMPLE_TYPE, NODE_KIND.GROUP,
  NODE_KIND.ATTRIBUTE_GROUP, NODE_KIND.ATTRIBUTE, NODE_KIND.BUILTIN, NODE_KIND.EXTERNAL,
];
/** Kind in the id of an external type reference ("type:X"): the server could not tell complexType from simpleType. */
export const TYPE_REFERENCE_KIND = 'type';
/** Node ids are "kind:name". */
export const ID_SEPARATOR = ':';
export const nodeId = (kind, name) => kind + ID_SEPARATOR + name;
export const kindOfId = (id) => id.slice(0, id.indexOf(ID_SEPARATOR));

/** Edge labels that name an XSD construct rather than an element / attribute (LinkLabel on the server). */
export const LINK_LABEL = {
  TYPE: 'type', REF: 'ref', ATTRIBUTE_REF: 'attribute ref', SUBSTITUTES: 'substitutes', GROUP: 'group',
  ATTRIBUTE_GROUP: 'attributeGroup', EXTENDS: 'extends', RESTRICTS: 'restricts', LIST_OF: 'list of', UNION_OF: 'union of',
  /** "attribute <name>": a nested attribute's type link. */
  ATTRIBUTE_PREFIX: 'attribute ',
};
export const STRUCTURAL_LINK_LABELS = new Set([
  LINK_LABEL.TYPE, LINK_LABEL.REF, LINK_LABEL.ATTRIBUTE_REF, LINK_LABEL.SUBSTITUTES, LINK_LABEL.GROUP,
  LINK_LABEL.ATTRIBUTE_GROUP, LINK_LABEL.EXTENDS, LINK_LABEL.RESTRICTS, LINK_LABEL.LIST_OF, LINK_LABEL.UNION_OF,
]);

/** Cardinality of a link (edge.min / edge.max, absent on type links); how it is written. */
export const CARDINALITY = { UNBOUNDED: -1, RANGE: '..', MANY: '*' };

/** Tags of the schema's imports (SchemaGraph.Import.tag). */
export const IMPORT_TAG = { IMPORT: 'import', INCLUDE: 'include', REDEFINE: 'redefine' };

export const VIEW = { GRAPH: 'graph', TEXT: 'text' };

// ---- server API (ApiPath on the server) ----
export const API = {
  PARSE: '/api/parse',
  INITIAL: '/api/initial',
  OPEN: '/api/open',
  LOCATE: '/api/locate',
  QUIT: '/api/quit',
  CAPABILITIES: '/api/capabilities',
  CHOOSE: '/api/choose',
  CHOOSE_FOLDER: '/api/choose-folder',
  WORKSPACE_SAVE: '/api/workspace/save',
  WORKSPACE_OPEN: '/api/workspace/open',
};
export const API_PARAM = { NAME: 'name', BASE: 'base', LOCATION: 'location', STRICT: 'strict' };
export const HTTP = {
  POST: 'POST',
  CONTENT_TYPE_HEADER: 'Content-Type',
  TEXT_PLAIN_UTF8: 'text/plain; charset=utf-8',
  JSON: 'application/json',
  ACCEPT_LANGUAGE_HEADER: 'Accept-Language',
};

// ---- files ----
/** A schemaLocation with a scheme (http://...) is never fetched. */
export const REMOTE_LOCATION_MARK = '://';
/** Files of an opened folder worth keeping at hand for following links. */
export const SCHEMA_FILE_PATTERN = /\.(xsd|xml)$/i;
/** Files of an opened folder that are opened as tabs. */
export const XSD_FILE_PATTERN = /\.xsd$/i;
/** At most this many schemas of a folder opened in the browser are listed. */
export const MAX_FOLDER_FILES = 2000;
/** A folder, a workspace or a set of linked schemas larger than this is only listed in the Files panel: one tab opens. */
export const MAX_AUTO_OPEN = 10;
/** Prefix of the identity of a file known only by its name (opened in the browser, not located yet). */
export const NAME_KEY_PREFIX = 'name:';
/** Suffix of a workspace file (Workspace.FILE_SUFFIX on the server); a workspace is named after the rest. */
export const WORKSPACE_FILE_SUFFIX = '.xsdviewer.json';
/** Prefix of the identity of a file that came from an opened folder (as opposed to a server path). */
export const LIBRARY_KEY_PREFIX = 'lib:';
export const PATH_SEPARATOR = '/';

// ---- browser ----
export const STORAGE_KEY = {
  TWO_LEVELS: 'xsdviewer.twoLevels',
  DETAILS_COLLAPSED: 'xsdviewer.detailsCollapsed',
  SCHEMA_INFO_COLLAPSED: 'xsdviewer.schemaInfoCollapsed',
  FILES_COLLAPSED: 'xsdviewer.filesCollapsed',
  COMPARE_BUSINESS_ONLY: 'xsdviewer.compareBusinessOnly',
  COMPARE_DIFF_ONLY: 'xsdviewer.compareDiffOnly',
  LANGUAGE: 'xsdviewer.language',
};
export const STORAGE_TRUE = '1';
export const STORAGE_FALSE = '0';
export const MIME = { PNG: 'image/png', SVG: 'image/svg+xml;charset=utf-8' };
export const SVG_NS = 'http://www.w3.org/2000/svg';
/** dataTransfer.types entry of a drag that carries files. */
export const DATA_TRANSFER_FILES = 'Files';
export const DROP_EFFECT_COPY = 'copy';
export const KEY = { ESCAPE: 'Escape', ARROW_LEFT: 'ArrowLeft', OPEN: 'o', FIND: 'f', SAVE: 's' };
export const TEXT = { LIST_SEPARATOR: ', ', TOAST_SEPARATOR: ' — ' };
export const MIDDLE_BUTTON = 1;
