# កម្មវិធីស្រង់ទិន្នន័យស្ថានភាពផ្លូវ

**នាយកដ្ឋានអភិវឌ្ឍន៍ហេដ្ឋារចនាសម្ព័ន្ធផ្លូវជនបទ** — Road condition inspection app (PWA)

A mobile web app for road condition surveys. It tracks GPS chainage (km+m), records left/right defects, photos and bridges, rates the route condition in 200 m windows, and exports reports.

## Features
- Live GPS chainage in km+m format (e.g. `5+200`), with manual start point
- Forward / reverse survey direction
- Left and right side defect marking: potholes, cracking, big damage, shoulder, other
- Geo-tagged photos and bridge marking
- Undo last entry
- Route condition (Good / Medium / Poor) and bar chart report
- Export to PDF, CSV, PPTX and photo ZIP
- Works offline after the first visit; data is saved on the phone

## Files
| File | Purpose |
|---|---|
| `index.html` | The whole app |
| `manifest.json` | App name, icon and colors for "Add to Home Screen" |
| `sw.js` | Offline support |
| `icons/` | App icons |
| `vendor/` | Export libraries (PDF, Excel, PPTX, ZIP), stored locally so exports work offline |
| `.nojekyll` | Tells GitHub Pages to serve files as they are |

## Publish with GitHub Pages
1. Create a new **public** repository on GitHub (e.g. `road-inspection`).
2. Upload all files and the `icons` folder to the repository root.
3. Go to **Settings → Pages**, set **Source** to *Deploy from a branch*, branch `main`, folder `/ (root)`, then **Save**.
4. After a minute, open `https://<your-username>.github.io/road-inspection/`.

## Install on iPhone
Open the link in **Safari**, tap **Share → Add to Home Screen**, then allow location access when you start a survey.

## Updating the app
1. Replace `index.html` with the new version.
2. In `sw.js`, change `CACHE_VERSION` (e.g. `road-inspection-v25`).
3. Commit. On the phone, close and reopen the app twice to load the update.

## Notes
- GPS needs HTTPS, which GitHub Pages provides.
- Survey data is stored in the phone's browser. Export reports before clearing Safari data.
- The app and all exports work with no internet after the first visit. Only the Khmer web font needs internet; without it the phone's own Khmer font is used.
