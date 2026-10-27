"""Writes the credits for the Free Music Archive songs Karaoke Mode learned from.

    python3 credits.py fma_dir npz_fma_dir fma_credits.csv karaoke_credits.txt

fma_dir/credits.csv comes from fma_fetch.py; only songs teacher.py labelled (an npz in npz_fma_dir)
are credited. Writes a CSV for the repository (title, artist, licence, URL) and the app's list
(app/src/main/assets/karaoke_credits.txt: title, artist, licence and URL, tab-separated).
"""
import csv
import os
import sys

LICENCES = {
    "Attribution": "CC BY",
    "Creative Commons Attribution": "CC BY",
    "Attribution 3.0 United States": "CC BY 3.0 US",
    "Attribution 3.0 International": "CC BY 3.0",
    "Attribution 4.0 International": "CC BY 4.0",
    "Creative Commons Attribution 4.0 International License": "CC BY 4.0",
    "CC0 1.0 Universal": "CC0 1.0",
    "Public Domain": "Public domain",
    "Public Domain Mark 1.0": "Public Domain Mark 1.0",
}


def clean(s):
    return " ".join((s or "").replace("\t", " ").split())


def main():
    fma, npz, out_csv, out_txt = sys.argv[1:5]
    used = {int(os.path.splitext(n)[0]) for n in os.listdir(npz) if n.endswith(".npz")}
    rows = [r for r in csv.DictReader(open(os.path.join(fma, "credits.csv"), encoding="utf-8")) if int(r["id"]) in used]
    rows.sort(key=lambda r: (clean(r["artist"]).lower(), clean(r["title"]).lower()))
    with open(out_csv, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["title", "artist", "licence", "url"])
        for r in rows:
            w.writerow([clean(r["title"]), clean(r["artist"]), LICENCES.get(r["licence"], r["licence"]), r["url"]])
    with open(out_txt, "w", encoding="utf-8") as f:
        for r in rows:
            f.write("\t".join([clean(r["title"]) or "Untitled", clean(r["artist"]) or "Unknown artist",
                               LICENCES.get(r["licence"], r["licence"]), r["url"]]) + "\n")
    print("%d songs credited" % len(rows))


if __name__ == "__main__":
    main()
