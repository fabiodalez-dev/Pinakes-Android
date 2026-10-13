package com.pinakes.app.staff

import com.pinakes.app.data.model.StaffSeries
import com.pinakes.app.data.staff.SeriesTree
import com.pinakes.app.ui.screens.staff.content.LabelForm
import com.pinakes.app.ui.screens.staff.content.SeriesForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The series tree rows, merge-target exclusion, and the series/label forms. */
class SeriesTreeTest {

    private val tree = listOf(
        StaffSeries(id = 1, name = "Discworld", children = listOf(
            StaffSeries(id = 2, name = "Rincewind", children = listOf(StaffSeries(id = 3, name = "Luggage"))),
            StaffSeries(id = 4, name = "Watch"),
        )),
        StaffSeries(id = 5, name = "Gialli"),
    )

    @Test fun collapsedShowsRootsOnly() {
        val rows = SeriesTree.rows(tree, expanded = emptySet())
        assertEquals(listOf(1, 5), rows.map { it.series.id })
        assertTrue(rows.first().hasChildren)
        assertFalse(rows.first().expanded)
    }

    @Test fun expandingRevealsChildrenWithDepth() {
        val rows = SeriesTree.rows(tree, expanded = setOf(1))
        assertEquals(listOf(1, 2, 4, 5), rows.map { it.series.id })
        assertEquals(listOf(0, 1, 1, 0), rows.map { it.depth })
        assertEquals(listOf(1, 2, 3, 4, 5), SeriesTree.rows(tree, setOf(1, 2)).map { it.series.id })
    }

    @Test fun searchKeepsAncestorsOfDeepMatches() {
        val rows = SeriesTree.rows(tree, expanded = emptySet(), query = "lugg")
        assertEquals(listOf(1, 2, 3), rows.map { it.series.id })
        assertEquals(listOf(5), SeriesTree.rows(tree, emptySet(), "GIALLI").map { it.series.id })
        assertTrue(SeriesTree.rows(tree, emptySet(), "nothing").isEmpty())
    }

    @Test fun flattenAndSubtree() {
        assertEquals(listOf(1, 2, 3, 4, 5), SeriesTree.flatten(tree).map { it.id })
        assertEquals(setOf(2, 3), SeriesTree.subtreeIds(tree, 2))
        assertEquals(setOf(99), SeriesTree.subtreeIds(tree, 99))
    }

    @Test fun seriesFormValidationAndWrite() {
        assertFalse(SeriesForm(name = " ").valid)
        assertFalse(SeriesForm(name = "x".repeat(101)).valid)
        assertFalse(SeriesForm(name = "A", cycleOrder = "70000").valid)
        val write = SeriesForm(name = " Discworld ", cycleOrder = "3", parentName = "", complete = true).toWrite()
        assertEquals("Discworld", write.name)
        assertEquals(3, write.cycleOrder)
        assertEquals("", write.parentName)
        assertEquals(true, write.complete)
    }

    @Test fun labelFormRanges() {
        val ok = LabelForm(width = "70", height = "36,5", padding = "")
        assertTrue(ok.valid)
        assertEquals(36.5, ok.toSettings().height!!, 0.0)
        assertEquals(null, ok.toSettings().padding)
        assertFalse(LabelForm(width = "9", height = "30").valid)
        assertFalse(LabelForm(width = "50", height = "101").valid)
        assertFalse(LabelForm(width = "50", height = "30", padding = "25").valid)
        assertEquals("70", LabelForm.from(ok.toSettings().copy(width = 70.0)).width)
    }
}
