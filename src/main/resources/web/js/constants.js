/** Names shared with the server (the API contract) and the fixed vocabulary of the page. */

export const APP_NAME = 'XsdViewer';
export const PROJECT_URL = 'https://github.com/j4ckk0/XsdViewer';
export const LICENSE_URL = 'https://www.apache.org/licenses/LICENSE-2.0';

/** Kinds of node of the schema graph (see NodeKind on the server): those of a WSDL, of a Schematron, of an XML Schema. */
export const NODE_KIND = {
  SERVICE: 'service',
  PORT_TYPE: 'portType',
  OPERATION: 'operation',
  BINDING: 'binding',
  MESSAGE: 'message',
  PHASE: 'phase',
  PATTERN: 'pattern',
  RULE: 'rule',
  ASSERT: 'assert',
  REPORT: 'report',
  DIAGNOSTIC: 'diagnostic',
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
  NODE_KIND.SERVICE, NODE_KIND.PORT_TYPE, NODE_KIND.OPERATION, NODE_KIND.BINDING, NODE_KIND.MESSAGE,
  NODE_KIND.PHASE, NODE_KIND.PATTERN, NODE_KIND.RULE, NODE_KIND.ASSERT, NODE_KIND.REPORT, NODE_KIND.DIAGNOSTIC,
  NODE_KIND.ELEMENT, NODE_KIND.COMPLEX_TYPE, NODE_KIND.SIMPLE_TYPE, NODE_KIND.GROUP,
  NODE_KIND.ATTRIBUTE_GROUP, NODE_KIND.ATTRIBUTE, NODE_KIND.BUILTIN, NODE_KIND.EXTERNAL,
];
/** The kinds declared by a WSDL file: a model holding one is a WSDL, whose legend shows them. */
export const WSDL_KINDS = new Set([NODE_KIND.SERVICE, NODE_KIND.PORT_TYPE, NODE_KIND.OPERATION, NODE_KIND.BINDING, NODE_KIND.MESSAGE]);
/** The kinds declared by a Schematron file: a model holding one is a Schematron, whose legend shows them (and no XSD kind). */
export const SCHEMATRON_KINDS = new Set([NODE_KIND.PHASE, NODE_KIND.PATTERN, NODE_KIND.RULE, NODE_KIND.ASSERT, NODE_KIND.REPORT, NODE_KIND.DIAGNOSTIC]);
/** What kind of file a model describes (an XSD otherwise): a WSDL cannot validate a document, a Schematron has its own validator. */
export const isWsdl = (model) => model.nodes.some(n => WSDL_KINDS.has(n.kind));
export const isSchematron = (model) => model.nodes.some(n => SCHEMATRON_KINDS.has(n.kind));
/** Kind in the id of an external type reference ("type:X"): the server could not tell complexType from simpleType. */
export const TYPE_REFERENCE_KIND = 'type';
/** Node ids are "kind:name". */
export const ID_SEPARATOR = ':';
export const nodeId = (kind, name) => kind + ID_SEPARATOR + name;
export const kindOfId = (id) => id.slice(0, id.indexOf(ID_SEPARATOR));
export const nameOfId = (id) => id.slice(id.indexOf(ID_SEPARATOR) + 1);

/** Edge labels that name an XSD construct rather than an element / attribute (LinkLabel on the server). */
export const LINK_LABEL = {
  TYPE: 'type', REF: 'ref', ATTRIBUTE_REF: 'attribute ref', SUBSTITUTES: 'substitutes', GROUP: 'group',
  ATTRIBUTE_GROUP: 'attributeGroup', EXTENDS: 'extends', RESTRICTS: 'restricts', LIST_OF: 'list of', UNION_OF: 'union of',
  // WSDL: a portType to its operations, an operation to its messages, a binding to its portType
  OPERATION: 'operation', INPUT: 'input', OUTPUT: 'output', FAULT: 'fault', BINDS: 'binds',
  // Schematron: a phase to its active patterns, a pattern to its rules and to the abstract pattern it instantiates, a rule to its assertions, an assertion to its diagnostics
  ACTIVE: 'active', RULE: 'rule', IS_A: 'is a', ASSERT: 'assert', REPORT: 'report', DIAGNOSTIC: 'diagnostic',
  /** "attribute <name>": a nested attribute's type link. */
  ATTRIBUTE_PREFIX: 'attribute ',
  /** "keyref <name>": a keyref's link to the element declaring its key. */
  KEYREF_PREFIX: 'keyref ',
};
/** The xs:sequence / xs:choice / xs:all a nested element sits in (edge.compositor, absent on the other links). */
export const COMPOSITOR = { SEQUENCE: 'sequence', CHOICE: 'choice', ALL: 'all' };

/** Cardinality of a link (edge.min / edge.max, absent on type links); how it is written. */
export const CARDINALITY = { UNBOUNDED: -1, RANGE: '..', MANY: '*' };

/** Tags of the schema's imports (SchemaGraph.Import.tag). */
export const IMPORT_TAG = { IMPORT: 'import', INCLUDE: 'include', REDEFINE: 'redefine' };

