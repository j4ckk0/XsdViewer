package org.jtools.xsdviewer.server;

/*-
 * #%L
 * XsdViewer
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jtools.xsdviewer.Log;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;

/**
 * The file dialogs the server opens on behalf of the page (which cannot learn a file's path from
 * the browser's own picker). The dialog is the platform's: on Windows and macOS the native one of
 * {@code java.awt.FileDialog}; elsewhere the desktop's, through {@code kdialog} (KDE, LXQt) or
 * {@code zenity} (GNOME and others) when one is installed — the AWT dialog there is an X11 relic —,
 * else a Swing chooser in the system look and feel. One dialog at a time.
 */
final class FileDialogs {

    /** What a dialog lets pick: a description and glob patterns ({@code *.xsd}). */
    record Filter(String description, List<String> globs) {
        /** The extensions of the globs, for the toolkits that filter that way ({@code xsd}). */
        List<String> extensions() {
            return globs.stream().map(g -> g.substring(g.lastIndexOf('.') + 1)).toList();
        }

        boolean accepts(String name) {
            String n = name.toLowerCase(Locale.ROOT);
            return extensions().stream().anyMatch(ext -> n.endsWith("." + ext.toLowerCase(Locale.ROOT)));
        }
    }

    private enum Platform { WINDOWS, MAC, OTHER }

    private static final Platform PLATFORM = platform();
    private static String lastDirectory;
    private static boolean lookAndFeelSet;

    private FileDialogs() {}

    static boolean available() {
        return !GraphicsEnvironment.isHeadless();
    }

    /** The files chosen in an "open" dialog (empty when cancelled). */
    static synchronized List<Path> chooseFilesToOpen(String title, boolean multiple, Filter filter) {
        DesktopDialog desktop = DesktopDialog.find();
        if (desktop != null) {
            List<Path> chosen = desktop.run(desktop.openCommand(title, startDirectory(), multiple, filter));
            if (chosen != null) return remember(chosen);
        }
        if (PLATFORM != Platform.OTHER) {
            FileDialog d = new FileDialog((Frame) null, title, FileDialog.LOAD);
            d.setMultipleMode(multiple);
            if (lastDirectory != null) d.setDirectory(lastDirectory);
            d.setFilenameFilter((dir, name) -> filter.accepts(name));
            return remember(paths(show(d)));
        }
        return remember(swingChooser(title, false, chooser -> {
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(multiple);
            chooser.setFileFilter(new FileNameExtensionFilter(filter.description(), filter.extensions().toArray(String[]::new)));
        }));
    }

    /** The file chosen in a "save as" dialog, or null when cancelled. */
    static synchronized Path chooseFileToSave(String title, Path directory, String defaultName, Filter filter) {
        DesktopDialog desktop = DesktopDialog.find();
        if (desktop != null) {
            List<Path> chosen = desktop.run(desktop.saveCommand(title, directory != null ? directory.toString() : startDirectory(), defaultName, filter));
            if (chosen != null) return first(remember(chosen));
        }
        if (PLATFORM != Platform.OTHER) {
            FileDialog d = new FileDialog((Frame) null, title, FileDialog.SAVE);
            d.setDirectory(directory != null ? directory.toString() : lastDirectory);
            d.setFile(defaultName);
            return first(remember(paths(show(d))));
        }
        return first(remember(swingChooser(title, true, chooser -> {
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setFileFilter(new FileNameExtensionFilter(filter.description(), filter.extensions().toArray(String[]::new)));
            chooser.setSelectedFile(new File(directory != null ? directory.toString() : startDirectory(), defaultName));
        })));
    }

