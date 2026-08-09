package net.mercdev.casino.core.util;

import java.security.SecureRandom;

/**
 * Single shared SecureRandom instance for every game. Plain java.util.Random is a
 * predictable PRNG — its internal state can in principle be reconstructed from enough
 * observed outputs, which matters when the outputs decide who wins real items.
 * SecureRandom avoids that; bets aren't frequent enough for the extra cost to matter.
 */
public final class SecureRng {

    private static final SecureRandom RNG = new SecureRandom();

    private SecureRng() {}

    /** Random int in [0, bound). */
    public static int nextInt(int bound) {
        return RNG.nextInt(bound);
    }

    /** Random int in [min, max]. */
    public static int nextInt(int min, int max) {
        return min + RNG.nextInt(max - min + 1);
    }

    public static double nextDouble() {
        return RNG.nextDouble();
    }

    public static boolean nextBoolean() {
        return RNG.nextBoolean();
    }
}
