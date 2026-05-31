package subsloth.testing.assertions

import java.util.Optional
import java.util.regex.Pattern
import kotlin.math.absoluteValue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

fun <T> assertThat(actual: T): Subject<T> = Subject(actual)

fun assertThat(actual: Boolean): BooleanSubject = BooleanSubject(actual)

fun assertThat(actual: Boolean?): BooleanSubject = BooleanSubject(actual)

fun assertThat(actual: String?): StringSubject = StringSubject(actual)

fun <T> assertThat(actual: Iterable<T>): IterableSubject<T> = IterableSubject(actual.toList())

fun <T> assertThat(actual: Optional<T>): OptionalSubject<T> = OptionalSubject(actual)

fun <T : Comparable<T>> assertThat(actual: T): ComparableSubject<T> = ComparableSubject(actual)

fun <T : Comparable<T>> assertThat(actual: T?): NullableComparableSubject<T> = NullableComparableSubject(actual)

fun assertThat(actual: Float): FloatSubject = FloatSubject(actual)

fun assertThat(actual: Double): DoubleSubject = DoubleSubject(actual)

fun assertThat(actual: Throwable): ThrowableSubject = ThrowableSubject(actual)

open class Subject<T>(
    protected val actual: T,
) {
    fun isEqualTo(expected: T) {
        assertEquals(expected, actual)
    }

    fun isNotEqualTo(expected: T) {
        assertNotEquals(expected, actual)
    }

    fun isNull() {
        assertTrue(actual == null)
    }

    fun isNotNull(): T = assertNotNull(actual)

    fun isEmpty() {
        assertEquals(0, sizeOf(actual))
    }

    fun isNotEmpty() {
        assertTrue(sizeOf(actual) > 0)
    }

    fun hasSize(size: Int) {
        assertEquals(size, sizeOf(actual))
    }

    fun isInstanceOf(klass: Class<*>) {
        assertTrue(klass.isInstance(actual))
    }

    private fun sizeOf(value: Any?): Int =
        when (value) {
            null -> fail("Size assertions require a non-null value")
            is Collection<*> -> value.size
            is Map<*, *> -> value.size
            is CharSequence -> value.length
            is Array<*> -> value.size
            is Iterable<*> -> value.toList().size
            is Optional<*> -> if (value.isPresent) 1 else 0
            else -> fail("Size assertions are not supported for ${value::class.qualifiedName}")
        }
}

class BooleanSubject(
    actual: Boolean?,
) : Subject<Boolean?>(actual) {
    fun isTrue() {
        assertTrue(actual == true)
    }

    fun isFalse() {
        assertTrue(actual == false)
    }
}

open class ComparableSubject<T : Comparable<T>>(
    actual: T,
) : Subject<T>(actual) {
    fun isGreaterThan(expected: T) {
        assertTrue(actual > expected)
    }

    fun isLessThan(expected: T) {
        assertTrue(actual < expected)
    }

    fun isAtMost(expected: T) {
        assertTrue(actual <= expected)
    }

    fun isAtLeast(expected: T) {
        assertTrue(actual >= expected)
    }
}

class NullableComparableSubject<T : Comparable<T>>(
    private val actual: T?,
) {
    fun isEqualTo(expected: T?) = assertEquals(expected, actual)

    fun isNotEqualTo(expected: T?) = assertNotEquals(expected, actual)

    fun isNull() {
        assertTrue(actual == null)
    }

    fun isNotNull(): T = assertNotNull(actual)

    fun isGreaterThan(expected: T) {
        assertTrue(assertNotNull(actual) > expected)
    }

    fun isLessThan(expected: T) {
        assertTrue(assertNotNull(actual) < expected)
    }

    fun isAtMost(expected: T) {
        assertTrue(assertNotNull(actual) <= expected)
    }

    fun isAtLeast(expected: T) {
        assertTrue(assertNotNull(actual) >= expected)
    }
}

class FloatSubject(
    actual: Float,
) : ComparableSubject<Float>(actual) {
    fun isWithin(delta: Float): FloatToleranceSubject = FloatToleranceSubject(actual, delta)
}

class DoubleSubject(
    actual: Double,
) : ComparableSubject<Double>(actual) {
    fun isWithin(delta: Double): DoubleToleranceSubject = DoubleToleranceSubject(actual, delta)
}

class FloatToleranceSubject(
    private val actual: Float,
    private val delta: Float,
) {
    fun of(expected: Float) {
        assertTrue((actual - expected).absoluteValue <= delta)
    }
}

class DoubleToleranceSubject(
    private val actual: Double,
    private val delta: Double,
) {
    fun of(expected: Double) {
        assertTrue((actual - expected).absoluteValue <= delta)
    }
}

@Suppress("TooManyFunctions")
open class StringSubject(
    private val actual: String?,
) {
    fun isEqualTo(expected: String?) {
        assertEquals(expected, actual)
    }

    fun isNotEqualTo(expected: String?) {
        assertNotEquals(expected, actual)
    }

    fun isNull() {
        assertTrue(actual == null)
    }

    fun isNotNull(): String = assertNotNull(actual)

    fun isEmpty() {
        assertTrue(stringValue().isEmpty())
    }

    fun isNotEmpty() {
        assertTrue(stringValue().isNotEmpty())
    }

    fun hasSize(size: Int) {
        assertEquals(size, stringValue().length)
    }

    fun contains(expected: String) {
        assertTrue(stringValue().contains(expected))
    }

    fun doesNotContain(expected: String) {
        assertFalse(stringValue().contains(expected))
    }

    fun startsWith(prefix: String) {
        assertTrue(stringValue().startsWith(prefix))
    }

    fun endsWith(suffix: String) {
        assertTrue(stringValue().endsWith(suffix))
    }

    fun matches(pattern: Pattern) {
        assertTrue(pattern.matcher(stringValue()).matches())
    }

    private fun stringValue(): String = assertNotNull(actual)
}

class IterableSubject<T>(
    actual: List<T>,
) : Subject<List<T>>(actual) {
    fun contains(expected: T) {
        assertTrue(actual.contains(expected))
    }

    fun doesNotContain(expected: T) {
        assertFalse(actual.contains(expected))
    }

    fun containsExactly(vararg expected: T): ContainsExactlySubject<T> {
        val expectedList = expected.toList()
        assertEquals(expectedList, actual)
        return ContainsExactlySubject(actual, expectedList)
    }
}

class ContainsExactlySubject<T>(
    private val actual: List<T>,
    private val expected: List<T>,
) {
    fun inOrder() {
        assertEquals(expected, actual)
    }

    fun inAnyOrder() {
        assertEquals(expected.groupingBy { it }.eachCount(), actual.groupingBy { it }.eachCount())
    }
}

class OptionalSubject<T>(
    private val actual: Optional<T>,
) {
    fun isPresent() {
        assertTrue(actual.isPresent)
    }

    fun isEmpty() {
        assertTrue(actual.isEmpty)
    }

    fun hasValue(expected: T) {
        assertTrue(actual.isPresent)
        assertEquals(expected, actual.get())
    }
}

class ThrowableSubject(
    private val actual: Throwable,
) {
    fun hasMessageThat(): StringSubject = StringSubject(assertNotNull(actual.message))
}
