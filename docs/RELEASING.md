# Releasing a family mod

Steps for any release of Backport Core or a backport. The update-notice step is easy to skip, so it comes first.

## 1. Update notices (every release, any mod)

Two places in **Backport Core's repository** say what each family mod's latest version is. Both must name the
version you are about to upload, for every mod in the release, or players are told they are up to date when they
are not (Core 1.4.7 went out with the table still naming Core 1.4.6 and the JSONs Core 1.4.4).

- `src/main/java/com/exiledradio/backportcore/settings/Family.java`, `MEMBERS`: the last argument of each
  `Member` is its latest version. The settings screen's notices fall back to it. It only reaches players in a
  Core release.
- `updates/<modid>.json`: Forge's update JSON, which every family mod's `@Mod(updateJSON)` reads from Core's
  `main` on GitHub at startup. Set `1.12.2-latest`, `1.12.2-recommended` and the `1.12.2` entry to the new
  version. It reaches players as soon as Core's `main` is pushed, with no Core release.

Releasing Core itself counts: its own `backportcore` entry must name the Core version being released.

Check before uploading:

```
grep -n 'new Member' src/main/java/com/exiledradio/backportcore/settings/Family.java
grep -H '1.12.2-latest' updates/*.json
```

Each line should show the version on its way to CurseForge (or the one already live there). Changing
`Family.java` after Core's release jar is built means building Core again, and any change after a release bumps
Core's version.

After the uploads are approved, push Core's `main` so the JSONs go live. Check one:

```
curl -s https://raw.githubusercontent.com/ExiledRadio/BackportCore/main/updates/backportcore.json
```

## 2. Build and test

- Build with `-PdeployDir=` (empty) so nothing installs into a modpack.
- Self-tests from a fresh world (delete the `level-name` world in `eclipse/server.properties` first): `./gradlew
  runServer -Pselftest`, then the client suite (`runClient -Pselftest=client` in Core).
- Build the release jar **after** the last test run: test runs rewrite `build/libs/` with the self-test classes
  in it. The jar must have 0 `/selftest/` entries and SRG (`func_`) names.
- Build from a clean working tree. Uncommitted debug edits end up in the jar.

## 3. Stage and upload

- `E:\MincraftModding\Releases\<Mod>-<version>\`: the jar, `NOTES.md` (player changelog plus Install), and the
  description and changelog copies. Move older folders to `superseded\`.
- Upload to CurseForge with the previous file's settings (Release, 1.12.2, Forge, Java 8, Client and Server,
  default relations), with `NOTES.md` as the Markdown changelog.
- Core: also a GitHub release `v<version>` with the jar.
- Then step 1's push.
