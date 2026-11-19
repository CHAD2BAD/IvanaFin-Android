# IvanaFin

IvanaFin is a [Jellyfin](https://jellyfin.org) app for Android TV, Google TV and Fire TV, with its own look:
a big-tile home screen, full-screen film and show pages, a music library in the style of a TV music app,
a player laid out like Jellyfin's web player, and extras for Star Wars films and shows.

It is a fork of [Wholphin](https://github.com/damontecres/Wholphin) by damontecres - all the hard work of
talking to Jellyfin and playing video comes from Wholphin. IvanaFin changes the design and adds features
on top.

> **Status:** early. The Star Wars extras work on any server for the official films and shows (matched by
> their TMDB id). On artist pages, Popular songs and the Albums / EPs & Singles / Mixtapes rows come from
> [Deezer](https://www.deezer.com) (the artist's name is sent to Deezer's free public API when the page opens).

## Install

1. On the TV, allow installing apps from unknown sources (Fire TV: *Settings > My Fire TV > Developer
   options*; Google TV: *Settings > System > About*, click *Android TV OS build* 7 times, then allow your
   downloader app).
2. Download the newest APK from [Releases](../../releases) with a downloader app (for example
   *Downloader* by AFTVnews):
   - most Fire TV sticks and older Android TVs: `IvanaFin-release-armeabi-v7a.apk`
   - newer 64-bit devices (Google TV Streamer, Shield, Fire TV Stick 4K Max 2nd gen): `IvanaFin-release-arm64-v8a.apk`
   - not sure: `IvanaFin-release.apk` (works everywhere, bigger download)
3. Open IvanaFin and sign in to your Jellyfin server.

The app checks this page for new versions and tells you when one is out.

## Discover needs Seerr

Everything else works with a plain Jellyfin server, but **Discover (browse and request films and shows)
only appears once [Seerr](https://github.com/seerr-team/seerr) is set up**. Without it, Discover is hidden.
To turn it on:

1. Install and set up Seerr, connected to your Jellyfin server (and to Radarr / Sonarr so requests
   are downloaded).
2. In IvanaFin open **Settings > Seerr integration**, enter your Seerr address and sign in (your
   Jellyfin user works).
3. Discover then appears in IvanaFin.

## Build it yourself

Requirements: JDK 21 and the Android SDK.

```
./gradlew assembleDefaultRelease
```

Release builds are signed with the key configured in `~/.gradle/gradle.properties`
(`IVF_RELEASE_STORE_FILE`, `IVF_RELEASE_STORE_PASSWORD`, `IVF_RELEASE_KEY_ALIAS`); without it, set
`release.signing.config=debug` in `local.properties` to sign with your debug key.

## Credits and licences

- **Wholphin** - the app this is built on, by damontecres and contributors, GPL-2.0.
- **Jellyfin** SDK and server - GPL-2.0.
- **Star Wars facts** shown in the Planets / Characters / Starships rows are summarised from
  [Wookieepedia](https://starwars.fandom.com) (text under CC BY-SA 3.0). The pictures are copies from Wookieepedia, kept in
  [extras/star-wars](extras/star-wars) so the app can show them on any server; they belong to their owners.
  Star Wars is a trademark of Lucasfilm Ltd. This project is not affiliated with Lucasfilm or Disney.
- Icons: Material Icons (Apache 2.0), Font Awesome Free (fonts SIL OFL 1.1, icons CC BY 4.0).

IvanaFin is free software under the **GNU General Public License v2** (see [LICENSE](LICENSE)), like
Wholphin. Wholphin's original README is kept as [README-Wholphin.md](README-Wholphin.md).
