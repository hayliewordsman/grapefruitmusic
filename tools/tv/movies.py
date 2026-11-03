#!/usr/bin/env python3
"""Grapefruit TV & Movies' Movies tab: a hand-picked list of films that are free and legal to watch, hosted
by the Internet Archive (archive.org), which serves them openly for streaming and download.

Only films that are in the public domain in the United States (published before 1930, or never renewed
or published without notice) or released under Creative Commons licences (the Blender Foundation's open
movies, Sita Sings the Blues) are listed, and only archive.org items marked public domain or CC.

The list is curated by hand, not pulled from a search: nothing discriminatory. Films with racist
caricatures or blackface, propaganda, Confederate "lost cause" heroics and exploitation pictures are
left out, as are Blu-ray restoration rips whose new scores or restorations may carry their own rights.

For each film this resolves a playable H.264 MP4 (480p where archive.org has made one) and its poster,
checks the file answers, and writes tv/src/main/assets/movies.json.
Run from repo root: python3 tools/tv/movies.py
"""
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parents[2]
OUT = root / 'tv/src/main/assets/movies.json'
UA = 'GrapefruitTV/3.0 (catalog check; +https://github.com/hayliewordsman/grapefruitmusic)'

# (archive.org identifier, title, year, genre, minutes, licence, description)
FILMS = [
    ('his_girl_friday', 'His Girl Friday', 1940, 'Comedy', 92, 'Public domain',
     'Cary Grant and Rosalind Russell trade rapid-fire lines as a newspaper editor and his ace reporter, who is also his ex-wife.'),
    ('MyManGodfrey1936', 'My Man Godfrey', 1936, 'Comedy', 94, 'Public domain',
     'A scatterbrained heiress hires a man she finds living at the city dump as her family\'s butler. With Carole Lombard and William Powell.'),
    ('meet_john_doe', 'Meet John Doe', 1941, 'Drama', 122, 'Public domain',
     'Frank Capra\'s story of a newspaper stunt that turns a drifter (Gary Cooper) into the face of a national movement. With Barbara Stanwyck.'),
    ('penny_serenade', 'Penny Serenade', 1941, 'Drama', 119, 'Public domain',
     'Irene Dunne and Cary Grant as a couple whose marriage is told through the records they play.'),
    ('AStarIsBorn', 'A Star Is Born', 1937, 'Drama', 111, 'Public domain',
     'The original: Janet Gaynor rises to fame in Hollywood as her husband, Fredric March, falls.'),
    ('LifeWithFather', 'Life with Father', 1947, 'Comedy', 118, 'Public domain',
     'William Powell and Irene Dunne in a warm family comedy set in 1880s New York.'),
    ('royal_wedding', 'Royal Wedding', 1951, 'Musical', 93, 'Public domain',
     'Fred Astaire and Jane Powell as a brother-and-sister act in London, including Astaire\'s famous dance on the ceiling.'),
    ('BeatTheDevil1953', 'Beat the Devil', 1953, 'Comedy', 89, 'Public domain',
     'John Huston\'s offbeat caper with Humphrey Bogart, Gina Lollobrigida and Jennifer Jones, written with Truman Capote.'),
    ('cco_jackandthebeanstalk', 'Jack and the Beanstalk', 1952, 'Family', 78, 'Public domain',
     'Abbott and Costello tell the fairy tale their own way, in sepia and colour.'),
    ('SteamboatBillJr', 'Steamboat Bill, Jr.', 1928, 'Silent', 70, 'Public domain',
     'Buster Keaton as a college boy helping his father\'s riverboat, ending in a legendary cyclone.'),
    ('ThePhantomoftheOpera', 'The Phantom of the Opera', 1925, 'Silent', 93, 'Public domain',
     'Lon Chaney as the masked Phantom haunting the Paris Opera House.'),
    ('nosferatu_201508', 'Nosferatu', 1922, 'Silent', 94, 'Public domain',
     'F. W. Murnau\'s eerie vampire classic, with Max Schreck as Count Orlok.'),
    ('Detour', 'Detour', 1945, 'Film Noir', 68, 'Public domain',
     'A hitchhiking pianist\'s trip to Los Angeles goes from bad to fatal. A lean, famous B-noir.'),
    ('ScarletStreet', 'Scarlet Street', 1945, 'Film Noir', 102, 'Public domain',
     'Fritz Lang\'s noir: a lonely cashier and amateur painter (Edward G. Robinson) is drawn into a con.'),
    ('TheStranger_0', 'The Stranger', 1946, 'Film Noir', 95, 'Public domain',
     'Orson Welles directs and stars as a Nazi war criminal hiding in a Connecticut town; Edward G. Robinson hunts him.'),
    ('Hitch_Hiker', 'The Hitch-Hiker', 1953, 'Film Noir', 71, 'Public domain',
     'Directed by Ida Lupino: two friends on a fishing trip pick up a killer.'),
    ('kansascityconfidencial', 'Kansas City Confidential', 1952, 'Film Noir', 99, 'Public domain',
     'An ex-convict framed for an armoured-car robbery tracks down the masked men who did it.'),
    ('suddenly', 'Suddenly', 1954, 'Thriller', 77, 'Public domain',
     'Frank Sinatra as a gunman who takes over a family\'s house to ambush the President\'s train.'),
    ('night_of_the_living_dead_dvd', 'Night of the Living Dead', 1968, 'Horror', 96, 'Public domain',
     'George A. Romero\'s groundbreaking horror: strangers barricade themselves in a farmhouse as the dead rise.'),
    ('CarnivalofSouls', 'Carnival of Souls', 1962, 'Horror', 78, 'Public domain',
     'After surviving a car crash, a church organist is drawn to an abandoned lakeside pavilion.'),
    ('The_House_On_Haunted_Hill', 'House on Haunted Hill', 1959, 'Horror', 75, 'Public domain',
     'Vincent Price offers five guests $10,000 each to survive a night in a haunted house.'),
    ('TheTerror', 'The Terror', 1963, 'Horror', 81, 'Public domain',
     'Boris Karloff and a young Jack Nicholson in Roger Corman\'s gothic mystery.'),
    ('the-last-man-on-earth-1964_202312', 'The Last Man on Earth', 1964, 'Sci-Fi', 86, 'Public domain',
     'Vincent Price as the lone survivor of a plague, the first film of Richard Matheson\'s I Am Legend.'),
    ('angel_and_the_badman', 'Angel and the Badman', 1947, 'Western', 100, 'Public domain',
     'A wounded gunslinger is taken in by a Quaker family and starts to change his ways.'),
    ('HerculesUnchained', 'Hercules Unchained', 1959, 'Adventure', 101, 'Public domain',
     'Steve Reeves returns as Hercules, caught between warring brothers and a queen\'s spell.'),
    ('gullivers_travels1939', "Gulliver's Travels", 1939, 'Animation', 76, 'Public domain',
     'The Fleischer Studios\' animated feature: Gulliver washes up in Lilliput, a land at war over a song.'),
    ('Sita_Sings_the_Blues', 'Sita Sings the Blues', 2008, 'Animation', 82, 'CC0',
     'Nina Paley\'s animated retelling of the Ramayana, set to 1920s jazz songs by Annette Hanshaw.'),
    ('Sintel', 'Sintel', 2010, 'Animation', 15, 'CC BY 3.0',
     'Blender Foundation open movie: a young woman searches for the baby dragon she raised.'),
    ('BigBuckBunny_328', 'Big Buck Bunny', 2008, 'Animation', 10, 'CC BY 3.0',
     'Blender Foundation open movie: a big, gentle rabbit takes on three bullying rodents.'),
    ('ElephantsDream', 'Elephants Dream', 2006, 'Animation', 11, 'CC BY 3.0',
     'Blender Foundation\'s first open movie: two men in a strange, endless machine.'),
    ('CosmosLaundromatFirstCycle', 'Cosmos Laundromat', 2015, 'Animation', 12, 'CC BY 4.0',
     'Blender Foundation open movie: a suicidal sheep meets a salesman who offers him other lives.'),
    ('Tears-of-Steel', 'Tears of Steel', 2012, 'Sci-Fi', 12, 'CC BY 3.0',
     'Blender Foundation open movie: scientists in Amsterdam try to save the world from robots.'),
]


