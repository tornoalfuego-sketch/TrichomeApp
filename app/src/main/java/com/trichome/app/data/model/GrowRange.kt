package com.trichome.app.data.model

import androidx.room.TypeConverter

/**
 * A closed band with a low bound and a high bound, in one unit.
 *
 * ## Why this is a type and not two floats
 *
 * pH is not `6.2`. It is `6.0 - 6.5`, and EC is not `1.4`, it is `1.2 - 1.6`. A
 * grower who writes a lower bound and never writes the upper one has not
 * recorded a range of `6.0 - 6.0`; they have recorded a half-typed form that the
 * UI will happily render as a target, and the plant will be driven to a number
 * the grower never chose.
 *
 * Two loose columns cannot express that difference: `phLow = 6.0` with
 * `phHigh = null` is representable, and it means nothing. So the band is one
 * value with two bounds, [low] <= [high] enforced in the constructor, and a
 * half-filled band is not a malformed instance — it is unrepresentable. A band
 * the grower has not filled in is `null`, which the UI renders as "Sin definir"
 * rather than as `0.0 - 0.0`.
 *
 * ## Why it lives in a single TEXT column
 *
 * Two bounds are two numbers, and SQLite has no row type. Storing them as two
 * REAL columns is exactly the shape this type exists to reject, so the band is
 * encoded as one TEXT value (`"6.0:6.5"`).
 *
 * The cost is that the column does not sort or compare numerically, and it is
 * not queryable as a number. That is accepted deliberately: a band is a target
 * the grower *declares* and the UI *displays*. Nothing aggregates protocols by
 * pH band, and if something later needs to, it needs a measured-event column
 * (`grow_events.ph`, which is a real reading) rather than a declared target.
 *
 * The encoding round-trips exactly: [Float.toString] is locale-independent and
 * produces the shortest decimal string that parses back to the same [Float], so
 * `decode(encode(x)) == x` for every band.
 */
data class GrowRange(
    /** Lower bound, inclusive. */
    val low: Float,
    /** Upper bound, inclusive. Always >= [low]. */
    val high: Float
) {
    init {
        require(!low.isNaN() && !high.isNaN()) {
            "A band needs two real bounds; NaN means the value was never parsed."
        }
        require(!low.isInfinite() && !high.isInfinite()) {
            "A band needs two finite bounds; an infinity means the value was never parsed."
        }
        require(low <= high) {
            "Band bounds are inverted: low=$low, high=$high. A band whose low bound is " +
                "above its high bound is a typo, not a target, and accepting it would let " +
                "the UI render '6.5 - 6.0' as a protocol the grower never wrote."
        }
    }

    /** `true` when both bounds are the same number, so the band renders as one value. */
    val isSingle: Boolean get() = low == high

    companion object {
        /**
         * Separator between the two bounds in the stored form. A colon, not a
         * comma: a comma is a decimal separator in Spanish copy, and a band
         * string is read by [GrowRangeText] in the same breath as that copy.
         */
        const val SEPARATOR: Char = ':'

        /** The stored form of a band, or `null` for no band at all. */
        fun encode(range: GrowRange?): String? {
            if (range == null) return null
            return "${range.low}$SEPARATOR${range.high}"
        }

        /**
         * The band behind a stored value, or `null` when there is none to read.
         *
         * `null` covers three cases on purpose: an unset column, an empty
         * string, and text that does not decode as a band. The third case is the
         * one that matters — a column written by a future build, or damaged on
         * disk, must not throw inside a Room row mapper. Throwing there crashes
         * the list that was merely trying to render a protocol; returning `null`
         * shows the field as "Sin definir", which is the honest description of a
         * value this build cannot read.
         *
         * Every rejection is a test case in `GrowRangeTest`, so a decoding rule
         * cannot be loosened by accident.
         */
        fun decode(raw: String?): GrowRange? {
            val text = raw?.trim() ?: return null
            if (text.isEmpty()) return null
            val parts = text.split(SEPARATOR)
            // Exactly two bounds. A third field means the stored form changed
            // and this build does not know how to read it.
            if (parts.size != 2) return null
            val low = parts[0].trim().toFloatOrNull() ?: return null
            val high = parts[1].trim().toFloatOrNull() ?: return null
            // `toFloatOrNull` happily accepts "NaN" and "Infinity", and every
            // comparison against them is false — including `low > high`, which
            // is the inverted-bounds guard below. Without this check a stored
            // "NaN:NaN" reached the constructor and threw from inside a Room row
            // mapper, which is the crash this function exists to prevent.
            if (!low.isFinite() || !high.isFinite()) return null
            // The constructor throws on inverted bounds, so the check is here
            // rather than in a try/catch: decode reports, it does not fail.
            if (low > high) return null
            return GrowRange(low, high)
        }
    }
}

/**
 * Room bridge between [GrowRange] and its stored TEXT form.
 *
 * Scoped to [Protocol] via `@TypeConverters` rather than registered globally:
 * a global converter pair of `GrowRange` <-> `String` would sit close enough
 * to any future string column to be a silent coercion, and the band is the only
 * structured value on the protocol header.
 *
 * A class, not an `object`: Room instantiates it, so the converter methods have
 * to be ordinary instance methods.
 */
class GrowRangeConverters {

    @TypeConverter
    fun toGrowRange(stored: String?): GrowRange? = GrowRange.decode(stored)

    @TypeConverter
    fun fromGrowRange(range: GrowRange?): String? = GrowRange.encode(range)
}