package com.kharagedition.tibetandictionary.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import kotlin.math.roundToInt

/**
 * The pixels of the node tagged [tag], drawn in software from [root] (the view from `LocalView`). Needs
 * `@GraphicsMode(NATIVE)`. `captureToImage` doesn't work under Robolectric: it waits for a frame that never comes.
 */
internal fun ComposeContentTestRule.pixelsOf(root: View, tag: String): PixelMap {
    val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    val whole = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
    runOnIdle { root.draw(Canvas(whole)) }
    val node = Bitmap.createBitmap(
        whole,
        bounds.left.roundToInt(),
        bounds.top.roundToInt(),
        bounds.width.roundToInt(),
        bounds.height.roundToInt(),
    )
    return node.asImageBitmap().toPixelMap()
}
