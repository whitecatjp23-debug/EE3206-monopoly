# Winter Village Monopoly 3D — Interface Documentation

**Version:** 1.0  
**Packaged:** 2026-11-15  
**Default scene:** Winter Village v2 (square frozen lake)  
**Runtime:** JDK 21 / JavaFX 21.0.8 / jgltf-model 2.0.4

This document describes the public Java API, runtime configuration, asset
conventions, and integration points of the `winter_village_javafx` project.

---

## 1. Entry Points and Modules

| Module | Responsibility |
| --- | --- |
| `wintervillage.WinterVillageApp` | JavaFX application entry point and window |
| `wintervillage.WinterVillageView` | Board scene container (3D SubScene + HUD) |
| `wintervillage.WinterVillageProfile` | v1/v2 asset, board, and camera settings |
| `wintervillage.OrbitCameraController` | Orbit, zoom, reset, and focus camera control |
| `wintervillage.tokens.PlayerTokenLayer` | Loads six tokens, recolours them, lays them out on tiles, and moves them step by step |
| `wintervillage.dice.DiceController` | Two 3D dice, roll animation, results, and turn coordination |
| `wintervillage.cards.CardController` | Two card decks, draw animation, and state interlocks |
| `wintervillage.board.MonopolyBoardModel` | Placeable compact Monopoly board model |
| `wintervillage.board.BoardGeometry` | 40-tile board geometry and indexing |
| `wintervillage.asset.ModelAssetLoader` | GLB/glTF to JavaFX MeshView loading |

All scene APIs must be called on the **JavaFX Application Thread**.
`ModelAssetLoader.load(...)` checks the calling thread.

---

## 2. Quick Start

```java
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import wintervillage.WinterVillageView;

public class Demo extends Application {
    @Override
    public void start(Stage stage) {
        WinterVillageView view = new WinterVillageView(true);
        stage.setScene(new Scene(new StackPane(view), 1400, 900));
        stage.show();
    }
}
```

The default scene is v2. To switch to the original v1 scene:

```bash
WINTER_VILLAGE_PROFILE=v1 ./run.sh
# or
java ... -Dwinter.village.profile=v1 ...
```

---

## 3. `WinterVillageView`

```java
public final class WinterVillageView extends StackPane
```

### Constructors

| Signature | Description |
| --- | --- |
| `WinterVillageView()` | Creates the scene with the default HUD |
| `WinterVillageView(boolean showHud)` | When `false`, creates only the 3D scene, which is useful for embedding |

Construction synchronously loads the village GLB, the six tokens, the dice,
and the card decks. It must be called on the FX thread.

### Methods

| Method | Returns | Description |
| --- | --- | --- |
| `getCamera()` | `PerspectiveCamera` | Current perspective camera |
| `getCameraController()` | `OrbitCameraController` | Camera orbit controller |
| `getProfile()` | `WinterVillageProfile` | Active v1/v2 configuration |
| `getModelRoot()` | `Group` | Loaded village GLB root node |
| `getPlayerTokens()` | `PlayerTokenLayer` | Public API for the six tokens |
| `getDiceController()` | `DiceController` | Dice and roll flow |
| `getCardController()` | `CardController` | Cards and draw flow |

### HUD and Interaction

The default HUD provides:

```text
Roll Dice · Activation Card · Resource Card · Reset Tokens · Reset Camera
```

Keyboard shortcuts:

```text
Space / D   Roll the dice
A           Draw an activation card
C           Draw a public resource card
T           Reset all tokens to GO
R / Home    Reset the camera
```

The status labels at the bottom report card, dice, and token state.

---

## 4. `WinterVillageProfile`

```java
public final class WinterVillageProfile
```

### Static Configuration

| Constant | Description |
| --- | --- |
| `V1` | Original approved 26 × 20 m pond scene |
| `V2` | Square frozen-lake scene, the default |
| `active()` | Returns the configuration selected by `-Dwinter.village.profile=v1|v2` (default v2) |

### Fields

