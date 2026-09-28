// Service worker: lets the app open with no signal in the field.
// Change CACHE_VERSION every time you upload a new index.html.
const CACHE_VERSION = 'road-inspection-v46';
const APP_SHELL = [
  './',
  './index.html',
  './manifest.json',
  './icons/apple-touch-icon.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/favicon-32.png',
  './fonts/kantumruy-khmer-400.woff2',
  './fonts/kantumruy-khmer-600.woff2',
  './fonts/kantumruy-khmer-700.woff2',
  './fonts/kantumruy-latin-400.woff2',
  './fonts/kantumruy-latin-600.woff2',
  './fonts/kantumruy-latin-700.woff2',
  // Export libraries are stored with the app, so PDF / Excel / PPTX / ZIP
  // exports keep working with no internet at all.
  './vendor/jszip.min.js',
  './vendor/jspdf.umd.min.js',
  './vendor/pptxgen.bundle.js'
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_VERSION).then((c) => c.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_VERSION).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  // The app page: try the network first so updates show, fall back to cache offline.
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE_VERSION).then((c) => c.put('./index.html', copy));
          return res;
        })
        .catch(() => caches.match('./index.html'))
    );
    return;
  }

  // Everything else (icons, export libraries, Khmer font): cache first, then network.
  event.respondWith(
    caches.match(req).then((cached) => {
      if (cached) return cached;
      return fetch(req).then((res) => {
        if (res && (res.ok || res.type === 'opaque')) {
          const copy = res.clone();
          caches.open(CACHE_VERSION).then((c) => c.put(req, copy));
        }
        return res;
      });
    })
  );
});
