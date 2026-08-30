/** Tells the server this page is open, so that it can stop by itself once the last page has gone
 *  (unless started with --keep-alive).
 *
 *  Presence is a GET /api/alive event stream held open for the page's whole life: the browser
 *  drops it when the page is closed, discarded or crashes, whatever the user did or did not do
 *  meanwhile, and no timer is involved (background tabs throttle timers, not connections).
 *  EventSource reconnects by itself after a network hiccup or a sleep, well within the server's
 *  grace. pagehide also sends a beacon (POST /api/bye) so the server needs not wait for the
 *  stream to break; pageshow from the back/forward cache reconnects. */
import { API, API_PARAM } from './constants.js';

const pageId = typeof crypto.randomUUID === 'function'
  ? crypto.randomUUID()
  : String(Date.now()) + '-' + Math.random().toString(16).slice(2);   // http://<lan-ip>: not a secure context

const withId = (path) => path + '?' + new URLSearchParams({ [API_PARAM.ID]: pageId }).toString();

let stream = null;

export function startPresence() {
  if (stream) return;
  stream = new EventSource(withId(API.ALIVE));
}

/** Closes the stream; with {@code sayBye} also tells the server right away (a beacon survives the page). */
export function stopPresence(sayBye = true) {
  if (!stream) return;
  stream.close();
  stream = null;
  if (sayBye && navigator.sendBeacon) navigator.sendBeacon(withId(API.BYE));
}

window.addEventListener('pagehide', () => stopPresence());
window.addEventListener('pageshow', (e) => { if (e.persisted) startPresence(); });
