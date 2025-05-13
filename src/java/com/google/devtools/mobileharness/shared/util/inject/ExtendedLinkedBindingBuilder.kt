package com.google.devtools.mobileharness.shared.util.inject

import com.google.devtools.mobileharness.shared.util.inject.GuiceKt.key
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import kotlin.reflect.KClass

/**
 * A wrapper of [LinkedBindingBuilder] that provides convenience function when using
 * [KAbstractModule].
 */
@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
open class ExtendedLinkedBindingBuilder<T>
internal constructor(private val delegate: LinkedBindingBuilder<T>) :
  LinkedBindingBuilder<T> by delegate {

  /**
   * An extension function of [LinkedBindingBuilder] that allows users to specify a binding target
   * in a Kotlin-idiomatic way.
   *
   * Usage: `bind<...>().toProvider<MyProvider>()`
   *
   * Usage (with annotation): `bind<...>().toProvider<MyProvider>(MyAnnotation::class)`
   *
   * @param P the type for the binding target's [javax.inject.Provider]
   * @return a [ScopedBindingBuilder] for the binding
   * @sample [KAbstractModuleTest.testExtendedLinkedBindingBuilder_toProvider]
   * @sample [KAbstractModuleTest.testExtendedLinkedBindingBuilder_toProviderWithAnnotation]
   */
  @CanIgnoreReturnValue
  inline fun <reified P : javax.inject.Provider<out T>> toProvider(
    annotation: KClass<out Annotation>? = null
  ): ScopedBindingBuilder = toProvider(key<P>(annotation))
}
