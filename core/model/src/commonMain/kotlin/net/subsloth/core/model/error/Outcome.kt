package net.subsloth.core.model.error

/**
 * A typed result of a fallible operation.
 *
 * [Outcome] is the project-wide shape for "expected, recoverable"
 * failures in domain and use-case code. Unlike [kotlin.Result] the
 * failure carries a typed [DomainError] value rather than a
 * [Throwable] wrapper, which means:
 *
 * - The failure type is part of the return signature, not hidden in a
 *   `Throwable` hierarchy the caller has to know about.
 * - Patterns like `when (outcome) { is Success -> ...; is Failure -> ... }`
 *   are exhaustive; the compiler catches any new [DomainError] variant.
 * - FP pipelines (`map`, `flatMap`, `recover`, `zipOrAccumulate`) work
 *   without exception machinery.
 *
 * Use [kotlin.Result] at the I/O shell boundary in `:core:network`,
 * `:core:database`, `:core:preferences` where engine exceptions are
 * translated into typed values. Convert `Result<T>` → `Outcome<T>` at
 * the port boundary so consumers in `:core:domain` and `:feature:*`
 * never see a `Throwable`.
 *
 * This is the project-local equivalent of Arrow's `Either<DomainError, T>`,
 * kept dependency-free per the project's "no new third-party deps" rule.
 */
sealed interface Outcome<out T> {
    /**
     * The happy path. The result value is in [value].
     */
    data class Success<out T>(val value: T) : Outcome<T> {
        companion object {
            /**
             * Convenience factory that infers the success type for the
             * call site. Use as `Outcome.Success(42)` or
             * `Outcome.Success(Unit)`.
             */
            fun <T> of(value: T): Success<T> = Success(value)
        }
    }

    /**
     * The failure path. The typed [DomainError] is in [error]. There is
     * no success value to extract.
     */
    data class Failure(val error: DomainError) : Outcome<Nothing> {
        companion object {
            /** Convenience factory. Use as `Outcome.Failure(SyncError.Timeout)`. */
            operator fun invoke(error: DomainError): Failure = Failure(error)
        }
    }
}

// ── Constructor helpers ────────────────────────────────────────────────────

/** Lift a value into a successful [Outcome]. */
fun <T> T.asOutcome(): Outcome.Success<T> = Outcome.Success(this)

/** Lift a [DomainError] into a failed [Outcome]. */
fun DomainError.asFailure(): Outcome.Failure = Outcome.Failure(this)

// ── Functional pipelines ───────────────────────────────────────────────────

/**
 * Catamorphism over [Outcome]. The compiler enforces that the function
 * returns [R] for both branches, so a new [DomainError] variant does
 * not break the call site.
 */
inline fun <T, R> Outcome<T>.fold(onSuccess: (T) -> R, onFailure: (DomainError) -> R): R = when (this) {
    is Outcome.Success -> onSuccess(value)
    is Outcome.Failure -> onFailure(error)
}

/** Returns the success value or `null` if this is a failure. */
fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

/**
 * Returns the success value or computes a fallback from the typed
 * [DomainError]. Use this when a sensible default exists for *any*
 * failure (e.g. `getOrElse { emptyList() }` for a list-returning port).
 */
inline fun <T> Outcome<T>.getOrElse(fallback: (DomainError) -> T): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> fallback(error)
}

/** Transform the success value; failures pass through unchanged. */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/**
 * Bind the success value into a transformation that itself returns an
 * [Outcome]. Use for sequential use-case composition.
 */
inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

/**
 * Recover from a failure by mapping a specific [DomainError] sub-type
 * to a new [Outcome]. Other failures pass through unchanged.
 */
inline fun <reified E : DomainError, T> Outcome<T>.recover(transform: (E) -> Outcome<T>): Outcome<T> = when (this) {
    is Outcome.Success -> this
    is Outcome.Failure -> if (error is E) transform(error) else this
}

/** Returns `true` if this is a [Outcome.Success]. */
val Outcome<*>.isSuccess: Boolean get() = this is Outcome.Success

/** Returns `true` if this is a [Outcome.Failure]. */
val Outcome<*>.isFailure: Boolean get() = this is Outcome.Failure

/**
 * Execute [action] if this is a success. Returns the receiver so calls
 * can be chained. The [action] lambda's return value is discarded.
 */
inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

/**
 * Execute [action] if this is a failure. Returns the receiver so calls
 * can be chained. The [action] lambda's return value is discarded.
 */
inline fun <T> Outcome<T>.onFailure(action: (DomainError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

// ── Result interop ─────────────────────────────────────────────────────────

/**
 * Convert a [kotlin.Result] (which carries a [Throwable] failure) into
 * an [Outcome] by mapping the throwable with [onThrowable]. The default
 * implementation treats any [Throwable] as a decode failure
 * ([DecodeError.SerializationFailed]); the I/O shell should override
 * [onThrowable] with a more specific classifier (e.g.
 * `NetworkErrorClassifier`) at the port boundary.
 *
 * Use this to drop the last `Throwable` from the public type system at
 * the I/O-shell-to-port boundary.
 */
fun <T> Result<T>.toOutcome(
    onThrowable: (Throwable) -> DomainError = { DecodeError.SerializationFailed },
): Outcome<T> = fold(
    onSuccess = { Outcome.Success(it) },
    onFailure = { Outcome.Failure(onThrowable(it)) },
)
