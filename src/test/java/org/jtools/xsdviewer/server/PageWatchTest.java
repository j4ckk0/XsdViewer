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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/** The automatic stop: only after a first page, only once the grace has elapsed with no page, cancelled by a page coming back. */
class PageWatchTest {

    private static final Duration GRACE = Duration.ofMillis(200);
    private static final Duration CHECK = Duration.ofMillis(20);

    private static PageWatch watching(CountDownLatch stopped) {
        PageWatch w = new PageWatch(GRACE, CHECK);
        w.watch(stopped::countDown);
        return w;
    }

    @Test
    void noStopBeforeAFirstPage() throws Exception {
        CountDownLatch stopped = new CountDownLatch(1);
        watching(stopped);
        assertFalse(stopped.await(GRACE.toMillis() * 3, TimeUnit.MILLISECONDS));
    }

    @Test
    void stopsOnceTheLastPageHasBeenGoneForTheGrace() throws Exception {
        CountDownLatch stopped = new CountDownLatch(1);
        PageWatch w = watching(stopped);
        w.opened("a");
        w.opened("b");
        assertEquals(2, w.count());
        w.closed("a");
        assertFalse(stopped.await(GRACE.toMillis() * 2, TimeUnit.MILLISECONDS), "one page still open");
        w.closed("b");
        assertEquals(0, w.count());
        assertTrue(stopped.await(GRACE.toMillis() * 5, TimeUnit.MILLISECONDS), "no page left");
    }

    @Test
    void aPageBackWithinTheGraceCancelsTheStop() throws Exception {
        CountDownLatch stopped = new CountDownLatch(1);
        PageWatch w = watching(stopped);
        w.opened("a");
        w.closed("a");
        Thread.sleep(GRACE.toMillis() / 2);
        w.opened("a2");                       // a reload
        assertFalse(stopped.await(GRACE.toMillis() * 3, TimeUnit.MILLISECONDS));
    }

    @Test
    void withoutWatchNothingHappens() throws Exception {
        PageWatch w = new PageWatch(GRACE, CHECK);   // --keep-alive: watch() is never called
        w.opened("a");
        w.closed("a");
        Thread.sleep(GRACE.toMillis() * 3);
        assertEquals(0, w.count());
    }
}
