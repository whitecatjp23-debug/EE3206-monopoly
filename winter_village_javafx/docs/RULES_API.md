# Rules Integration API

This guide explains how rules code can take over dice movement, token
placement, and card effects. The default flow is unchanged:

```text
roll dice → move the current player automatically → next turn
```

Rules code may instead own movement or replace the contents of either deck.

All APIs must be called on the **JavaFX Application Thread**. Animations run
asynchronously through JavaFX `Timeline` and `PauseTransition`; do not use
`Thread.sleep()`.

---

## 1. Default Event Order

### 1.1 Automatic Roll (Default)

```text
DiceController.setOnStateChanged(ROLLING)
→ dice roll animation
→ DiceController.setOnStateChanged(RESULT_READY)
→ DiceController.setOnRollResult(RollResult)
→ 380 ms hold
→ DiceController.setOnStateChanged(MOVING)
→ PlayerTokenLayer.setOnPlayerMoveStarted(player, targetTile)
→ step-by-step movement + settle
→ PlayerTokenLayer.setOnPlayerMoved(player, tile)
→ PlayerTokenLayer.setOnMovementFinished(player, tile)
→ DiceController.setOnStateChanged(IDLE)
→ DiceController.setOnTurnComplete()
```

- When `onRollResult` fires, the dice have settled and the top faces match the
  result.
- When `onTurnComplete` fires, both the logical and visual positions are in
  sync.
- All `setOn...` methods are **single-listener setters**; setting one again
  replaces the previous listener.

### 1.2 Rules-Owned Movement

```java
DiceController dice = view.getDiceController();
PlayerTokenLayer tokens = view.getPlayerTokens();

dice.setAutoMovement(false);
dice.setOnRollResult(result -> {
    int steps = rules.modifySteps(result.total());
    if (steps == 0) {
        dice.completeTurn();          // end the turn without moving
    } else {
        dice.beginMovement(steps);    // move the current player
    }
});
```

- After `setAutoMovement(false)`, the dice remain in `RESULT_READY` and do not
  move automatically.
- `beginMovement(steps)` is only valid in `RESULT_READY`. It delegates to
  `PlayerTokenLayer.moveNextPlayer(steps)` and advances the turn index.
- `completeTurn()` is only valid in `RESULT_READY`. It ends the turn without
  moving and fires `onTurnComplete`.
- To choose a player explicitly, call `tokens.setTurnIndex(index)` first, or use
  `tokens.movePlayerSteps(index, steps)` / `movePlayerToTile(...)` directly and
  then call `dice.completeTurn()`.

### 1.3 Card Draw

```text
CardController.setOnStateChanged(DRAWING)
→ card lifts, stands upright, and scales up
→ at about 700 ms, CardController.setOnCardDrawn(DrawnCard)
→ display hold
→ card returns along the same path
→ CardController.setOnStateChanged(IDLE)
→ CardController.setOnCardDismissed(DrawnCard)
```

- `onCardDrawn` is the entry point for resolving a card effect; the face is
  clearly visible at that point.
- `onCardDismissed` means the card is back in its deck and the next action can
  begin.
- `CardController.isBusy()` is true during a draw. Dice and card actions use
  `externalBusy` to block each other.
- The card animation has no 180° flip; both sides show the same face, so the
  text remains readable.

---

## 2. Card Interface

### 2.1 Replacing Deck Contents

```java
CardController cards = view.getCardController();

cards.setDeck(CardType.ACTIVATION, List.of(
    new CardDefinition("city_pulse", "City Pulse", "Activate one building for free."),
    new CardDefinition("extra_turn", "Extra Turn", "Take one additional action.")
));

cards.setDeck(CardType.RESOURCE, List.of(
    new CardDefinition("supply", "Public Supply", "Gain resources from the public pool.")
));
```

| Method | Description |
| --- | --- |
| `setDeck(CardType, List<CardDefinition>)` | Replaces a deck and resets its draw cursor |
| `getDeck(CardType)` | Returns an immutable copy |
| `draw(CardType)` | Draws the next card from the selected deck, cycling |
| `draw(CardType, CardDefinition)` | Shows a specific card without changing the deck cursor |
| `getLastDrawn()` | Most recent `DrawnCard` |
| `setOnCardDrawn(Consumer<DrawnCard>)` | Fires when the card face is displayed |
| `setOnCardDismissed(Consumer<DrawnCard>)` | Fires after the card returns to its deck |

`CardDefinition`:

```java
public record CardDefinition(String id, String title, String description) {}
```

`id` is the stable key used by rules code; `title` and `description` are display
text only.

### 2.2 Card Effect Example

```java
cards.setOnCardDrawn(drawn -> {
    switch (drawn.card().id()) {
        case "go_to_jail" -> {
            int player = tokens.getTurnIndex();
            tokens.setPlayerTile(player, 10, false); // Jail, immediate
            dice.completeTurn();                    // if a roll is still resolving
        }
        case "move_three" -> {
            int player = tokens.getTurnIndex();
            tokens.movePlayerSteps(player, 3);
        }
        default -> { }
    }
});
```

- Use the `PlayerTokenLayer` movement and tile APIs for card effects that move
  tokens.
- Tokens may move while the card state is `DRAWING`; the dice/card interlock
  still applies.
- Card display is rules-agnostic. Rules code can implement any effect based on
  `id`.

---

## 3. Token Movement Interface

