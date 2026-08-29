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

final class HttpStatus {

    private HttpStatus() {}

    static final int OK = 200;
    static final int BAD_REQUEST = 400;
    static final int NOT_FOUND = 404;
    static final int METHOD_NOT_ALLOWED = 405;
    /** The request is fine but the server cannot do it here (no display for a dialog). */
    static final int CONFLICT = 409;
    static final int INTERNAL_ERROR = 500;
}
