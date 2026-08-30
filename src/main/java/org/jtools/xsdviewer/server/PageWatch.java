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

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jtools.xsdviewer.Log;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;

/**
 * The pages currently open on the server, and the automatic stop once the last one has gone.
 * <p>
 * Each page announces itself with a {@code GET /api/alive?id=} kept open for its whole life
 * ({@link AliveHandler}) and, when it can, a {@code POST /api/bye?id=} as it closes
 * ({@link ByeHandler}). When no page is left for {@link #GRACE} — long enough for a reload or
 * a browser restart, and for a laptop waking up — {@link #watch} runs the stop. Nothing
 * happens before a first page has been seen (the browser may be slow to open), and nothing
 * happens at all without {@link #watch} ({@code --keep-alive}).
 */
final class PageWatch {

    /** How long the server waits, with no page open, before stopping. */
    static final Duration GRACE = Duration.ofSeconds(15);
    private static final Duration CHECK_EVERY = Duration.ofSeconds(1);

    private final Set<String> pages = ConcurrentHashMap.newKeySet();
    private final Duration grace;
    private final Duration checkEvery;
    private volatile boolean seenOnce;
    private volatile Instant emptySince;
    private ScheduledExecutorService scheduler;

    PageWatch() {
        this(GRACE, CHECK_EVERY);
    }

    /** Tests: shorter delays. */
    PageWatch(Duration grace, Duration checkEvery) {
        this.grace = grace;
        this.checkEvery = checkEvery;
    }

    void opened(String pageId) {
        pages.add(pageId);
        seenOnce = true;
        emptySince = null;
    }

    void closed(String pageId) {
        pages.remove(pageId);
    }

    int count() {
        return pages.size();
    }

    /** Starts the periodic check; {@code stop} runs once, from the check thread, when the grace has elapsed with no page. */
    synchronized void watch(Runnable stop) {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name(getClass().getSimpleName()).unstarted(r));
        scheduler.scheduleAtFixedRate(() -> check(stop), checkEvery.toMillis(), checkEvery.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void check(Runnable stop) {
        if (!seenOnce || !pages.isEmpty()) {
            emptySince = null;
            return;
        }
        Instant now = Instant.now();
        if (emptySince == null) {
            emptySince = now;
        } else if (Duration.between(emptySince, now).compareTo(grace) >= 0) {
            scheduler.shutdown();
            Log.info(Messages.get(MessageKey.SERVER_NO_PAGE_LEFT, String.valueOf(grace.toSeconds())));
            stop.run();
        }
    }
}
