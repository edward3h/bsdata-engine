/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.gen;

import java.util.List;

/**
 * A small deterministic random source using only 32-bit integer arithmetic, so the JVM and the compiled JavaScript
 * produce the same sequence. It is not for anything that needs good randomness.
 */
final class Rng {
    private int state;
    private int counter;
    private final int salt;

    Rng(int seed) {
        this.state = seed == 0 ? 0x2545F491 : seed;
        this.salt = mix(seed ^ 0x5bd1e995);
    }

    int next() {
        int x = state;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        state = x;
        return x;
    }

    /** A number in [0, bound). */
    int below(int bound) {
        return bound <= 1 ? 0 : (next() >>> 1) % bound;
    }

    /** A number in [lo, hi]. */
    int between(int lo, int hi) {
        return lo + below(hi - lo + 1);
    }

    boolean chance(double p) {
        return below(1_000_000) < (int) (p * 1_000_000);
    }

    <T> T pick(List<T> items) {
        return items.get(below(items.size()));
    }

    static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    /** A new unique id in the data's {@code xxxx-xxxx-xxxx-xxxx} form. */
    String id() {
        int c = ++counter;
        int a = mix(c * 0x9E3779B9 + salt);
        int b = mix(c ^ salt ^ 0x68E31DA4);
        char[] out = new char[19];
        int pos = 0;
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                out[pos++] = '-';
            }
            int word = i < 8 ? a : b;
            int shift = 28 - 4 * (i % 8);
            out[pos++] = Character.forDigit((word >>> shift) & 0xF, 16);
        }
        return new String(out);
    }
}
