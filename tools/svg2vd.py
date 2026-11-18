"""Convert a single-colour SVG mark to an Android VectorDrawable for firehome: cropped to the shape's bounds,
filled white, every path written out in absolute form with arcs turned into cubic curves (Android 5.1's PathParser
draws some arcs and compact arc flags wrongly).

Usage:  tools/venv/bin/python -I tools/svg2vd.py <mark.svg> firehome/res/drawable/logo_<name>_clean.xml "<source note>"
Needs:  pip install -r tools/requirements-icons.txt   (svgelements)
"""
import re, sys
from svgelements import Path
src, out, pad = sys.argv[1], sys.argv[2], 0.02
svg = open(src).read()
ds = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
xs0, xs1, ys0, ys1 = [], [], [], []
norm = []
for d in ds:
    # written out in full (absolute commands, separated arc flags): Android 5.1's PathParser misreads "0 00"
    path = Path(d)
    path.approximate_arcs_with_cubics()   # Android 5.1 draws some arcs wrong; cubics are safe everywhere
    norm.append(path.d(relative=False))
    x0, y0, x1, y1 = path.bbox()
    xs0.append(x0); xs1.append(x1); ys0.append(y0); ys1.append(y1)
x0, x1, y0, y1 = min(xs0), max(xs1), min(ys0), max(ys1)
w, h = x1 - x0, y1 - y0
p = pad * max(w, h)
x0 -= p; y0 -= p; w += 2 * p; h += 2 * p
dpw, dph = (48, 48 * h / w) if w >= h else (48 * w / h, 48)
paths = "\n".join(f'        <path android:fillColor="#FFFFFFFF" android:pathData="{d}" />' for d in norm)
open(out, "w").write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {sys.argv[3]} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{dpw:.2f}dp" android:height="{dph:.2f}dp"
    android:viewportWidth="{w:.4f}" android:viewportHeight="{h:.4f}">
    <group android:translateX="{-x0:.4f}" android:translateY="{-y0:.4f}">
{paths}
    </group>
</vector>
''')
print(f"{out.split('/')[-1]}: {w:.2f}x{h:.2f} aspect {w/h:.2f}")
