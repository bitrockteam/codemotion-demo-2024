package io.simplematter.waterstream.vehiclesimulator.domain

import java.lang.RuntimeException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.random.Random
import org.slf4j.LoggerFactory

class Modelssuer(seed: Long) {
    private val log = LoggerFactory.getLogger(Modelssuer::class.java)

    private val generatedPlates = ConcurrentSkipListSet<String>()
    private val returnedPlates = ConcurrentLinkedQueue<String>()
    private val colors = listOf("White", "Black", "Blue", "Red")

    private val rng = Random(seed)

    private fun generatePlate(remainingAttempts: Int): String {
        val begin: String = listOf(1, 2).map { CharRange('A', 'F').random(rng) }.joinToString("")
        val end: String = listOf(1, 2).map { CharRange('A', 'Z').random(rng) }.joinToString("")
        val middle: String = listOf(1, 2, 3).map { IntRange(0, 9).random(rng) }.joinToString("")
        val plate = "$begin$middle$end"
        if (generatedPlates.add(plate))
            return plate
        else if (remainingAttempts > 0)
            return generatePlate(remainingAttempts - 1)
        else
            throw RuntimeException("Unable to generate a plate")
    }

    fun getModel(): Int {
        return IntRange(1, 30).random(rng)
    }

    fun getColor(): String {
        return colors.get(IntRange(0, colors.size - 1).random(rng))
    }

    companion object {
        val default = Modelssuer(20200331)
    }
}