def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=60)


def seconds(f):
    """A file's running time in seconds (archive.org gives "1190.49" or "01:35:12")."""
    value = str(f.get('length', '') or '0')
    try:
        if ':' in value:
            total = 0.0
            for part in value.split(':'):
                total = total * 60 + float(part)
            return total
        return float(value)
    except ValueError:
        return 0.0


def pick(files):
    """The whole film as an H.264 MP4: the longest file (not a reel or a menu), archive.org's 480p
    'h.264' derivative where there is one, else '512Kb MPEG4', else another MP4; the smaller on a tie."""
    mp4 = [f for f in files if f.get('name', '').lower().endswith('.mp4') and 'ipod' not in f['name'].lower()]
    for fmt in ('h.264', '512Kb MPEG4', 'h.264 IA', 'MPEG4', 'h.264 HD'):
        found = [f for f in mp4 if f.get('format') == fmt]
        if found:
            return max(found, key=lambda f: (round(seconds(f) / 30), -int(f.get('size', 0) or 0)))
    return None


def main():
    out = []
    failed = []
    for ident, title, year, genre, minutes, licence, description in FILMS:
        try:
            meta = json.load(get('https://archive.org/metadata/' + ident))
            lic = meta.get('metadata', {}).get('licenseurl', '')
            if lic and 'publicdomain' not in lic and 'creativecommons.org/licenses/by' not in lic:
                raise ValueError('licence ' + lic)
            f = pick(meta.get('files', []))
            if not f:
                raise ValueError('no MP4')
            if seconds(f) < minutes * 60 * 0.75:  # a reel of a film split into parts: not the whole film
                raise ValueError('only %d of about %d minutes in one file' % (seconds(f) / 60, minutes))
            url = 'https://archive.org/download/%s/%s' % (ident, urllib.parse.quote(f['name']))
            with get(url) as r:  # follows the redirect to the serving host
                if r.status != 200:
                    raise ValueError('HTTP %d' % r.status)
            out.append({'id': 'ia-' + ident, 'title': title, 'year': year, 'genre': genre, 'minutes': minutes,
                        'licence': licence, 'description': description, 'url': url,
                        'poster': 'https://archive.org/services/img/' + ident,
                        'page': 'https://archive.org/details/' + ident,
                        'size': int(f.get('size', 0) or 0), 'seconds': round(seconds(f))})
            print('ok  ', title, f.get('format'), f['name'])
        except Exception as e:  # noqa: BLE001 - report and carry on
            failed.append(title)
            print('FAIL', title, e, file=sys.stderr)
    OUT.write_text(json.dumps({'source': 'Internet Archive (archive.org)', 'movies': out}, indent=1, ensure_ascii=False) + '\n')
    print(OUT, len(out), 'films;', len(failed), 'failed', failed)
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
