/** Handing a file to the browser to save: what every export ends with. */
import { MIME } from './constants.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { toast } from './toast.js';

/** How long the blob's URL is kept alive after the click that saves it. */
const REVOKE_DELAY_MS = 10000;

/** Hands a file to the browser to save. */
export function saveBlob(blob, fileName) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), REVOKE_DELAY_MS);
}

/** The canvas as a PNG file; the browser refusing to encode it is reported. */
export function saveCanvas(canvas, fileName) {
  canvas.toBlob((blob) => {
    if (!blob) { toast(t(MSG.EXPORT_PNG_FAILED)); return; }
    saveBlob(blob, fileName);
  }, MIME.PNG);
}
