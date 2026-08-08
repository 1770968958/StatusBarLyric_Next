package statusbar.lyric.reflection

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** Cached reflection helpers shared by runtime-specific SystemUI integrations. */
object ReflectionUtils {
    private const val NULL_REFERENCE_SCORE = 100
    private const val REFERENCE_ASSIGNABLE_BASE_SCORE = 10
    private const val PRIMITIVE_WIDENING_BASE_SCORE = 2

    private val countMethodCache = ConcurrentHashMap<CountMethodKey, Optional<Method>>()
    private val nameMethodCache = ConcurrentHashMap<NameMethodKey, Optional<Method>>()
    private val compatibleMethodCache = ConcurrentHashMap<CompatibleMethodKey, Optional<Method>>()
    private val fieldCache = ConcurrentHashMap<FieldKey, Optional<Field>>()

    fun findMethod(clazz: Class<*>, name: String, parameterCount: Int): Method? {
        val key = CountMethodKey(clazz, name, parameterCount)
        return countMethodCache.computeIfAbsent(key) {
            Optional.ofNullable(scanMethods(clazz) { method ->
                method.name == name && method.parameterCount == parameterCount && !method.isBridge
            })
        }.orElse(null)
    }

    fun findMethodByName(clazz: Class<*>, name: String): Method? {
        val key = NameMethodKey(clazz, name)
        return nameMethodCache.computeIfAbsent(key) {
            Optional.ofNullable(scanMethods(clazz) { method ->
                method.name == name && !method.isBridge
            })
        }.orElse(null)
    }


    fun findField(clazz: Class<*>, name: String): Field? {
        val key = FieldKey(clazz, name)
        return fieldCache.computeIfAbsent(key) {
            Optional.ofNullable(scanField(clazz, name))
        }.orElse(null)
    }

    fun getFieldValue(instance: Any, name: String): Any? {
        val field = findField(instance.javaClass, name) ?: return null
        return runCatching { field.get(instance) }.getOrNull()
    }

    fun getIntFieldValue(instance: Any, name: String): Int? {
        val field = findField(instance.javaClass, name) ?: return null
        return runCatching { field.getInt(instance) }.getOrNull()
    }

    fun hasField(clazz: Class<*>, name: String): Boolean = findField(clazz, name) != null

    fun hasMethod(clazz: Class<*>, name: String): Boolean = findMethodByName(clazz, name) != null

    fun findCompatibleMethod(clazz: Class<*>, name: String, args: Array<out Any?>): Method? {
        val key = CompatibleMethodKey(clazz, name, args.map { it?.javaClass })
        return compatibleMethodCache.computeIfAbsent(key) {
            Optional.ofNullable(findBestCompatibleMethod(clazz, name, args))
        }.orElse(null)
    }

    fun callNoArg(instance: Any, name: String): Any? {
        val method = findMethod(instance.javaClass, name, 0) ?: return null
        return runCatching { method.invoke(instance) }.getOrNull()
    }

    fun callWithArgs(instance: Any, name: String, vararg args: Any?): Boolean {
        val method = findCompatibleMethod(instance.javaClass, name, args) ?: return false
        method.invoke(instance, *args)
        return true
    }