| Field | Type | Description |
| --- | --- | --- |
| `id` | `String` | `v1` or `v2` |
| `label` | `String` | Display name |
| `assetFileName` | `String` | Scene GLB file name |
| `boardWidth` | `double` | Board outer width in world metres |
| `boardX/boardY/boardZ` | `double` | Board placement |
| `pine05ShiftX` | `double` | X offset for the v1 snow pine (0 in v2) |
| `cameraTargetX/Y/Z` | `double` | Initial camera target |
| `cameraAzimuthDeg` | `double` | Initial azimuth |
| `cameraElevationDeg` | `double` | Initial elevation |
| `cameraDistance` | `double` | Initial distance |
| `hudEyebrow/hudTitle/hudSubtitle/cameraInfo` | `String` | HUD text |

### Scene Parameters

| Parameter | v1 | v2 |
| --- | --- | --- |
| Board outer width | 6.4 m | 7.6 m |
| Board centre | `(3.05, 0.81, 3.40)` | `(0, 0.794, 0)` |
| Camera target | `(-0.013, 1.78, -0.772)` | `(0, 0.88, 0)` |
| Azimuth / elevation / distance | `38° / 33° / 58.753 m` | `38° / 50° / 86.839 m` |
| Deck centre z | `1.864` | `-1.824` |

---

## 5. `OrbitCameraController`

```java
public final class OrbitCameraController
```

### Constructor

```java
OrbitCameraController(PerspectiveCamera camera,
                      WinterVillageProfile profile)
```

### Methods

| Method | Description |
| --- | --- |
| `orbitBy(double dx, double dy)` | Left-drag: changes azimuth/elevation |
| `zoomBy(double deltaY)` | Wheel: changes distance; minimum 2.5 m, maximum 160 m |
| `reset()` | Restores the initial camera for the active profile |
| `focus(Point3D target, double azimuth, double elevation, double distance)` | Sets a custom target and framing for close-ups |
| `cameraPosition()` | Current camera position in world space |
| `target()` | Current look-at target |
| `azimuthDegrees()` / `elevationDegrees()` / `distance()` | Current orbit parameters |

The camera always looks at its target; world `+Y` remains screen-up.

---

## 6. Player Token API

### `TokenType`

```java
public enum TokenType {
    TOP_HAT, RACE_CAR, DOG, SHIP, BOOT, CAT
}
```

| Method | Description |
| --- | --- |
| `id()` | Resource id such as `top_hat` |
| `label()` | Chinese display name |
| `fileName()` | `<id>.glb` |

### `PlayerToken`

```java
public final class PlayerToken
```

| Method | Returns | Description |
| --- | --- | --- |
| `index()` | `int` | Player index (0–5) |
| `colorName()` | `String` | Red/blue/green/yellow/purple/orange |
| `color()` | `Color` | Player accent colour |
| `type()` | `TokenType` | Model type |
| `root()` | `Group` | Position/rotation/scale parent node |
| `tileIndex()` | `int` | Current logical tile (0–39) |

### `PlayerTokenLayer`

```java
public final class PlayerTokenLayer
```

#### Constructor

```java
PlayerTokenLayer(Group parent, WinterVillageProfile profile)
```

Loads the six GLBs, recolours them with the player colours, and places all six
on GO. It must be called on the FX thread.

#### Query and Appearance

| Method | Description |
| --- | --- |
| `getLayer()` | Token layer root node |
| `getPlayerCount()` | Always 6 |
| `getPlayer(int index)` | Returns a `PlayerToken` |
| `getPlayerPosition(int index)` | Current tile index |
| `getTurnIndex()` | Next player to move |
| `isAnimating()` | Whether a move or reset animation is running |
| `getTileTopY()` | World Y of the tile surface |
| `getTileCenter(int tile)` | Tile-centre `Point3D` (y = tile surface) |
| `setTokenScale(int index, double scale)` | Per-token scale around the base centre |
| `setTokenRotation(int index, double degrees)` | Per-token rotation around Y |
| `setTurnIndex(int index)` | Sets the player used by the next `moveNextPlayer` |

#### Movement

