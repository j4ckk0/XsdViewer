package org.jtools.xsdviewer;

/** Keys of the texts in {@code messages*.properties}; see {@link Messages}. */
public final class MessageKey {

    private MessageKey() {}

    // command line
    public static final String USAGE = "cli.usage";
    public static final String OPTION_VALUE_EXPECTED = "cli.optionValueExpected";
    public static final String INVALID_PORT = "cli.invalidPort";
    public static final String NOT_A_FILE = "cli.notAFile";

    // console
    public static final String SERVER_LISTENING = "server.listening";
    public static final String SERVER_QUIT_REQUESTED = "server.quitRequested";

    // HTTP / API errors
    public static final String POST_EXPECTED = "http.postExpected";
    public static final String NOT_FOUND = "http.notFound";
    public static final String NO_INITIAL_FILE = "api.noInitialFile";
    public static final String LOCATION_EXPECTED = "api.locationExpected";
    public static final String REMOTE_LOCATION_NOT_SUPPORTED = "api.remoteLocationNotSupported";
    public static final String FILE_NOT_FOUND = "api.fileNotFound";
    public static final String FILE_NAME_EXPECTED = "api.fileNameExpected";
    public static final String NO_FILE_WITH_CONTENT = "api.noFileWithContent";

    // schema parsing
    public static final String NOT_A_SCHEMA = "xsd.notASchema";
    public static final String EXTERNAL_DECLARATION_DOC = "xsd.externalDeclarationDoc";
    public static final String BUILTIN_TYPE_DOC = "xsd.builtinTypeDoc";
}