export const VIEW = { GRAPH: 'graph', MODEL: 'model', TEXT: 'text' };
/** The two things the comparison compares, its sections: declarations, and the files of two workspaces. */
export const COMPARE_SECTION = { OBJECTS: 'objects', FILES: 'files' };
/** How large the drawn views can be made in their panel, and the size they are drawn at. */
export const ZOOM = { STEPS: [0.5, 0.67, 0.8, 1, 1.25, 1.5, 2, 3], DEFAULT: 1 };
/** The views drawn as SVG, which the zoom scales; the text is read at the browser's own size. */
export const ZOOMABLE_VIEWS = new Set([VIEW.GRAPH, VIEW.MODEL]);
/** The kinds of particle of a content model (ParticleKind on the server): what a box of the Model view is. */
export const PARTICLE = { SEQUENCE: 'sequence', CHOICE: 'choice', ALL: 'all', ELEMENT: 'element', GROUP: 'group', ANY: 'any', EXTENDS: 'extends', RESTRICTS: 'restricts' };
/** The themes (js/theme.js): stored once chosen, stamped on <html> as data-theme. */
export const THEME = { LIGHT: 'light', DARK: 'dark' };

// ---- server API (ApiPath on the server) ----
export const API = {
  PARSE: '/api/parse',
  INITIAL: '/api/initial',
  OPEN: '/api/open',
  LOCATE: '/api/locate',
  QUIT: '/api/quit',
  ALIVE: '/api/alive',
  BYE: '/api/bye',
  SETTINGS: '/api/settings',
  CAPABILITIES: '/api/capabilities',
  CHOOSE: '/api/choose',
  CHOOSE_FOLDER: '/api/choose-folder',
  WORKSPACE_SAVE: '/api/workspace/save',
  WORKSPACE_OPEN: '/api/workspace/open',
  VALIDATE: '/api/validate',
};
export const API_PARAM = { NAME: 'name', BASE: 'base', LOCATION: 'location', STRICT: 'strict', ID: 'id', SCHEMA: 'schema', SCHEMATRON: 'schematron', PHASE: 'phase' };
/** A validation problem (POST /api/validate): which validation found it, how bad it is (Severity on the server). */
export const PROBLEM_SOURCE = { XSD: 'xsd', SCHEMATRON: 'schematron' };
export const PROBLEM_SEVERITY = { ERROR: 'error', WARNING: 'warning', INFO: 'info', UNSUPPORTED: 'unsupported' };
/** The Schematron phase running every pattern (SchematronValidator.ALL_PHASES). */
export const ALL_PHASES = '#ALL';
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
export const SCHEMA_FILE_PATTERN = /\.(xsd|wsdl|sch|xml)$/i;
/** Files of an opened folder that are opened as tabs. */
export const XSD_FILE_PATTERN = /\.(xsd|wsdl|sch)$/i;
/** What a not yet parsed workspace file is, by its name: an XML Schema, a Schematron (a document to validate: XML_FILE_PATTERN). */
export const XSD_ONLY_FILE_PATTERN = /\.xsd$/i;
export const SCHEMATRON_FILE_PATTERN = /\.sch$/i;
export const XML_FILE_PATTERN = /\.xml$/i;
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
/** The side panels: how narrow they may get, the share of the window they may take, what an arrow key moves. */
export const PANEL = { MIN_WIDTH: 150, MAX_SHARE: 0.5, KEY_STEP: 16 };

export const STORAGE_KEY = {
  TWO_LEVELS: 'xsdviewer.twoLevels',
  SIDEBAR_WIDTH: 'xsdviewer.sidebarWidth',
  DETAILS_WIDTH: 'xsdviewer.detailsWidth',
  DETAILS_COLLAPSED: 'xsdviewer.detailsCollapsed',
  SCHEMA_INFO_COLLAPSED: 'xsdviewer.schemaInfoCollapsed',
  FILES_COLLAPSED: 'xsdviewer.filesCollapsed',
  COMPARE_BUSINESS_ONLY: 'xsdviewer.compareBusinessOnly',
  COMPARE_DIFF_ONLY: 'xsdviewer.compareDiffOnly',
  VALIDATE_ERRORS_ONLY: 'xsdviewer.validateErrorsOnly',
  HIDDEN_LINKS: 'xsdviewer.hiddenLinks',
  HIDDEN_KINDS: 'xsdviewer.hiddenKinds',
  LANGUAGE: 'xsdviewer.language',
  /** Read by js/theme-boot.js too, before the modules load. */
  THEME: 'xsdviewer.theme',
};
export const STORAGE_TRUE = '1';
export const STORAGE_FALSE = '0';
export const MIME = { PNG: 'image/png', SVG: 'image/svg+xml;charset=utf-8' };
export const SVG_NS = 'http://www.w3.org/2000/svg';
/** dataTransfer.types entry of a drag that carries files. */
export const DATA_TRANSFER_FILES = 'Files';
export const DROP_EFFECT_COPY = 'copy';
export const KEY = { ESCAPE: 'Escape', ENTER: 'Enter', SPACE: ' ', ARROW_LEFT: 'ArrowLeft', ARROW_RIGHT: 'ArrowRight', ARROW_UP: 'ArrowUp', ARROW_DOWN: 'ArrowDown', HOME: 'Home', OPEN: 'o', FIND: 'f', SAVE: 's' };
export const TEXT = { LIST_SEPARATOR: ', ', TOAST_SEPARATOR: ' — ', STORED_SEPARATOR: ',' };
export const MIDDLE_BUTTON = 1;
