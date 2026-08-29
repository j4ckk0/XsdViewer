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

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.JFileChooser;
import javax.swing.JFrame;

/**
 * Native file dialogs shown by the server, on the machine it runs on: the browser never reveals
 * where a chosen file is, the operating system's dialog does. One dialog at a time; each call
 * blocks until the user answers. Unavailable when the JVM is headless (no display).
 */
final class FileDialogs {

    private static String lastDirectory;

    private FileDialogs() {}

    static boolean available() {
        return !GraphicsEnvironment.isHeadless();
    }

    /** The files chosen in an "open" dialog (empty when cancelled); {@code accept} filters by file name where the platform supports it. */
    static synchronized List<Path> chooseFilesToOpen(String title, boolean multiple, Predicate<String> accept) {
        FileDialog d = new FileDialog((Frame) null, title, FileDialog.LOAD);
        d.setMultipleMode(multiple);
        if (lastDirectory != null) d.setDirectory(lastDirectory);
        d.setFilenameFilter((dir, name) -> accept.test(name));
        List<Path> out = new ArrayList<>();
        for (File f : show(d)) out.add(f.toPath().toAbsolutePath().normalize());
        return out;
    }

    /** The file chosen in a "save as" dialog, or null when cancelled. */
    static synchronized Path chooseFileToSave(String title, Path directory, String defaultName) {
        FileDialog d = new FileDialog((Frame) null, title, FileDialog.SAVE);
        d.setDirectory(directory != null ? directory.toString() : lastDirectory);
        d.setFile(defaultName);
        File[] files = show(d);
        return files.length == 0 ? null : files[0].toPath().toAbsolutePath().normalize();
    }

    /** The folder chosen in a "choose folder" dialog (a Swing chooser: the native dialog cannot pick folders), or null when cancelled. */
    static synchronized Path chooseFolder(String title) {
        Path[] result = { null };
        Thread t = Thread.ofPlatform().start(() -> {
            JFrame owner = new JFrame();   // an invisible always-on-top owner, so the chooser comes over the browser
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
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    result[0] = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
                    lastDirectory = result[0].toString();
                }
            } finally {
                owner.dispose();
            }
        });
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /** Shows the dialog on a platform thread (AWT is happier there than on a virtual one) and waits for it. */
    private static File[] show(FileDialog d) {
        File[][] result = { new File[0] };
        Thread t = Thread.ofPlatform().start(() -> {
            try {
                d.setAlwaysOnTop(true);   // the browser window is in front: come over it
            } catch (SecurityException ignored) { /* then the dialog may open behind */ }
            d.setVisible(true);           // blocks until closed
            result[0] = d.getFiles();
            if (d.getDirectory() != null) lastDirectory = d.getDirectory();
            d.dispose();
        });
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }
}
