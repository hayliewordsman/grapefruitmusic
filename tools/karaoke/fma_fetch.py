"""Fetches the Free Music Archive tracks Karaoke Mode v2 learns from.

From fma_small (8,000 30-second clips; Defferrard et al., https://github.com/mdeff/fma), only the
tracks whose licence allows training a model that ships in the app: Creative Commons Attribution,
CC0 and public domain (no NonCommercial, NoDerivatives or ShareAlike). The zip is 7 GB, so each
track is read straight out of it with HTTP range requests rather than downloading the lot.

    python3 fma_fetch.py fma_metadata/tracks.csv out_dir [max_tracks]

Writes out_dir/<track id>.mp3 and out_dir/credits.csv (id, artist, title, licence, URL), the
attribution the licences ask for.
"""
import csv
import io
import os
import sys
import urllib.request
import zipfile

ZIP_URL = "https://os.unil.cloud.switch.ch/fma/fma_small.zip"
ALLOWED = (
    "Attribution",
    "Creative Commons Attribution",
    "Attribution 3.0 United States",
    "Attribution 3.0 International",
    "Attribution 4.0 International",
    "Creative Commons Attribution 4.0 International License",
    "CC0 1.0 Universal",
    "Public Domain",
    "Public Domain Mark 1.0",
)


class HttpFile(io.RawIOBase):
    """A read-only, seekable view of a URL, read with HTTP range requests (for zipfile)."""

    def __init__(self, url):
        self.url = url
        self.pos = 0
        req = urllib.request.Request(url, method="HEAD")
        with urllib.request.urlopen(req) as r:
            self.size = int(r.headers["Content-Length"])
        self.cache_start = 0
        self.cache = b""

    def seekable(self):
        return True

    def readable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, offset, whence=0):
        if whence == 0:
            self.pos = offset
        elif whence == 1:
            self.pos += offset
        else:
            self.pos = self.size + offset
        return self.pos

    def _fetch(self, start, end):
        req = urllib.request.Request(self.url, headers={"Range": "bytes=%d-%d" % (start, end - 1)})
        for attempt in range(5):
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    return r.read()
            except OSError:
                if attempt == 4:
                    raise

    def read(self, n=-1):
        if n is None or n < 0:
            n = self.size - self.pos
        n = min(n, self.size - self.pos)
        if n <= 0:
            return b""
        end = self.pos + n
        if not (self.cache_start <= self.pos and end <= self.cache_start + len(self.cache)):
            # Read ahead: a track is read in many small pieces.
            want = max(n, 1 << 20)
            self.cache_start = self.pos
            self.cache = self._fetch(self.pos, min(self.size, self.pos + want))
        out = self.cache[self.pos - self.cache_start:end - self.cache_start]
        self.pos = end
        return out

    def readinto(self, b):
        data = self.read(len(b))
        b[:len(data)] = data
        return len(data)


def allowed_tracks(tracks_csv):
    rows = list(csv.reader(open(tracks_csv, newline="", encoding="utf-8")))
    col = {(a, b): i for i, (a, b) in enumerate(zip(rows[0], rows[1]))}
    out = []
    for r in rows[3:]:
        if len(r) <= col[("set", "subset")] or r[col[("set", "subset")]] != "small":
            continue
        licence = r[col[("track", "license")]].strip()
        if licence in ALLOWED:
            out.append({
                "id": int(r[0]),
                "artist": r[col[("artist", "name")]],
                "title": r[col[("track", "title")]],
                "licence": licence,
                "url": "https://freemusicarchive.org/track/%d" % int(r[0]),
                "genre": r[col[("track", "genre_top")]],
            })
    return out


def main():
    tracks_csv, out_dir = sys.argv[1], sys.argv[2]
    limit = int(sys.argv[3]) if len(sys.argv) > 3 else None
    os.makedirs(out_dir, exist_ok=True)
    tracks = allowed_tracks(tracks_csv)[:limit]
    print("%d tracks allowed" % len(tracks), flush=True)
    z = zipfile.ZipFile(HttpFile(ZIP_URL))
    names = {os.path.basename(n): n for n in z.namelist()}
    with open(os.path.join(out_dir, "credits.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["id", "artist", "title", "licence", "url", "genre"])
        for i, t in enumerate(tracks):
            name = names.get("%06d.mp3" % t["id"])
            dest = os.path.join(out_dir, "%06d.mp3" % t["id"])
            if name is None:
                continue
            if not os.path.exists(dest):
                data = z.read(name)
                with open(dest, "wb") as out:
                    out.write(data)
            w.writerow([t["id"], t["artist"], t["title"], t["licence"], t["url"], t["genre"]])
            if i % 50 == 0:
                print("%d/%d" % (i, len(tracks)), flush=True)


if __name__ == "__main__":
    main()
