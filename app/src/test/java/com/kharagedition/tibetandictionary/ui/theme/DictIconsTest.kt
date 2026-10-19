package com.kharagedition.tibetandictionary.ui.theme

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class DictIconsTest {

    @Test
    fun `the set has every icon from the design`() {
        assertThat(DictIcons.all.keys).containsExactly(
            "Search", "SearchOff", "Mic", "Speaker", "Heart", "HeartFilled", "Share", "Copy", "Learn", "Person",
            "Back", "Close", "ChevronRight", "ChevronDown", "ChevronLeft", "Check", "Plus", "History",
            "OpenExternal",
        )
    }

    @Test
    fun `icons are 24-unit round-capped strokes at the set width and only HeartFilled is filled`() {
        for ((set, width) in listOf(DictIcons to 1.75f, DictIcons.Active to 2f, DictIcons.EmptyState to 1.5f)) {
            assertThat(set.strokeWidth).isEqualTo(width)
            for ((name, icon) in set.all) {
                assertWithMessage("$name viewport").that(icon.viewportWidth).isEqualTo(24f)
                assertWithMessage("$name viewport").that(icon.viewportHeight).isEqualTo(24f)
                assertWithMessage("$name size").that(icon.defaultWidth.value).isEqualTo(24f)
                val paths = icon.root.paths()
                assertWithMessage("$name paths").that(paths).isNotEmpty()
                for (path in paths) {
                    assertWithMessage("$name @$width has path data").that(path.pathData).isNotEmpty()
                    assertWithMessage("$name @$width stroke").that(path.stroke).isNotNull()
                    assertWithMessage("$name @$width stroke width").that(path.strokeLineWidth).isEqualTo(width)
                    assertWithMessage("$name @$width cap").that(path.strokeLineCap).isEqualTo(StrokeCap.Round)
                    assertWithMessage("$name @$width join").that(path.strokeLineJoin).isEqualTo(StrokeJoin.Round)
                    if (name == "HeartFilled") {
                        assertWithMessage("$name fill").that(path.fill).isNotNull()
                    } else {
                        assertWithMessage("$name fill").that(path.fill).isNull()
                    }
                }
            }
        }
    }

    @Test
    fun `icons are built once per set`() {
        assertThat(DictIcons.Search).isSameInstanceAs(DictIcons.Search)
        assertThat(DictIcons.Active.Search).isNotSameInstanceAs(DictIcons.Search)
    }

    private fun VectorGroup.paths(): List<VectorPath> = flatMap { node: VectorNode ->
        when (node) {
            is VectorPath -> listOf(node)
            is VectorGroup -> node.paths()
        }
    }
}