| Method | Returns | Description |
| --- | --- | --- |
| `moveNextPlayer(int steps)` | `boolean` | Moves the player at `turnIndex` and advances the turn; returns false while animating |
| `movePlayerSteps(int index, int steps)` | `boolean` | Moves a specific player forward/backward; positive is forward, negative is backward; returns false while animating |
| `movePlayerToTile(int index, int tile)` | `boolean` | Walks the forward path to a tile; does not advance the turn |
| `setPlayerTile(int index, int tile, boolean animate)` | `boolean` | Sets an exact tile; `false` teleports without movement callbacks |
| `teleportPlayer(int index, int tile)` | `void` | Moves immediately with no animation |
| `resetAll(boolean animate)` | `void` | Returns all six tokens to GO |

The path uses `BoardGeometry.norm` and supports all four sides, corners, passing
GO, and 40-tile wrap-around. Each step takes 230 ms with a small vertical hop;
the logical `tileIndex` is updated when the whole move completes.

#### Callbacks

```java
void setOnPlayerMoveStarted(BiConsumer<Integer,Integer> listener);
void setOnPlayerMoved(BiConsumer<Integer,Integer> listener);
void setOnMovementFinished(BiConsumer<Integer,Integer> listener);
```

- Arguments are `playerIndex, tileIndex`.
- `onPlayerMoveStarted` fires before the animation begins.
- `onPlayerMoved` and `onMovementFinished` fire after the full move and settle,
  when the logical position has been updated.
- Teleports (`teleportPlayer` / `setPlayerTile(..., false)`) do not fire these
  movement callbacks.
- `onPlayerMoved` is intended for UI state.
- `onMovementFinished` is intended for dice-turn coordination.
- **Setters are single-listener**: setting a callback again replaces the previous
  listener.

---

## 7. Dice API

### `DiceController`

```java
public final class DiceController
```

#### Constructor

```java
DiceController(Group parent,
               WinterVillageProfile profile,
               PlayerTokenLayer playerTokens)
```

Loads `dice_01.glb` once and creates two independent instances. It must be
called on the FX thread.

#### State

```java
public enum State { IDLE, ROLLING, RESULT_READY, MOVING }
```

| State | Meaning |
| --- | --- |
| `IDLE` | Ready to start the next roll |
| `ROLLING` | Dice animation is running |
| `RESULT_READY` | Dice have settled and a result is available; waiting to move |
| `MOVING` | The current token is moving step by step |

#### Result

```java
public record RollResult(int die1, int die2) {
    public int total();
}
```

#### Methods

| Method | Returns | Description |
| --- | --- | --- |
| `rollRandom()` | `boolean` | Production roll; each die is independent and uniform from 1 to 6 |
| `roll(int first, int second)` | `boolean` | Deterministic roll for tests; values must be 1–6 |
| `setPreviewValues(int first, int second)` | `boolean` | Sets resting faces without playing an animation |
| `setAutoMovement(boolean)` | `void` | Defaults to true; when false, rules code owns movement |
| `isAutoMovement()` | `boolean` | Current automatic-movement mode |
| `beginMovement(int steps)` | `boolean` | Moves the current player with rules-decided steps in `RESULT_READY` |
| `completeTurn()` | `void` | Ends the turn without moving in `RESULT_READY` |
| `canRoll()` | `boolean` | True when IDLE, tokens are not moving, and no external work is busy |
| `isBusy()` | `boolean` | True when not IDLE |
| `getState()` | `State` | Current state |
| `getLastResult()` | `RollResult` | Most recent result |
| `getFocusPoint()` | `Point3D` | Camera target for a dice close-up |
| `getLayer()` | `Group` | Dice layer root node |
| `setExternalBusy(BooleanSupplier)` | `void` | Additional busy condition (defaults to card animation) |

#### Callbacks

```java
void setOnRollResult(Consumer<RollResult> listener);
void setOnStateChanged(Consumer<State> listener);
void setOnTurnComplete(Runnable listener);
```

- Results are fixed before the animation starts; the final top faces match the
  result exactly.
