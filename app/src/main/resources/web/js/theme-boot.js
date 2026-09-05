/* The theme before the page's modules load, so that a dark page does not flash white: the same
   key and rule as js/theme.js ('xsdviewer.theme': 'system' / 'light' / 'dark'). A classic script, run from <head>. */
(function () {
  var choice = null;
  try { choice = localStorage.getItem('xsdviewer.theme'); } catch (e) { /* storage unavailable */ }
  var dark = choice === 'dark' || (choice !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
})();
