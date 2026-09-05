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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the text of a schema file whatever its encoding: a byte order mark decides, else UTF-8 when
 * the bytes are valid UTF-8, else the {@code encoding} of the XML declaration, else ISO-8859-1 (which
 * decodes anything). One Latin-1 schema in a folder must not make the whole folder unreadable.
 */
final class SchemaText {

    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
    private static final byte[] UTF16BE_BOM = { (byte) 0xFE, (byte) 0xFF };
    private static final byte[] UTF16LE_BOM = { (byte) 0xFF, (byte) 0xFE };
    /** The XML declaration's encoding, in the first bytes of the file (an ASCII-compatible prolog). */
    private static final Pattern DECLARED_ENCODING = Pattern.compile("^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");
    private static final int PROLOG_LENGTH = 200;

    private SchemaText() {}

    static String read(Path file) throws IOException {
        return decode(Files.readAllBytes(file));
    }

    static String decode(byte[] bytes) {
        if (startsWith(bytes, UTF8_BOM)) return new String(bytes, UTF8_BOM.length, bytes.length - UTF8_BOM.length, StandardCharsets.UTF_8);
        if (startsWith(bytes, UTF16BE_BOM)) return new String(bytes, StandardCharsets.UTF_16BE).substring(1);
        if (startsWith(bytes, UTF16LE_BOM)) return new String(bytes, StandardCharsets.UTF_16LE).substring(1);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, declaredCharset(bytes));
        }
    }

    /** The charset the XML declaration names when Java knows it, else ISO-8859-1. */
    private static Charset declaredCharset(byte[] bytes) {
        String prolog = new String(bytes, 0, Math.min(bytes.length, PROLOG_LENGTH), StandardCharsets.ISO_8859_1);
        Matcher m = DECLARED_ENCODING.matcher(prolog);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (IllegalArgumentException e) { /* unknown to Java: fall through */ }
        }
        return StandardCharsets.ISO_8859_1;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }
}
