package uk.dnpk.sekund.runtime.platform

import kotlinx.io.files.Path

// Small security helpers that Kotlin's common standard library doesn't have.
//
// `expect` declares a function here; each platform provides the body with `actual` in
// PlatformSecurity.jvm.kt (desktop) and PlatformSecurity.android.kt. (Like a TypeScript interface
// with one implementation per platform, chosen at compile time.)

/** [size] bytes from a cryptographically secure random generator (for PKCE verifiers, OAuth state...). */
expect fun secureRandomBytes(size: Int): ByteArray

/** The SHA-256 hash of [data]. */
expect fun sha256(data: ByteArray): ByteArray

/** Makes [file] readable and writable by the current user only (chmod 600), where the OS supports it. */
expect fun restrictToOwner(file: Path)
