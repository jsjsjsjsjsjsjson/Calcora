package dev.libchara.calculatorplus

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.libchara.calculatorplus.engine.EvalMode
import dev.libchara.calculatorplus.engine.GiacEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GiacEngineInstrumentedTest {
    @Test
    fun nativeGiacBackendEvaluatesCoreExpressions() {
        assertTrue(GiacEngine.init())
        assertTrue(GiacEngine.version().contains("Giac 2.0.0 native core"))

        val arithmetic = GiacEngine.evaluate("1+2*3", EvalMode.Auto)
        assertNull(arithmetic.error, arithmetic.error)
        assertEquals("7", arithmetic.symbolic)
        assertTrue(arithmetic.backend.contains("giac 2.0.0 native core"))

        val derivative = GiacEngine.evaluateRawXcas("diff(sin(x),x)")
        assertNull(derivative.error, derivative.error)
        assertTrue(derivative.symbolic.contains("cos"))
    }
}
