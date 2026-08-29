package org.jtools.xsdviewer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/** Opens a URL in the user's default browser: {@link Desktop} when available, else {@code xdg-open}. */
public final class BrowserLauncher {

    private static final String XDG_OPEN = "xdg-open";

    private BrowserLauncher() {}

    public static void open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // fall through to xdg-open
        }
        try {
            new ProcessBuilder(XDG_OPEN, url).start();
        } catch (IOException ignored) {
            // nothing more to try; the URL is printed on the console
        }
    }
}
