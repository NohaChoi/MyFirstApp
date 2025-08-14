package com.example.myfirstapp

import kotlin.math.*

/**
 * This class handles all the Glicko-2 rating calculations.
 * It's a direct translation of the math from your Python script.
 */
class Glicko2(private val tau: Double = 0.5) {

    // These are constants used in the calculations.
    companion object {
        private const val DEFAULT_RATING = 1500.0
        private const val SCALE = 173.7178
        private const val EPS = 0.000001
    }

    // A simple data structure to hold the results of an update.
    data class UpdateResult(val rating: Double, val rd: Double, val vol: Double)

    // Scales ratings down for calculations.
    private fun scaleDown(rating: Double, rd: Double): Pair<Double, Double> {
        val mu = (rating - DEFAULT_RATING) / SCALE
        val phi = rd / SCALE
        return Pair(mu, phi)
    }

    // Scales ratings back up for display.
    private fun scaleUp(mu: Double, phi: Double): Pair<Double, Double> {
        val rating = mu * SCALE + DEFAULT_RATING
        val rd = phi * SCALE
        return Pair(rating, rd)
    }

    private fun g(phi: Double): Double {
        return 1.0 / sqrt(1.0 + 3.0 * phi.pow(2) / PI.pow(2))
    }

    private fun E(mu: Double, muOpp: Double, phiOpp: Double): Double {
        return 1.0 / (1.0 + exp(-g(phiOpp) * (mu - muOpp)))
    }

    // The main function that calculates new ratings after a match.
    fun updateRatings(winner: Player, loser: Player): Pair<UpdateResult, UpdateResult> {
        val (muW, phiW) = scaleDown(winner.rating, winner.rd)
        val (muL, phiL) = scaleDown(loser.rating, loser.rd)

        // --- Calculate for Winner ---
        val gL = g(phiL)
        val eW = E(muW, muL, phiL)
        val vW = 1.0 / (gL.pow(2) * eW * (1 - eW))
        val deltaW = vW * gL * (1.0 - eW)
        val volWNew = computeNewVolatility(deltaW, phiW, vW, winner.vol)
        val phiWPrime = sqrt(phiW.pow(2) + volWNew.pow(2))
        val phiWNew = 1.0 / sqrt(1.0 / phiWPrime.pow(2) + 1.0 / vW)
        val muWNew = muW + phiWNew.pow(2) * gL * (1.0 - eW)
        val (ratingWNew, rdWNew) = scaleUp(muWNew, phiWNew)
        val winnerResult = UpdateResult(ratingWNew, rdWNew, volWNew)

        // --- Calculate for Loser ---
        val gW = g(phiW)
        val eL = E(muL, muW, phiW)
        val vL = 1.0 / (gW.pow(2) * eL * (1 - eL))
        val deltaL = vL * gW * (0.0 - eL)
        val volLNew = computeNewVolatility(deltaL, phiL, vL, loser.vol)
        val phiLPrime = sqrt(phiL.pow(2) + volLNew.pow(2))
        val phiLNew = 1.0 / sqrt(1.0 / phiLPrime.pow(2) + 1.0 / vL)
        val muLNew = muL + phiLNew.pow(2) * gW * (0.0 - eL)
        val (ratingLNew, rdLNew) = scaleUp(muLNew, phiLNew)
        val loserResult = UpdateResult(ratingLNew, rdLNew, volLNew)

        return Pair(winnerResult, loserResult)
    }

    private fun computeNewVolatility(delta: Double, phi: Double, v: Double, vol: Double): Double {
        val a = ln(vol.pow(2))
        var A = a
        // CORRECTED: 'B' needs to change, so it must be a 'var'.
        var B = if (delta.pow(2) > phi.pow(2) + v) {
            ln(delta.pow(2) - phi.pow(2) - v)
        } else {
            var k = 1.0
            while (f(a - k * tau, delta, phi, v, a) < 0) {
                k++
            }
            a - k * tau
        }

        // CORRECTED: 'fA' and 'fB' also need to change inside the loop.
        var fA = f(A, delta, phi, v, a)
        var fB = f(B, delta, phi, v, a)

        while (abs(B - A) > EPS) {
            val C = A + (A - B) * fA / (fB - fA)
            val fC = f(C, delta, phi, v, a)
            if (fC * fB < 0) {
                A = B
                fA = fB
            } else {
                fA /= 2.0
            }
            B = C
            fB = fC
        }
        return exp(A / 2.0)
    }

    private fun f(x: Double, delta: Double, phi: Double, v: Double, a: Double): Double {
        val ex = exp(x)
        val term1 = (ex * (delta.pow(2) - phi.pow(2) - v - ex)) / (2.0 * (phi.pow(2) + v + ex).pow(2))
        val term2 = (x - a) / tau.pow(2)
        return term1 - term2
    }
}