# Winter Village Monopoly JavaFX

A standalone JavaFX 3D Monopoly board set in a winter village. The scene
contains a square frozen lake, a centred 40-tile Monopoly board, six recoloured
3D player tokens, two animated dice, and two card decks.

**Runtime:** JDK 21 / JavaFX 21.0.8  
**Default scene:** Winter Village v2 (square frozen lake)

## Requirements

- JDK 21
- JavaFX SDK 21.0.8 for the host platform
- macOS or Linux for `download-javafx.sh`; Windows requires a manually installed
  JavaFX SDK

The repository includes the Java libraries needed by the model loader. The
JavaFX runtime itself is downloaded separately because it contains
platform-specific native libraries.

## Run

From this directory:

```bash
./download-javafx.sh
./run.sh
```

`download-javafx.sh` installs the JavaFX SDK into `vendor/`. If a compatible SDK
already exists elsewhere:

```bash
JAVAFX_LIB=/path/to/javafx-sdk-21.0.8/lib ./run.sh
```

The first run compiles the sources into `winter_village_javafx/out/classes`.
The output directory is intentionally excluded from version control.

### Scene Profiles

```bash
./run.sh                          # v2 square lake, default
WINTER_VILLAGE_PROFILE=v1 ./run.sh
```

A specific GLB can also be forced with:

```bash
java ... -Dwinter.village.asset=/absolute/path/model.glb ...
```

## Features

- v2 square-lake winter village with a centred Monopoly board.
- Six imported 3D tokens with per-player colours.
- Two 3D dice with deterministic final faces, roll animation, result display,
  and one-tile-at-a-time movement.
- Orange activation-card deck and blue public-resource-card deck above the
  MONOPOLY banner, with a lift/stand/hold/reverse draw animation.
- Rules integration hooks: automatic or rules-owned dice movement, explicit
  step and tile setters, custom card decks, and draw callbacks.
- Orbit camera, zoom, reset, lighting, and HUD.

## Project Layout

```text
.
├── README.md
├── INTERFACE.md
├── run.sh
├── download-javafx.sh
├── .gitignore
├── .vscode/
└── winter_village_javafx/
    ├── README.md
    ├── build.sh
    ├── run.sh
    ├── assets/                 # village, token, and dice GLB files
    ├── docs/RULES_API.md
    ├── lib/                    # jgltf, Jackson, and JavaFX compile jars
    └── src/main/java/wintervillage/
```

## Documentation

- [`INTERFACE.md`](INTERFACE.md): public Java API, runtime configuration, asset
  conventions, and rules integration.
- [`winter_village_javafx/docs/RULES_API.md`](winter_village_javafx/docs/RULES_API.md):
  detailed rules-team integration guide.
- [`winter_village_javafx/README.md`](winter_village_javafx/README.md): project
  build, controls, and layout notes.

## VS Code

The project includes `.vscode/settings.json`, `.vscode/tasks.json`, and JavaFX
compile jars under `winter_village_javafx/lib/javafx/`. Open either this folder
or `winter_village_javafx` directly, then reload the window or run
**Java: Clean Java Language Server Workspace** if imports still show errors.

Use the task **Winter Village: run package** or the terminal command `./run.sh`
to launch. VS Code's plain “Run Java” action may not include the JavaFX module
path; the scripts do.

## Notes

- JavaFX uses `PhongMaterial`; PBR metallic/roughness and soft shadows are
  approximated.
- Card content is a display mechanism, not a complete game-rules engine.
- The board and token geometry use metres, Y-up, and front +Z.
