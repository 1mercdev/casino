# Casino 

A Paper plugin for a multi-game casino (Roulette, Slots, Blackjack, Coinflip,
Mines, HiLo).


Thank you claude for sponsoring this readme and documentation throughout the code! (i cannot bother doing it myself)

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

## Roulette

European wheel, single 0. Deliberately does **not** use the config `house-edge` value —
the edge here (~2.70%) is structural, coming from the single 0 pocket combined with
standard casino payout ratios (35:1 straight up, 2:1 dozens, 1:1 even-money). Verified
analytically that all three bet shapes land on exactly the same edge, so there's nothing
to tune. Only straight-up numbers and the even-money/dozen outside bets are supported —
no split/street/corner/column bets, and one bet per spin (no multi-bet betting slip);
clicking a number or an outside bet places the current bet amount on it and spins
immediately, same one-click-resolves pattern as Slots.

## HiLo

The one stateful game so far — a round genuinely sits "in progress" between clicks (bet
placed, multiplier already banked, next guess not yet made). Payout per correct guess is
computed live from the actual win probability for the current card (`1 / P`), scaled by
`house-edge`, so there's no fixed payout table the way Slots has one — the odds shown on
the Higher/Lower buttons are the literal numbers driving the payout.

Because a streak's multiplier has no fixed ceiling, `HouseBankroll` capacity is checked
before *every single guess*, not just once at the start of a round — if the bankroll
couldn't cover the next step, that guess is refused and the player has to cash out
instead of extending further. Closing the GUI mid-round auto-cashes-out at the current
multiplier (via `onClose`) rather than just refunding the bet, so walking away never
costs a player a streak they'd already earned.

One known gap, worth knowing about: while a HiLo round is open, its potential payout
isn't reserved as a lump sum the way Slots/Roulette reserve theirs — it only gets
checked against the bankroll one step at a time. With ~15 casual players this is a
low-impact edge case, but it's the reason a thin bankroll could theoretically get
squeezed by a single lucky streak in a way a quick top-up would fix.

## Shop

`ShopGame`/`ShopSession` aren't really a "game" — no bet, no win/loss, no house bankroll
involvement — but they implement `CasinoGame` anyway purely to get a free hub icon and
click routing through the existing listener, the same way Slots and Coinflip do. A
purchase resolves atomically on click (deduct chips, give the item, log it), so there's
never anything mid-transaction for `onClose` to refund.

The catalog lives in `config.yml`'s `shop.items` list, not in code — add, remove, or
reprice items there. Each entry supports an optional `permission` node (shown as a
locked barrier icon to players who lack it, rather than hidden — see the `elytra`
example item) and an optional `lore` list for extra flavor text. Purchases log to their
own `purchases` table in `casino.db`, separate from `bets`/`transactions`.

## Mines

Same push-your-luck shape as HiLo — fair-odds-by-construction payout, checked against
the bankroll before every reveal rather than reserved as a lump sum up front, auto-cash-out
on `onClose`. The difference from HiLo: Mines samples *without* replacement from a fixed
board (the mine layout is generated once per round, not redrawn per click), so the odds
are hypergeometric rather than independent — verified analytically that this is still
exactly fair pre-scaling at every mine count and reveal depth, same as HiLo's construction.
Mine count is itself adjustable per round (1-24), trading bust risk for a steeper
multiplier curve. After a round ends, the *entire* board is revealed — not just the tile
that was hit — so the layout's legitimacy is visible rather than asserted.

There's no separate DEAL button: clicking any tile while idle both starts the round and
resolves that click as the first pick, the same "click commits" pattern Roulette uses.
That only works here because — unlike HiLo, where the first card reveal has no guess
attached yet — a Mines click is always itself a pick, so the very first click naturally
doubles as the first move.

## Blackjack

The one game with genuinely complex state — a real hand, dealer AI, hit/stand/double.
Infinite shoe (each draw is an independent, uniform pick of 1 of 13 ranks, not a depleting
52-card deck), dealer stands on all 17s, no split, no insurance, double only as the first
action. Payouts are fixed/standard (3:2 blackjack, 1:1 win) rather than config-scaled, same
reasoning as Roulette — blackjack's edge comes structurally from the player busting before
the dealer's hand is even compared, not from a tunable multiplier. That edge lands in the
ordinary small blackjack range (roughly 0.5-1% under reasonable play) — that's the standard
known order of magnitude for this ruleset, not something computed here the way the other
games' edges were.

`onClose` behaves differently here than HiLo/Mines, deliberately: closing mid-hand **voids
the round and refunds the full current wager** (including a double, if taken) rather than
trying to auto-resolve it. HiLo/Mines can fairly auto-cash-out mid-round because "current
multiplier" is a concrete, already-banked value; a blackjack hand mid-decision has no
equivalent — the dealer hasn't played, so resolving it behind a closed GUI would mean
deciding a winner nobody saw. Voiding it is the more honest behavior.

## A note on the hub

The hub GUI has room for exactly 7 game icons. Adding an 8th
game won't error, it'll just silently fail to get an icon.


# Building from source

## A note on sqlite-jdbc and shading

`org.sqlite.*` is not relocated when shading because shit was breaking and i have NO idea why. Make sure your server does not have version mismatches. If you encounter an error related to this, you are allowed to make an issue about it. I'll fix it if it causes enough problems!! >:)


Building requires network access to `repo.papermc.io` for the Paper API dependency.

```
mvn clean package
```
