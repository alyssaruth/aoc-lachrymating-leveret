data class Valve(val room: String, val flowRate: Int)
data class PersonState(val currentValve: Valve, val prevValves: Set<Valve>)
data class VolcanoState(
    val myState: PersonState,
    val elephantState: PersonState?,
    val releasedValves: Set<Pair<Int, Valve>>
)

data class VolcanoStateHash(val personStates: Set<PersonState?>, val released: Set<Pair<Int, Valve>>)

class Day16 : Solver {
    override val day = 16

    private val input = readStringList("16").map(::parseValves)
    private val valvesMap = getRoutedValvesMap(input)
    private val releasableValves = valvesMap.keys.filter { it.flowRate > 0 }
    private val startingValve = valvesMap.keys.first { it.room == "AA" }

    override fun partA() =
        runSimulation(initialState(false), 29)

    override fun partB() =
        runSimulation(initialState(true), 25)

    private fun initialState(withElephant: Boolean) =
        VolcanoState(initialPersonState(), if (withElephant) initialPersonState() else null, emptySet())

    private fun initialPersonState() = PersonState(startingValve, emptySet())

    private fun canReleaseValve(move: VolcanoState, valve: Valve) =
        valve.flowRate > 0 && !move.releasedValves.any { it.second == valve }

    private fun countTotalReleased(move: VolcanoState) = move.releasedValves.sumOf {
        it.first * it.second.flowRate
    }

    private fun canSurpassMax(timeRemaining: Int, move: VolcanoState, currentMax: Int) =
        getTheoreticalMax(timeRemaining, move) > currentMax

    private fun getTheoreticalMax(timeRemaining: Int, currentState: VolcanoState): Int {
        val currentScore = countTotalReleased(currentState)
        val canReleaseNow =
            canReleaseValve(
                currentState,
                currentState.myState.currentValve
            ) || (currentState.elephantState != null && canReleaseValve(
                currentState,
                currentState.elephantState.currentValve
            ))

        val firstReleaseTime = if (canReleaseNow) timeRemaining else timeRemaining - 1
        val optimisticReleaseTimes = (firstReleaseTime downTo 0 step 2)

        val zipped = optimisticReleaseTimes zip releasableValves(currentState).sortedByDescending { it.flowRate }
        return currentScore + zipped.sumOf { it.first * it.second.flowRate }
    }

    private fun releasableValves(move: VolcanoState) =
        (releasableValves - move.releasedValves.map { it.second })

    private fun runSimulation(startingState: VolcanoState, startingTime: Int) =
        exploreRecursively(
            listOf(startingState),
            findNaiveMaximum(startingState, startingTime),
            startingTime
        ) { moves, highScore, timeRemaining ->
            moves.filter { canSurpassMax(timeRemaining, it, highScore) }
        }

    private tailrec fun exploreRecursively(
        moves: List<VolcanoState>,
        highScore: Int,
        timeRemaining: Int,
        moveReducer: (List<VolcanoState>, Int, Int) -> List<VolcanoState>
    ): Int {
        if (timeRemaining == 0 || moves.isEmpty()) {
            return highScore
        }

        val newMoves = takeMoves(timeRemaining, moves)
        val newHighScore = maxOf(highScore, newMoves.maxOf(::countTotalReleased))
        return exploreRecursively(
            moveReducer(newMoves, newHighScore, timeRemaining),
            newHighScore,
            timeRemaining - 1,
            moveReducer
        )
    }

    private fun VolcanoState.toDistinctHash() =
        VolcanoStateHash(setOf(myState, elephantState), releasedValves)

    private fun findNaiveMaximum(initialState: VolcanoState, startingMoves: Int) =
        exploreRecursively(listOf(initialState), 0, startingMoves) { moves, _, _ ->
            moves.sortedByDescending(::countTotalReleased).subList(0, minOf(1000, moves.size))
        }

    private fun takeMoves(currentTime: Int, currentMoves: List<VolcanoState>) =
        currentMoves.flatMap { takeAllPossibleMoves(currentTime, it) }.distinctBy { it.toDistinctHash() }

    private fun releasedAllValves(move: VolcanoState) = move.releasedValves.size == releasableValves.size

    private fun takeAllPossibleMoves(currentTime: Int, move: VolcanoState): List<VolcanoState> {
        if (releasedAllValves(move)) {
            return listOf(move)
        }

        val myMoves = getValidMoves(move, currentTime, move.myState, ::updateMyState)
        return move.elephantState?.let { elephantState ->
            myMoves.flatMap {
                getValidMoves(
                    it,
                    currentTime,
                    elephantState,
                    ::updateElephantState
                )
            }
        } ?: myMoves
    }

    private fun getValidMoves(
        move: VolcanoState,
        currentTime: Int,
        person: PersonState,
        personUpdater: (VolcanoState, PersonState) -> VolcanoState
    ): List<VolcanoState> {
        val (currentValve, prevValves) = person
        val movements = valvesMap
            .getValue(currentValve)
            .filterNot(prevValves::contains)
            .map { newValve -> personUpdater(move, PersonState(newValve, prevValves + newValve)) }

        return if (canReleaseValve(move, currentValve)) {
            val newReleased = move.releasedValves.plus(Pair(currentTime, currentValve))
            val newPerson = PersonState(currentValve, emptySet())
            movements + personUpdater(move, newPerson).copy(releasedValves = newReleased)
        } else {
            movements
        }
    }

    private fun updateMyState(move: VolcanoState, myState: PersonState): VolcanoState =
        move.copy(myState = myState)

    private fun updateElephantState(move: VolcanoState, elephantState: PersonState): VolcanoState =
        move.copy(elephantState = elephantState)

    private fun parseValves(line: String): Pair<Valve, List<String>> {
        val replaced = line.replace(
            "tunnel leads to valve ",
            "tunnels lead to valves "
        )

        val match =
            Regex("Valve ([A-Z]+) has flow rate=(\\d+); tunnels lead to valves ([A-Z ,]+)").find(
                replaced
            )!!


        val (room, flowRate, others) = match.destructured.toList()
        val valve = Valve(room, flowRate.toInt())
        val otherValves = others.split(", ")
        return valve to otherValves
    }

    private fun getRoutedValvesMap(parsed: List<Pair<Valve, List<String>>>): Map<Valve, List<Valve>> {
        val pairs = parsed.map { (valve, others) ->
            val otherValves = others.map { otherRoom -> parsed.first { it.first.room == otherRoom }.first }
            valve to otherValves
        }

        return pairs.toMap()
    }
}