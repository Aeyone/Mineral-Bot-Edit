package gg.mineral.bot.ai.goal.findpath

import java.util.PriorityQueue
import gg.mineral.bot.ai.goal.findpath.MoveMent.Pos
import gg.mineral.bot.api.world.ClientWorld

class Astar(
   val world : ClientWorld
) {
    class PathNode(
        val pos: Pos
    ) : Comparable<PathNode> {
        var g = 0.0
        var h = 0.0
        var f = 0.0
        var pathSize = 1
        var parent: PathNode? = null

        override fun compareTo(other: PathNode): Int {
            return f.compareTo(other.f)
        }
    }

    private val heap = PriorityQueue<PathNode>()
    private val visitedPos = HashSet<Pos>()
    private val bestG = HashMap<Pos, Double>()
    private val movement = MoveMent(world)

    fun extractNextStep(end: PathNode): List<Pos> {
        val path = ArrayList<Pos>()
        var node : PathNode? = end

        while (node != null) {
            path.add(node.pos)
            node = node.parent
        }
        path.reverse()
        return path
    }

    fun compute(start: Pos, goal: Pos): List<Pos> {
        heap.clear()
        visitedPos.clear()
        bestG.clear()

        val startNode = PathNode(start)
        var bestNode = startNode

        startNode.h = movement.heuristic(start, goal)
        startNode.f = startNode.h

        heap.add(startNode)
        bestG[start] = 0.0

        while (heap.isNotEmpty()) {
            val current = heap.poll()

            val knownBest = bestG[current.pos]
            if (knownBest != null && current.g > knownBest) continue

            if (current.h < bestNode.h) {
                bestNode = current
            }

            if (!visitedPos.add(current.pos)) continue

            for (nPos in movement.neighbors(current.pos)) {
                if (visitedPos.contains(nPos)) continue

                val newG = current.g + movement.movementCost(current.pos, nPos)
                val oldBest = bestG[nPos]

                if (oldBest != null && newG >= oldBest) continue

                val node = PathNode(nPos)
                node.g = newG
                node.h = movement.heuristic(nPos, goal)
                node.f = node.g + 1.5 * node.h
                node.parent = current
                node.pathSize = current.pathSize + 1

                bestG[nPos] = newG
                heap.add(node)
            }
        }

        return extractNextStep(bestNode)
    }
}
