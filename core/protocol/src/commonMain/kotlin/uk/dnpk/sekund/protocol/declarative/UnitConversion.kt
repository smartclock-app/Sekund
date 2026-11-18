package uk.dnpk.sekund.protocol.declarative

/**
 * Converts numbers between units, for the `|convert:<from>:<to>` template filter, e.g.
 * `{$.wind.speed|convert:m/s:mph|round}`.
 *
 * Each unit belongs to a family; converting between families (say mph to °C) isn't possible.
 * Within a family every unit is defined by how to get to and from one base unit.
 */
object UnitConversion {

    /** One unit: how to turn a value into the family's base unit, and back. */
    private class Unit(val family: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

    private val units: Map<String, Unit> = mapOf(
        // Temperature; base = Celsius.
        "C" to Unit("temperature", { c -> c }, { c -> c }),
        "F" to Unit("temperature", { f -> (f - 32) * 5 / 9 }, { c -> c * 9 / 5 + 32 }),
        "K" to Unit("temperature", { k -> k - 273.15 }, { c -> c + 273.15 }),
        // Speed; base = metres per second.
        "m/s" to Unit("speed", { v -> v }, { v -> v }),
        "km/h" to Unit("speed", { v -> v / 3.6 }, { v -> v * 3.6 }),
        "mph" to Unit("speed", { v -> v * 0.44704 }, { v -> v / 0.44704 }),
        "kn" to Unit("speed", { v -> v * 0.514444 }, { v -> v / 0.514444 }),
        // Length (rain, snow); base = millimetres.
        "mm" to Unit("length", { v -> v }, { v -> v }),
        "in" to Unit("length", { v -> v * 25.4 }, { v -> v / 25.4 }),
    )

    /** Every unit name the filter accepts. Shown in validation messages. */
    val NAMES: Set<String> = units.keys

    /** [value] converted from [from] to [to], or null if either unit is unknown or they don't match. */
    fun convert(value: Double, from: String, to: String): Double? {
        val source = units[from] ?: return null
        val target = units[to] ?: return null
        if (source.family != target.family) return null
        return target.fromBase(source.toBase(value))
    }
}