```java
PlayerTokenLayer tokens = view.getPlayerTokens();
```

### 3.1 Movement Methods

| Method | Behaviour | Advances turn |
| --- | --- | --- |
| `moveNextPlayer(int steps)` | Moves the player at `turnIndex` | Yes, after a successful start |
| `movePlayerSteps(int index, int steps)` | Moves a specific player by positive/negative steps | No |
| `movePlayerToTile(int index, int tile)` | Walks the forward path to a tile | No |
| `setPlayerTile(int index, int tile, boolean animate)` | Sets an exact tile, animated or immediate | No |
| `teleportPlayer(int index, int tile)` | Immediate teleport, no movement callbacks | No |
| `resetAll(boolean animate)` | Returns all six tokens to GO | Resets turn to 0 |

Notes:

- The forward path covers all four board sides, corners, GO, and 40-tile
  wrap-around.
- `movePlayerToTile` always uses the forward path. Use negative
  `movePlayerSteps` when a backward move is required.
- `setPlayerTile(..., false)` and `teleportPlayer` are **silent**: they do not
  fire `onPlayerMoveStarted`, `onPlayerMoved`, or `onMovementFinished`. Rules
  code should update its own external UI state.
- `setTurnIndex(int)` selects the player used by the next `moveNextPlayer`.

### 3.2 Movement Callbacks

```java
void setOnPlayerMoveStarted(BiConsumer<Integer,Integer> listener);
void setOnPlayerMoved(BiConsumer<Integer,Integer> listener);
void setOnMovementFinished(BiConsumer<Integer,Integer> listener);
```

- `onPlayerMoveStarted(player, targetTile)`: before the animation begins.
- `onPlayerMoved(player, tile)`: after the full animation and settle; the
  logical position has been updated.
- `onMovementFinished(player, tile)`: fires immediately after `onPlayerMoved`
  for dice-turn coordination.
- All callbacks run on the FX thread; `tile` is a 0-based tile index.

### 3.3 Reading State

```java
int count = tokens.getPlayerCount();          // 6
int tile = tokens.getPlayerPosition(index);   // 0..39
int next = tokens.getTurnIndex();
boolean moving = tokens.isAnimating();
Point3D center = tokens.getTileCenter(tile);
```

---

## 4. Dice Interface

```java
DiceController dice = view.getDiceController();
```

| Method | Description |
| --- | --- |
| `rollRandom()` | Production roll: 2 × uniform 1–6 |
| `roll(int d1, int d2)` | Deterministic result for tests and rules validation |
| `setPreviewValues(int d1, int d2)` | Sets resting faces without rolling |
| `setAutoMovement(boolean)` | Defaults to true; false hands movement to rules code |
| `isAutoMovement()` | Current mode |
| `beginMovement(int steps)` | Moves the current player with rules-computed steps in `RESULT_READY` |
| `completeTurn()` | Ends the turn without moving in `RESULT_READY` |
| `canRoll()` | Whether the next roll may start |
| `isBusy()` / `getState()` | State queries |
| `getLastResult()` | Most recent `RollResult` |
| `setOnRollResult(Consumer<RollResult>)` | Settled result |
| `setOnStateChanged(Consumer<State>)` | State changes |
| `setOnTurnComplete(Runnable)` | Movement/turn complete |
| `setExternalBusy(BooleanSupplier)` | Additional busy condition |

`RollResult`:

```java
public record RollResult(int die1, int die2) {
    public int total();
}
```

Rules code can also block rolls with
`setExternalBusy(() -> rulesAnimationRunning)`.

---

## 5. Recommended Rules Layer

```java
public final class RulesLayer {
    private final DiceController dice;
    private final PlayerTokenLayer tokens;
    private final CardController cards;

    public RulesLayer(WinterVillageView view) {
        this.dice = view.getDiceController();
        this.tokens = view.getPlayerTokens();
        this.cards = view.getCardController();

        dice.setAutoMovement(false);
        dice.setOnRollResult(this::afterRoll);
        dice.setOnTurnComplete(this::afterTurn);

        cards.setOnCardDrawn(this::resolveCard);
    }

    private void afterRoll(DiceController.RollResult result) {
        int player = tokens.getTurnIndex();
        int steps = computeSteps(player, result);
        if (steps == 0) {
            dice.completeTurn();
        } else {
            dice.beginMovement(steps);
        }
    }

    private void resolveCard(CardController.DrawnCard drawn) {
        // Apply resource, movement, or teleport logic by drawn.card().id().
    }

    private void afterTurn() {
        // Update rules state and prepare the next player.
    }
}
```

---

## 6. Interlocks and Cautions

- **Automatic movement is the default.** Movement is handed to rules code only
  after `setAutoMovement(false)`.
- `beginMovement` and `completeTurn` are valid only in `RESULT_READY`.
- `moveNextPlayer` advances the turn index as soon as the move starts;
  `movePlayerSteps` and `movePlayerToTile` do not advance it.
- Teleports do not fire movement callbacks. Use `movePlayerToTile` when rules
  logic needs the movement events, or update rules state directly.
- Dice and card draws block each other through `externalBusy`. Do not start two
  long, interdependent animations from the same callback.
- Callback setters are single-listener. If multiple subscribers are needed,
  dispatch from a dedicated rules layer.
- The card animation has no flip. Rules code only needs `id`; it does not need
  to parse the display text.
- All operations run on the FX thread. Do not block inside animation callbacks.
