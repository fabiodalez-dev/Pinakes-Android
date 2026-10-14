package com.pinakes.app.data.staff

import com.pinakes.app.data.model.StaffSeries

/** One visible row of the series tree. */
data class SeriesRow(val series: StaffSeries, val depth: Int, val hasChildren: Boolean, val expanded: Boolean)

/**
 * The series tree as rows: roots first, a node's children right under it when [expanded]
 * contains its id. A non-blank [query] shows every matching node with its ancestors, fully
 * expanded, so a match deep in a universe is never hidden. Pure.
 */
object SeriesTree {

    fun rows(roots: List<StaffSeries>, expanded: Set<Int>, query: String = ""): List<SeriesRow> {
        val q = query.trim()
        val out = ArrayList<SeriesRow>()
        fun matches(node: StaffSeries): Boolean = node.name.contains(q, ignoreCase = true) || node.children.any(::matches)
        fun walk(nodes: List<StaffSeries>, depth: Int) {
            for (node in nodes) {
                if (q.isNotEmpty() && !matches(node)) continue
                val open = q.isNotEmpty() || node.id in expanded
                out += SeriesRow(node, depth, node.children.isNotEmpty(), open && node.children.isNotEmpty())
                if (open) walk(node.children, depth + 1)
            }
        }
        walk(roots, 0)
        return out
    }

    /** Every node, depth-first, for pickers (merge target). */
    fun flatten(roots: List<StaffSeries>): List<StaffSeries> {
        val out = ArrayList<StaffSeries>()
        fun walk(nodes: List<StaffSeries>) { nodes.forEach { out += it; walk(it.children) } }
        walk(roots)
        return out
    }

    /** [id] and all its descendants: a series cannot be merged into itself or a child. */
    fun subtreeIds(roots: List<StaffSeries>, id: Int): Set<Int> {
        val node = flatten(roots).firstOrNull { it.id == id } ?: return setOf(id)
        return flatten(listOf(node)).map { it.id }.toSet()
    }
}