- `onRollResult` fires after the dice settle.
- `onTurnComplete` fires after player movement completes.
- Setters are single-listener.

#### Roll Flow

```text
IDLE
  → rollRandom()/roll()
  → ROLLING (animation, about 1.3–1.5 s)
  → RESULT_READY (result holds for 380 ms)
  → MOVING (step-by-step movement)
  → IDLE
```

---

## 8. Card API

### `CardType`

```java
public enum CardType {
    ACTIVATION, RESOURCE
}
```

| Method | Description |
| --- | --- |
| `id()` | `activation` or `resource` |
| `deckLabel()` | Card header text |
| `displayName()` | Activation card / public resource card |
| `accent()` | Orange / blue |
| `cards()` | `List<CardDefinition>` with four built-in cards per deck |

### `CardDefinition`

```java
public record CardDefinition(String id, String title, String description) {}
```

### `CardController`

```java
public final class CardController
```

#### Constructor

```java
CardController(Group parent,
               WinterVillageProfile profile,
               OrbitCameraController camera)
```

Must be called on the FX thread. Both decks sit above the MONOPOLY banner.

#### State

```java
public enum State { IDLE, DRAWING }
```

#### Result

```java
public record DrawnCard(CardType type, CardDefinition card) {}
```

#### Methods

| Method | Returns | Description |
| --- | --- | --- |
| `draw(CardType type)` | `boolean` | Draws the next card; false while animating or externally busy |
| `isBusy()` | `boolean` | Whether the state is DRAWING |
| `getState()` | `State` | Current state |
| `getLastDrawn()` | `DrawnCard` | Most recently drawn card |
| `setDeck(CardType, List<CardDefinition>)` | `void` | Replaces a deck and resets its cursor |
| `getDeck(CardType)` | `List<CardDefinition>` | Read-only copy of the current deck |
| `draw(CardType, CardDefinition)` | `boolean` | Shows a specific card without advancing the deck cursor |
| `getFocusPoint()` | `Point3D` | Camera target for a deck close-up |
| `getLayer()` | `Group` | Card layer root node |
| `setExternalBusy(BooleanSupplier)` | `void` | Additional busy condition (defaults to dice/movement) |

#### Callbacks

```java
void setOnStateChanged(Consumer<State> listener);
void setOnCardDrawn(Consumer<DrawnCard> listener);
void setOnCardDismissed(Consumer<DrawnCard> listener);
```

- `onCardDrawn` fires when the card is upright in its display pose.
- `onCardDismissed` fires after the card returns to the deck and is removed.
- The card is removed after the animation and the state returns to `IDLE`.
- Setters are single-listener.

#### Animation

- There is no 180° flip; both sides of the card show the same face.
- Path:

```text
A (deck, flat)
→ B (lift)
→ C (upright above MONOPOLY)
→ D (scaled display, about 1.65×)
→ hold for about 2.1 s
→ C → B → A (exact reverse path)
→ remove
```

- The card faces the camera when the draw begins; it does not rotate to track
  the camera during the hold.
- The dice, card, and token-reset buttons are disabled while a card is drawing.
- The `externalBusy` hook blocks card draws while the dice are animating.

---

## 9. Board Model and Tiles

### `MonopolyBoardModel`

```java
public final class MonopolyBoardModel
```

| Member | Description |
| --- | --- |
| `DESIGN_WIDTH = 940.0` | Board design width |
| `TILE_TOP_DESIGN = 28.7` | Printed tile surface design height |
| `FIELD_TOP_DESIGN = 26.0` | Green field top design height |
| `tileTopY(double outerWidth, double boardY)` | World tile-surface Y |
| `fieldTopY(double outerWidth, double boardY)` | World green-field Y |
| `create(double outerWidth, double x, double y, double z)` | Builds a placeable board `Group` |

Default v2 values:

```text
outerWidth = 7.6 m
board bottom Y = 0.794 m
tile top Y ≈ 1.026 m
field top Y ≈ 1.004 m
```

### `BoardGeometry`

