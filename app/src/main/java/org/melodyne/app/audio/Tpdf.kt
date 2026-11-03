package org.melodyne.app.audio

/** Triangular dither of +-1 LSB. Rounding to 16 bits with it avoids the harsh distortion plain rounding leaves on quiet passages. */
class Tpdf(private var seed: Int = 0x2545F491) {
    private fun uniform(): Double {
        seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
        return (seed and 0x7FFFFFFF) / 2147483648.0
    }
    fun next(): Double = uniform() - uniform()
}