    /**
     * The folder chosen in a "choose folder" dialog, or null when cancelled. The AWT dialog cannot
     * pick folders: the desktop's dialog, else a Swing chooser.
     *
     * @throws IllegalStateException when the chooser itself fails (logged)
     */
    static synchronized Path chooseFolder(String title) {
        DesktopDialog desktop = DesktopDialog.find();
        if (desktop != null) {
            List<Path> chosen = desktop.run(desktop.folderCommand(title, startDirectory()));
            if (chosen != null) return first(remember(chosen));
        }
        return first(remember(swingChooser(title, false, chooser -> chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY))));
    }

    private static String startDirectory() {
        return lastDirectory != null ? lastDirectory : System.getProperty("user.home");
    }

    private static Path first(List<Path> paths) {
        return paths.isEmpty() ? null : paths.get(0);
    }

    /** Keeps the directory of what was chosen for the next dialog. */
    private static List<Path> remember(List<Path> chosen) {
        if (!chosen.isEmpty()) {
            Path p = chosen.get(0);
            lastDirectory = (Files.isDirectory(p) ? p : p.getParent() != null ? p.getParent() : p).toString();
        }
        return chosen;
    }

    private static List<Path> paths(File[] files) {
        List<Path> out = new ArrayList<>();
        for (File f : files) out.add(f.toPath().toAbsolutePath().normalize());
        return out;
    }

    private static Platform platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return Platform.WINDOWS;
        if (os.contains("mac")) return Platform.MAC;
        return Platform.OTHER;
    }

    /** Shows the AWT dialog and waits for it: the files chosen (none when cancelled). */
    private static File[] show(FileDialog d) {
        return onPlatformThread(() -> {
            try {
                d.setAlwaysOnTop(true);   // the browser window is in front: come over it
            } catch (SecurityException ignored) { /* then the dialog may open behind */ }
            d.setVisible(true);           // blocks until closed
            File[] files = d.getFiles();
            d.dispose();
            return files;
        });
    }

    /**
     * A Swing chooser in the system look and feel, on the event thread as Swing requires, over an
     * invisible always-on-top owner so that it comes over the browser: what was chosen (none when cancelled).
     *
     * @throws IllegalStateException when the chooser itself fails (logged)
     */
    private static List<Path> swingChooser(String title, boolean save, Consumer<JFileChooser> setup) {
        List<Path> chosen = new ArrayList<>();
        try {
            EventQueue.invokeAndWait(() -> {
                setSystemLookAndFeel();
                JFrame owner = new JFrame();
                owner.setUndecorated(true);
                owner.setSize(0, 0);
                owner.setLocationRelativeTo(null);
                try {
                    owner.setAlwaysOnTop(true);
                } catch (SecurityException ignored) { /* then the chooser may open behind */ }
                owner.setVisible(true);
                try {
                    JFileChooser chooser = new JFileChooser(lastDirectory);
                    chooser.setDialogTitle(title);
                    setup.accept(chooser);
                    int answer = save ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
                    if (answer != JFileChooser.APPROVE_OPTION) return;
                    if (save && chooser.getSelectedFile() != null && chooser.getSelectedFile().exists()
                            && JOptionPane.showConfirmDialog(owner, Messages.get(MessageKey.DIALOG_OVERWRITE, chooser.getSelectedFile().getName()),
                                    title, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                        return;
                    }
                    File[] files = chooser.isMultiSelectionEnabled() ? chooser.getSelectedFiles() : new File[] { chooser.getSelectedFile() };
                    for (File f : files) if (f != null) chosen.add(f.toPath().toAbsolutePath().normalize());
                } finally {
                    owner.dispose();
                }
            });
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Log.warn(Messages.get(MessageKey.DIALOG_FAILED, cause), cause);
            throw new IllegalStateException(Messages.get(MessageKey.DIALOG_FAILED, cause), cause);
        }
        return chosen;
    }

    /** The system look and feel (GTK on Linux when available), once; Metal stays when it cannot be set. */
    private static void setSystemLookAndFeel() {
        if (lookAndFeelSet) return;
        lookAndFeelSet = true;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            Log.warn(e.toString());
        }
    }

    /** Runs a blocking AWT interaction on a platform thread (AWT is happier there than on a virtual one) and waits for its result. */
    private static <T> T onPlatformThread(Supplier<T> interaction) {
        Object[] result = { null };
        Thread t = Thread.ofPlatform().start(() -> result[0] = interaction.get());
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        @SuppressWarnings("unchecked")
        T value = (T) result[0];
        return value;
    }

    /**
     * The desktop's own dialogs, as command-line tools: {@code kdialog} (KDE, LXQt) or {@code zenity}
     * (GNOME, XFCE, others), whichever the desktop suggests and the PATH holds. Exit code 0 answers
     * the chosen paths one per line, 1 means cancelled, anything else is a failure to fall back from.
     */
    enum DesktopDialog {
        KDIALOG("kdialog") {
            @Override
            List<String> openCommand(String title, String dir, boolean multiple, Filter filter) {
                List<String> cmd = new ArrayList<>(List.of(command, "--title", title, "--getopenfilename", dir, qtFilter(filter)));
                if (multiple) { cmd.add("--multiple"); cmd.add("--separate-output"); }
                return cmd;
            }

            @Override
            List<String> saveCommand(String title, String dir, String name, Filter filter) {
                return List.of(command, "--title", title, "--getsavefilename", new File(dir, name).getPath(), qtFilter(filter));
            }

            @Override
            List<String> folderCommand(String title, String dir) {
                return List.of(command, "--title", title, "--getexistingdirectory", dir);
            }

            /** {@code XML Schema files (*.xsd *.wsdl)} */
            private static String qtFilter(Filter f) {
                return f.description() + " (" + String.join(" ", f.globs()) + ")";
            }
        },
        ZENITY("zenity") {
            @Override
            List<String> openCommand(String title, String dir, boolean multiple, Filter filter) {
                List<String> cmd = new ArrayList<>(List.of(command, "--file-selection", "--title=" + title, "--filename=" + dir + File.separator, gtkFilter(filter)));
                if (multiple) { cmd.add("--multiple"); cmd.add("--separator=\n"); }
                return cmd;
            }

            @Override
            List<String> saveCommand(String title, String dir, String name, Filter filter) {
                return List.of(command, "--file-selection", "--save", "--confirm-overwrite", "--title=" + title,
                        "--filename=" + new File(dir, name).getPath(), gtkFilter(filter));
            }

            @Override
            List<String> folderCommand(String title, String dir) {
                return List.of(command, "--file-selection", "--directory", "--title=" + title, "--filename=" + dir + File.separator);
            }

            /** {@code --file-filter=XML Schema files | *.xsd *.wsdl} */
            private static String gtkFilter(Filter f) {
                return "--file-filter=" + f.description() + " | " + String.join(" ", f.globs());
            }
        };

        private static final int EXIT_CHOSEN = 0, EXIT_CANCELLED = 1;
        private static final String DESKTOP_VARIABLE = "XDG_CURRENT_DESKTOP";
        private static final List<String> KDE_LIKE = List.of("KDE", "LXQT");
        private static final String PATH_VARIABLE = "PATH";

        final String command;

        DesktopDialog(String command) {
            this.command = command;
        }

        abstract List<String> openCommand(String title, String dir, boolean multiple, Filter filter);

        abstract List<String> saveCommand(String title, String dir, String name, Filter filter);

        abstract List<String> folderCommand(String title, String dir);

        /** The tool to use on this machine, or null (Windows, macOS, or none installed): the desktop's own first, else the other one. */
        static DesktopDialog find() {
            if (PLATFORM != Platform.OTHER) return null;
            String desktop = System.getenv(DESKTOP_VARIABLE);
            boolean kde = desktop != null && KDE_LIKE.stream().anyMatch(desktop.toUpperCase(Locale.ROOT)::contains);
            for (DesktopDialog d : kde ? List.of(KDIALOG, ZENITY) : List.of(ZENITY, KDIALOG)) {
                if (d.installed()) return d;
            }
            return null;
        }

        boolean installed() {
            String path = System.getenv(PATH_VARIABLE);
            if (path == null) return false;
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, command))) return true;
            }
            return false;
        }

        /** Runs the dialog: the paths chosen (empty when cancelled), or null when the tool failed (logged) and another dialog should be tried. */
        List<Path> run(List<String> cmd) {
            try {
                Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exit = p.waitFor();
                if (exit == EXIT_CANCELLED) return List.of();
                if (exit != EXIT_CHOSEN) {
                    Log.warn(Messages.get(MessageKey.DIALOG_FAILED, command + " exit " + exit));
                    return null;
                }
                List<Path> chosen = new ArrayList<>();
                for (String line : out.split("\\R")) {
                    if (!line.isBlank()) chosen.add(Path.of(line.trim()).toAbsolutePath().normalize());
                }
                return chosen;
            } catch (IOException e) {
                Log.warn(Messages.get(MessageKey.DIALOG_FAILED, command + ": " + e));
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return List.of();
            }
        }
    }
}