| Member | Description |
| --- | --- |
| `TILES = 40` | Total tile count |
| `MARGIN/CONTENT/BOARD/CORNER/EDGE` | Design dimensions |
| `norm(int)` | Wraps 0–39 |
| `rect(int)` | Tile `Rect` |
| `side(int)` | `BOTTOM/LEFT/TOP/RIGHT/CORNER_*` |
| `centerX(int)` / `centerY(int)` | Content-space centre |

Tile order starts at **GO (0, bottom-right)** and proceeds counter-clockwise:

```text
0 GO
1..9 bottom edge
10 Jail
11..19 left edge
20 Free Parking
21..29 top edge
30 Go To Jail
31..39 right edge
```

### `TileDef` / `TileType`

- `TileDef.all()` returns the name, type, colour, and price for all 40 tiles.
- `TileType`: `GO/PROPERTY/RAILROAD/UTILITY/TAX/CHANCE/COMMUNITY/JAIL/FREE_PARKING/GO_TO_JAIL`.

---

## 10. Asset Loading and Conventions

### `ModelAssetLoader`

```java
public static Group load(Path assetPath) throws IOException;
public static Group instantiateBatched(Path assetPath) throws IOException;
```

- Must be called on the FX thread.
- glTF is Y-up and JavaFX is Y-up, so **no axis conversion is applied**.
- Supports hierarchies, multiple materials, base-colour textures, and emissive
  materials.
- JavaFX has no PBR pipeline: metallic/roughness, normal maps, and clearcoat are
  approximated or ignored.
- Asset convention: metres, Y-up, front +Z, origin at the ground-contact centre
  for pieces and buildings, or at the geometric centre for dice.

### Token GLB

```text
TOKEN_BASE      Dark pedestal with gold trim
TOKEN_BODY      Recolourable main body, material M_TokenBody
TOKEN_DETAILS   Wheels, windows, eyes, collars, sails, gold details
```

`PlayerTokenLayer` only replaces the material of MeshViews whose id starts with
`TOKEN_BODY`.

### Dice GLB

```text
DICE_BODY       Cream body
DICE_PIPS       Dark pips
```

Size is 0.342 m with the geometric centre at the origin; +Y=1, −Y=6, +Z=2,
−Z=5, +X=3, −X=4.

### Cards

Cards are runtime JavaFX geometry with `BoardArt` Canvas textures, not GLB
assets:

```text
BoardArt.cardTop(deckLabel, accent)   // deck top surface
BoardArt.cardFace(deckLabel, title, description, accent) // card face
```

---

## 11. Runtime Configuration

### System Properties

| Property | Default | Description |
| --- | --- | --- |
| `winter.village.profile` | `v2` | `v1` or `v2` |
| `winter.village.asset` | Selected by profile | Absolute path to a village GLB |
| `winter.village.tokens.root` | `assets/tokens` | Token GLB directory |
| `winter.village.dice.root` | `assets/dice` | Dice GLB directory |
| `monopoly.assets.root` | none | Fallback root for `pieces/` and `props/dice_01/` |

### Environment Variables

| Variable | Description |
| --- | --- |
| `JAVAFX_LIB` | JavaFX SDK `lib` directory |
| `WINTER_VILLAGE_PROFILE` | Profile selection used by the run scripts |

### Launch Commands

```bash
./run.sh
WINTER_VILLAGE_PROFILE=v1 ./run.sh
JAVAFX_LIB=/path/to/javafx-sdk-21.0.8/lib ./run.sh
```

---

## 12. Coordinates and Scale

```text
glTF / JavaFX: Y-up, front +Z, metres
Board design units: 940 × 940
v2 world width: 7.6 m
```

- The board is built by `MonopolyBoardModel.create` with outer width
  `outerWidth`.
- Tile centres use `BoardGeometry.centerX/centerY` (design units), subtract 470,
  multiply by `outerWidth/940`, and add the board position.
- Token bases sit at `Y=0`; their world contact point is `tileTopY`.
- Dice use the geometric centre as origin; centre height is field Y + 0.172 m.
- Decks sit at field `z = boardZ − boardWidth × 0.24`; the three-card stack top
  is `fieldTop + 0.055 m`.

