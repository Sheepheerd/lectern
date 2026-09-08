package com.lectern

import androidx.compose.ui.graphics.Color
import com.lectern.ui.theme.pivotColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PivotColorTest {

    private val base = Color(0xFF808080)

    @Test
    fun `shade of zero leaves base untouched`() {
        assertEquals(base, pivotColor(base, 0f))
    }

    @Test
    fun `positive shade pulls toward white`() {
        val lighter = pivotColor(base, 0.5f)
        assertTrue(lighter.red > base.red)
        assertTrue(lighter.green > base.green)
        assertTrue(lighter.blue > base.blue)
    }

    @Test
    fun `negative shade pulls toward black`() {
        val darker = pivotColor(base, -0.5f)
        assertTrue(darker.red < base.red)
        assertTrue(darker.green < base.green)
        assertTrue(darker.blue < base.blue)
    }

    @Test
    fun `full positive shade is white`() {
        assertEquals(Color.White, pivotColor(base, 1f))
    }

    @Test
    fun `full negative shade is black`() {
        assertEquals(Color.Black, pivotColor(base, -1f))
    }

    @Test
    fun `shade beyond limits is clamped`() {
        assertEquals(Color.White, pivotColor(base, 1.5f))
        assertEquals(Color.Black, pivotColor(base, -1.5f))
    }
}
