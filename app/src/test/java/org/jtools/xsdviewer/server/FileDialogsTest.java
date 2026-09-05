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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FileDialogsTest {

    private static final FileDialogs.Filter SCHEMAS = new FileDialogs.Filter("XML Schema files", List.of("*.xsd", "*.wsdl"));

    @Test
    void filterByExtension() {
        assertEquals(List.of("xsd", "wsdl"), SCHEMAS.extensions());
        assertTrue(SCHEMAS.accepts("a.XSD"));
        assertTrue(SCHEMAS.accepts("service.wsdl"));
        assertFalse(SCHEMAS.accepts("notes.txt"));
    }

    @Test
    void kdialogCommands() {
        assertEquals(List.of("kdialog", "--title", "Open", "--getopenfilename", "/home/me", "XML Schema files (*.xsd *.wsdl)", "--multiple", "--separate-output"),
                FileDialogs.DesktopDialog.KDIALOG.openCommand("Open", "/home/me", true, SCHEMAS));
        assertEquals(List.of("kdialog", "--title", "Open", "--getopenfilename", "/home/me", "XML Schema files (*.xsd *.wsdl)"),
                FileDialogs.DesktopDialog.KDIALOG.openCommand("Open", "/home/me", false, SCHEMAS));
        assertEquals(List.of("kdialog", "--title", "Save", "--getsavefilename", "/home/me/ws.xsdviewer.json", "XML Schema files (*.xsd *.wsdl)"),
                FileDialogs.DesktopDialog.KDIALOG.saveCommand("Save", "/home/me", "ws.xsdviewer.json", SCHEMAS));
        assertEquals(List.of("kdialog", "--title", "Folder", "--getexistingdirectory", "/home/me"),
                FileDialogs.DesktopDialog.KDIALOG.folderCommand("Folder", "/home/me"));
    }

    @Test
    void zenityCommands() {
        assertEquals(List.of("zenity", "--file-selection", "--title=Open", "--filename=/home/me/", "--file-filter=XML Schema files | *.xsd *.wsdl", "--multiple", "--separator=\n"),
                FileDialogs.DesktopDialog.ZENITY.openCommand("Open", "/home/me", true, SCHEMAS));
        assertEquals(List.of("zenity", "--file-selection", "--save", "--confirm-overwrite", "--title=Save", "--filename=/home/me/ws.xsdviewer.json", "--file-filter=XML Schema files | *.xsd *.wsdl"),
                FileDialogs.DesktopDialog.ZENITY.saveCommand("Save", "/home/me", "ws.xsdviewer.json", SCHEMAS));
        assertEquals(List.of("zenity", "--file-selection", "--directory", "--title=Folder", "--filename=/home/me/"),
                FileDialogs.DesktopDialog.ZENITY.folderCommand("Folder", "/home/me"));
    }
}