---

## 13. Threading, State, and Error Handling

- All JavaFX node operations, scene construction, animation, and snapshots must
  run on the FX Application Thread.
- `PlayerTokenLayer` movement, `DiceController` rolls, and `CardController`
  draws are asynchronous through `Timeline`/`PauseTransition`.
- Use `isAnimating()`, `isBusy()`, `canRoll()`, and `externalBusy` to prevent
  overlapping actions.
- The HUD disables roll/draw/reset buttons while busy; camera controls remain
  available.
- A single-listener callback setter replaces the previous listener. If multiple
  subscribers are needed, forward events from an owning layer.
- Load failures are shown in an error label inside `WinterVillageView` and print
  a stack trace.

---

## 14. Known Limitations

- JavaFX has no PBR or soft shadows; materials are approximations.
- Dice, card, and token animations are keyframed, not rigid-body physics.
- Card content is a display mechanism; it does not execute property purchase,
  rent, or resource settlement.
- The card's camera-facing angle is fixed when the draw starts; rotating the
  camera during the hold does not make the card track it.
- There is no card box. The parent project's card system and old dice primitives
  are not modified.
- The integration target is the v2 lake board scene in `winter_village_javafx`;
  the parent project's `src/` tree is unchanged.
- When all six tokens share a tile, they use a 2×3 slot layout; narrow models may
  overlap slightly but never occupy exactly the same position.

---

## 15. Rules Integration API

Rules code connects through the three controllers exposed by
`WinterVillageView`:

```java
DiceController dice = view.getDiceController();
PlayerTokenLayer tokens = view.getPlayerTokens();
CardController cards = view.getCardController();
```

### 15.1 Taking Over Movement After a Roll

```java
dice.setAutoMovement(false);
dice.setOnRollResult(result -> {
    int player = tokens.getTurnIndex();
    int steps = rules.modifySteps(player, result);
    if (steps == 0) {
        dice.completeTurn();
    } else {
        dice.beginMovement(steps);
    }
});
```

- `beginMovement` and `completeTurn` are only valid in `RESULT_READY`.
- `beginMovement` uses the current `tokens.getTurnIndex()` and advances the turn.
- To choose a player explicitly, call `tokens.setTurnIndex(index)` first.

### 15.2 Setting Token Positions

```java
tokens.setPlayerTile(3, 10, false);       // teleport to Jail, no movement callbacks
tokens.movePlayerToTile(2, 12);           // animate to tile 12
tokens.setPlayerTile(1, 20, true);        // animate to tile 20
tokens.setTurnIndex(4);                   // next moveNextPlayer uses player 4
```

### 15.3 Card Content and Effects

```java
cards.setDeck(CardType.ACTIVATION, List.of(
    new CardDefinition("city_pulse", "City Pulse", "Activate one building for free."),
    new CardDefinition("extra_turn", "Extra Turn", "Take one additional action.")
));

cards.setOnCardDrawn(drawn -> {
    switch (drawn.card().id()) {
        case "go_to_jail" -> tokens.setPlayerTile(
                tokens.getTurnIndex(), 10, false);
        case "move_three" -> tokens.movePlayerSteps(
                tokens.getTurnIndex(), 3);
        default -> { }
    }
});
```

### 15.4 Recommended Event Order

```text
Roll:
  ROLLING → RESULT_READY → onRollResult
  → beginMovement / completeTurn
  → MOVING → onPlayerMoveStarted
  → onPlayerMoved / onMovementFinished
  → IDLE → onTurnComplete

Draw:
  DRAWING → onCardDrawn
  → card display → return to deck
  → IDLE → onCardDismissed
```

For a complete example and interlock notes, see
[`docs/RULES_API.md`](docs/RULES_API.md).

---

## 16. Files and Resources

- Project source: `src/main/java/wintervillage/**`
- Application assets: `assets/**`
- Dependencies: `lib/*.jar`
- Rules API guide: `docs/RULES_API.md`
