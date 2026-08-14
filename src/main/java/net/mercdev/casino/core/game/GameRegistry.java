package net.mercdev.casino.core.game;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Holds every registered {@link CasinoGame}, keyed by its id. Insertion order is
 *  preserved so the hub GUI lists games in a predictable, config-independent order. */
public class GameRegistry {

    private final Map<String, CasinoGame> games = new LinkedHashMap<>();

    public void tryRegister(CasinoGame game, boolean enabled) {
        if (enabled)
            games.put(game.getId().toLowerCase(), game);
    }

    public Optional<CasinoGame> get(String id) {
        return Optional.ofNullable(games.get(id.toLowerCase()));
    }

    public Collection<CasinoGame> all() {
        return games.values();
    }
}
