# Casino — framework

A Paper plugin skeleton for a multi-game casino (Roulette, Slots, Blackjack, Coinflip,
Mines, HiLo). This pass is **framework only** — no games are implemented yet, so the
hub GUI will show no game icons until the first `CasinoGame` is registered. That's
expected.

## Layout

```
game/
  CasinoGame.java      interface every game implements (id, display name, icon, createSession)
  GameSession.java      abstract per-player round: open() / onClick() / onClose()
  GameRegistry.java      id -> CasinoGame lookup, powers the hub GUI
  SessionManager.java    tracks each player's one active session

economy/
  EconomyManager.java    player chip balances; deposit/withdraw against echo shards
  HouseBankroll.java     house's own balance; caps payouts on house games (see below)

betting/
  BetLimitManager.java   per-game min/max bet (config.yml) + anti-macro cooldown

audit/
  AuditLogger.java       SQLite: players, house, transactions, bets tables

gui/
  CasinoMenuHolder.java  hub GUI, one icon per registered game

listener/
  CasinoGuiListener.java routes clicks/closes to the right GameSession or the hub
  PlayerJoinQuitListener.java loads/saves balances, clears stale sessions

command/
  CasinoCommand.java     /casino, /casino balance|deposit|withdraw|admin

util/
  SecureRng.java         one shared SecureRandom for every game
```

## The house bankroll design

Player deposits/withdrawals only ever convert between a player's own chips and their
own echo shards — that never touches the house bankroll, because it isn't house money.

The bankroll only moves on player-vs-house bets: `reserveBet()` when a wager is placed,
`resolveBet()` when it's paid out (or not). Before accepting any bet, a game should call
`HouseBankroll.canAcceptBet(wager, maxPossiblePayout)` — if the bankroll couldn't survive
the game's absolute best-case payout for that wager, the bet is rejected (or the player
is prompted to lower it). This is what stops a lucky streak from generating chips — and
therefore withdrawable echo shards — that were never actually backed by anything. Seed
the bankroll with `/casino admin bankroll <amount>` after depositing real shards as the
admin.

Coinflip is exempt from all of this: it's PvP, chips move directly between the two
players' balances, house bankroll untouched.

## Building a game

1. Implement `CasinoGame` — `getId()`, `getDisplayName()`, `getMenuIcon()`, and
   `createSession(plugin, player)` returning a new instance of your `GameSession` subclass.
2. Implement your `GameSession` subclass:
   - `open()` — build `this.inventory`, call `player.openInventory(inventory)`.
   - `onClick(event)` — already cancelled by the framework; read `event.getRawSlot()`
     to figure out what was clicked.
   - `onClose(event)` — refund the bet here if `!settled`, so closing early never
     costs the player chips.
3. In `CasinoPlugin.onEnable()`, register it: `gameRegistry.register(new YourGame());`
   (guard this on `config.games.<id>.enabled` once you want that to matter).
4. Use `plugin.getEconomyManager()` for balance changes, `plugin.getHouseBankroll()`
   for house-edge games, `plugin.getBetLimitManager().checkBet(...)` before accepting
   a wager, and `plugin.getAuditLogger().logBet(...)` once a round resolves.

## Building the jar

Requires network access to `repo.papermc.io` for the Paper API dependency (this
sandbox's egress is restricted to a handful of package registries, so the build
couldn't be verified from here — build it locally):

```
mvn clean package
```

The shaded jar lands in `target/casino-core.jar`. Drop it in your server's `plugins/`
folder.
