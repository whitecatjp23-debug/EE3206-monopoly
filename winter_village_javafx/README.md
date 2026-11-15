# Winter Village v2 — JavaFX 3D

A standalone JavaFX 3D project that renders the Winter Village v2 square
frozen-lake scene with a centred Monopoly board, six 3D player tokens, two
animated dice, and two card decks.

The project is self-contained under this directory and does not modify any
parent-project source, scripts, or assets.

## Requirements

- JDK 21
- JavaFX SDK 21.0.8 for the host platform

## Build and Run

```bash
./run.sh
```

The first run compiles into `out/classes`. To point at an existing JavaFX SDK:

```bash
JAVAFX_LIB=/path/to/javafx-sdk-21.0.8/lib ./run.sh
```

From the package root, the launcher can also be used:

```bash
../run.sh
```

## Scene Profiles

The default is v2:

```bash
./run.sh
```

To load the original approved v1 scene:

```bash
WINTER_VILLAGE_PROFILE=v1 ./run.sh
```

`WinterVillageProfile` switches the GLB, board placement, and initial camera
together. v1 uses `assets/winter_village_sample_v1.glb`; v2 uses
`assets/winter_village_sample_v2.glb`. A specific GLB can be forced with:

```bash
java ... -Dwinter.village.asset=/absolute/path/model.glb ...
```

## Controls

- Left-drag: orbit around the village.
- Mouse wheel: zoom (minimum distance 2.5 m).
- `Space` / `D`: roll both dice; the current player moves automatically after
  the result settles.
- `A`: draw an activation card (orange).
- `C`: draw a public resource card (blue).
- `T`: return all six tokens to GO.
- `R` / `Home`: restore the current profile's initial camera.
- HUD buttons: Roll Dice, Activation Card, Resource Card, Reset Tokens, and
  Reset Camera.

## Orientation

The glTF convention is Y-up with front +Z, and JavaFX 3D is also Y-up, so the
loader applies no model rotation. The initial camera sits on the +X/+Z side and
looks at the village front.

Key v2 placement values:

```text
Lake centre:       JavaFX (0, 0)
Lake top:          about Y = 0.774 m
Board centre:      (0, 0, 0)
Board outer width: 7.6 m
Board bottom:      Y = 0.794 m
Camera target:     (0, 0.88, 0)
Camera eye:        (34.37, 67.40, 43.99)
```

## Project Layout

```text
winter_village_javafx/
├── README.md
├── build.sh
├── run.sh
├── assets/
│   ├── winter_village_sample.glb
│   ├── winter_village_sample_v1.glb
│   ├── winter_village_sample_v2.glb
│   ├── tokens/*.glb
│   └── dice/dice_01.glb
├── docs/RULES_API.md
├── lib/
│   ├── jackson-*.jar
│   ├── jgltf-*.jar
│   └── javafx/*.jar
└── src/main/java/wintervillage/
    ├── WinterVillageApp.java
    ├── WinterVillageView.java
    ├── WinterVillageProfile.java
    ├── OrbitCameraController.java
    ├── asset/ModelAssetLoader.java
    ├── board/
    ├── cards/
    ├── dice/
    └── tokens/
```

## Rules Integration

The public controllers expose hooks for automatic or rules-owned movement,
exact tile placement, and custom card decks:

```java
DiceController dice = view.getDiceController();
PlayerTokenLayer tokens = view.getPlayerTokens();
CardController cards = view.getCardController();
```

See [`docs/RULES_API.md`](docs/RULES_API.md) for the full event order and
examples, and [`../INTERFACE.md`](../INTERFACE.md) for the complete public API.

## Notes

- JavaFX uses `PhongMaterial`; PBR metallic/roughness and soft shadows are
  approximated.
- Card content is a display mechanism; the project does not implement property
  purchase, rent, or resource settlement.
- The six tokens share a 2×3 slot layout when they occupy the same tile.
