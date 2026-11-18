package uk.dnpk.sekund.protocol.auth

/**
 * The `{placeholder}` templates used in auth definitions, e.g. `"Bearer {access_token}"`.
 *
 * Deliberately tiny: no expressions, no escaping. A placeholder is `{` + letters, digits or `_` + `}`.
 */
object Templates {
    private val placeholder = Regex("\\{([A-Za-z0-9_]+)\\}")

    /** The names used in [template], e.g. `["access_token"]` for `"Bearer {access_token}"`. */
    fun placeholdersIn(template: String): List<String> {
        val names = mutableListOf<String>()
        for (match in placeholder.findAll(template)) {
            names.add(match.groupValues[1])
        }
        return names
    }

    /**
     * Replaces each placeholder with its value from [values].
     *
     * @throws MissingTemplateValueException if a placeholder has no value, so a half-filled URL or
     *   header is never sent.
     */
    fun fill(template: String, values: Map<String, String>): String {
        return placeholder.replace(template) { match ->
            val name = match.groupValues[1]
            values[name] ?: throw MissingTemplateValueException(name, template)
        }
    }
}

/** A template needed a value nobody provided, e.g. `{instance_url}` for an account without one. */
class MissingTemplateValueException(val name: String, template: String) :
    IllegalStateException("No value for {$name} in \"$template\"")