    private fun scanField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            val searchClass = current
            val field = runCatching { searchClass.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                runCatching { field.isAccessible = true }
                return field
            }
            current = current.superclass
        }
        return null
    }

    private fun scanMethods(clazz: Class<*>, predicate: (Method) -> Boolean): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = current.declaredMethods.firstOrNull(predicate)
            if (method != null) {
                makeAccessible(method)
                return method
            }
            current = current.superclass
        }
        return null
    }

    private fun findBestCompatibleMethod(
        clazz: Class<*>,
        name: String,
        args: Array<out Any?>
    ): Method? {
        var current: Class<*>? = clazz
        var declarationDepth = 0
        var best: ScoredMethod? = null

        while (current != null) {
            current.declaredMethods.forEach { method ->
                if (method.name != name || method.isBridge || method.parameterCount != args.size) {
                    return@forEach
                }

                var compatibilityScore = 0
                for (index in args.indices) {
                    val score = compatibilityScore(method.parameterTypes[index], args[index])
                        ?: return@forEach
                    compatibilityScore += score
                }

                val candidate = ScoredMethod(method, compatibilityScore, declarationDepth)
                if (best == null || candidate < best!!) {
                    best = candidate
                }
            }
            current = current.superclass
            declarationDepth += 1
        }

        return best?.method?.also(::makeAccessible)
    }

    private fun compatibilityScore(parameterType: Class<*>, argument: Any?): Int? {
        if (argument == null) {
            return if (parameterType.isPrimitive) null else NULL_REFERENCE_SCORE
        }

        val argumentType = argument.javaClass
        if (!parameterType.isPrimitive) {
            if (parameterType == argumentType) return 0
            if (!parameterType.isAssignableFrom(argumentType)) return null
            return REFERENCE_ASSIGNABLE_BASE_SCORE + inheritanceDistance(argumentType, parameterType)
        }

        val sourcePrimitive = wrapperToPrimitive(argumentType) ?: return null
        if (sourcePrimitive == parameterType) return 0
        val wideningDistance = primitiveWideningDistance(sourcePrimitive, parameterType) ?: return null
        return PRIMITIVE_WIDENING_BASE_SCORE + wideningDistance
    }

    private fun inheritanceDistance(source: Class<*>, target: Class<*>): Int {
        if (source == target) return 0
        if (target.isInterface) {
            return if (source.interfaces.any { it == target || target.isAssignableFrom(it) }) 1 else 2
        }

        var current: Class<*>? = source.superclass
        var distance = 1
        while (current != null) {
            if (current == target) return distance
            current = current.superclass
            distance += 1
        }
        return distance
    }

    private fun wrapperToPrimitive(clazz: Class<*>): Class<*>? = when (clazz) {
        java.lang.Boolean::class.java -> java.lang.Boolean.TYPE
        java.lang.Byte::class.java -> java.lang.Byte.TYPE
        java.lang.Character::class.java -> java.lang.Character.TYPE
        java.lang.Short::class.java -> java.lang.Short.TYPE
        java.lang.Integer::class.java -> java.lang.Integer.TYPE
        java.lang.Long::class.java -> java.lang.Long.TYPE
        java.lang.Float::class.java -> java.lang.Float.TYPE
        java.lang.Double::class.java -> java.lang.Double.TYPE
        else -> null
    }

    private fun primitiveWideningDistance(source: Class<*>, target: Class<*>): Int? {
        val wideningOrder = when (source) {
            java.lang.Byte.TYPE -> listOf(
                java.lang.Short.TYPE,
                java.lang.Integer.TYPE,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                java.lang.Double.TYPE
            )
            java.lang.Short.TYPE -> listOf(
                java.lang.Integer.TYPE,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                java.lang.Double.TYPE
            )
            java.lang.Character.TYPE -> listOf(
                java.lang.Integer.TYPE,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                java.lang.Double.TYPE
            )
            java.lang.Integer.TYPE -> listOf(
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                java.lang.Double.TYPE
            )
            java.lang.Long.TYPE -> listOf(
                java.lang.Float.TYPE,
                java.lang.Double.TYPE
            )
            java.lang.Float.TYPE -> listOf(java.lang.Double.TYPE)
            else -> emptyList()
        }
        val index = wideningOrder.indexOf(target)
        return if (index >= 0) index + 1 else null
    }

    private fun makeAccessible(method: Method) {
        runCatching { method.isAccessible = true }
    }

    private data class ScoredMethod(
        val method: Method,
        val compatibilityScore: Int,
        val declarationDepth: Int
    ) : Comparable<ScoredMethod> {
        override fun compareTo(other: ScoredMethod): Int {
            val compatibility = compatibilityScore.compareTo(other.compatibilityScore)
            if (compatibility != 0) return compatibility
            return declarationDepth.compareTo(other.declarationDepth)
        }
    }

    private data class CountMethodKey(
        val clazz: Class<*>,
        val name: String,
        val parameterCount: Int
    )

    private data class NameMethodKey(
        val clazz: Class<*>,
        val name: String
    )

    private data class CompatibleMethodKey(
        val clazz: Class<*>,
        val name: String,
        val argumentTypes: List<Class<*>?>
    )

    private data class FieldKey(
        val clazz: Class<*>,
        val name: String
    )
}
