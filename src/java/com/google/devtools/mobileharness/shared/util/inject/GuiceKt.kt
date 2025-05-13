package com.google.devtools.mobileharness.shared.util.inject

import com.google.devtools.mobileharness.shared.util.inject.GuiceKt.key
import com.google.devtools.mobileharness.shared.util.inject.GuiceKt.typeLiteral
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.inject.AbstractModule
import com.google.inject.Binder
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Module
import com.google.inject.Provider
import com.google.inject.Scope
import com.google.inject.TypeLiteral
import com.google.inject.binder.AnnotatedBindingBuilder
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import com.google.inject.multibindings.OptionalBinder
import java.lang.reflect.Method
import kotlin.reflect.KClass

/** Object that houses non-extension functions. */
object GuiceKt {

  /**
   * Returns a new [com.google.inject.TypeLiteral] of [T].
   *
   * Usage: `val myTypeLiteral : TypeLiteral<T> = typeLiteral<String>()`
   *
   * @param T the type argument to be passed to [com.google.inject.TypeLiteral]
   * @sample [GuiceKtTest.testTypeLiteral]
   */
  inline fun <reified T> typeLiteral(): TypeLiteral<T> = object : TypeLiteral<T>() {}

  /**
   * Return a new [com.google.inject.Key] of [T].
   *
   * Usage (no annotation): `val myKey : Key<String> = key<String>()`
   *
   * Usage (with annotation): `val myAnnotatedKey: Key<String> = key<String>(MyAnnotation::class)`
   *
   * @param T the type argument to be passed to [com.google.inject.Key]
   * @param annotation the annotation class to annotate the returned Key. When null (the default),
   *   the [com.google.inject.Key] will not be annotated.
   * @sample [GuiceKtTest.testKey]
   * @sample [GuiceKtTest.testKeyPassingAnnotation]
   */
  inline fun <reified T> key(annotation: KClass<out Annotation>? = null): Key<T> =
    if (annotation == null) object : Key<T>() {} else object : Key<T>(annotation.java) {}

  /**
   * Returns a new [Key] of [T].
   *
   * Usage: `val myKey : Key<String> = key<String>(myAnnotationInstance)`
   *
   * @param T the type argument to be passed to [Key]
   * @param annotation the annotation instance to annotate the returned [Key]
   * @sample [GuiceKtTest.testKeyPassingAnnotationInstance]
   */
  inline fun <reified T> key(annotation: Annotation): Key<T> = object : Key<T>(annotation) {}

  /**
   * Creates an anonymous [com.google.inject.AbstractModule] that can be configured with the lambda
   * given.
   *
   * Usage: `abstractModule { bind<Foo>().to<FooImpl>() }`
   *
   * @sample [GuiceKtTest.testAbstractModuleBindTo]
   */
  fun abstractModule(block: AbstractModule.() -> Unit) =
    object : AbstractModule() {
      override fun configure() {
        block()
      }
    }
}

// Extensions for Key

/**
 * Returns a new [Key] of [T], using the same annotation (if present) as the given key.
 *
 * Usage: `val myKey : Key<String> = myOtherKey.ofType<String>()`
 *
 * @param T the type argument to be passed to [Key]
 * @return a new `Key<T>` with the same annotation (if any) from the source key
 * @sample [GuiceKtTest.testOfType]
 */
inline fun <reified T> Key<*>.ofType(): Key<T> = ofType(typeLiteral<T>())

/**
 * Return a new [Key], whose type is the same but whose annotation is the given annotation.
 *
 * Usage: `val myKey : Key<String> = myKeyOfString.withAnnotation(MyAnnotation::class)`
 *
 * @param annotation the annotation class used to annotate the returned [Key]
 * @sample [GuiceKtTest.testWithAnnotation]
 */
fun <T, A : Annotation> Key<T>.withAnnotation(annotation: KClass<A>): Key<T> =
  withAnnotation(annotation.java)

// Extensions for LinkedBindingBuilder.

/**
 * An extension function of [LinkedBindingBuilder] that returns a [ScopedBindingBuilder].
 *
 * WARNING: Make sure to pass a [T] to this function. If you don't, you will end up binding your key
 * to itself (see [GuiceKtTest.testBindToMissingTypePointsToItself]).
 *
 * Usage (no annotation): `bind(...).to<MyImplementation>()`
 *
 * Usage (with annotation): `bind(...).to<MyImplementation>(MyAnnotation::class)`
 *
 * @param T the type argument to be passed to the binding target's [Key]
 * @param annotation the annotation class used to annotate the binding target's [Key]. When null
 *   (the default), the target's key will have no annotation.
 * @sample [GuiceKtTest.testLinkedBindingBuilderTo]
 * @sample [GuiceKtTest.testLinkedBindingBuilderToWithAnnotation]
 */
@CanIgnoreReturnValue
inline fun <reified T> LinkedBindingBuilder<in T>.to(
  annotation: KClass<out Annotation>? = null
): ScopedBindingBuilder = to(key<T>(annotation))

/**
 * An extension function of [LinkedBindingBuilder] that returns a [ScopedBindingBuilder].
 *
 * Usage (with annotation): `bind(...).to<MyImplementation>(MyAnnotation(value="my value"))`
 *
 * @param T the type argument to be passed to the binding target's [Key]
 * @param annotation the annotation instanced used to annotate the binding target's [Key]
 * @sample [GuiceKtTest.testLinkedBindingBuilderToWithAnnotationInstance]
 */
@CanIgnoreReturnValue
inline fun <reified T> LinkedBindingBuilder<in T>.to(annotation: Annotation): ScopedBindingBuilder =
  to(key<T>(annotation))

// Note: There is no LinkedBindingBuilder<T>.toProvider() extension function because it would
// require having two type parameters:
//   inline fun <reified T, reified S : javax.inject.Provider<T>>
//     LinkedBindingBuilder<T>.toProvider(): ScopedBindingBuilder =
//       this.toProvider(S::class.java)
// That would cause users to repeat the type (ugly):
//   bind<MyType>().toProvider<MyType, MyTypeProvider>()
// Instead, use the inline function ExtendedLinkedBindingBuilder.toProvider or call
//   toProvider(key<MyTypeProvider>())

// Extensions for ScopedBindingBuilder

/**
 * An extension function of [ScopedBindingBuilder] that allows you to specify the scope of a binding
 * without needing to use backticks.
 *
 * Usage: `bind(...).to(...).inScope<Singleton>()`
 *
 * @param A the [com.google.inject.Scope]'s annotation
 * @sample [GuiceKtTest.testInScope]
 */
inline fun <reified A : Annotation> ScopedBindingBuilder.inScope() = this.`in`(A::class.java)

// Extensions for Injector

/**
 * Returns an instance of [T] from the [com.google.inject.Injector].
 *
 * Usage (no annotation): `val s : String = injector.getInstance<String>()`
 *
 * Usage (with annotation): `val s : String = injector.getInstance<String>(MyAnnotation::class)`
 *
 * @param T the type of the [Key] to get
 * @param annotation the annotation class of the [Key] to get. When null (the default), this will
 *   get an instance of [T] whose [Key] is not annotated.
 * @return the requested (possibly-annotated) [T]
 * @sample [GuiceKtTest.testInjectorGetInstance]
 * @sample [GuiceKtTest.testInjectorGetInstanceWithAnnotation]
 */
inline fun <reified T> Injector.getInstance(annotation: KClass<out Annotation>? = null): T =
  getInstance(key<T>(annotation))

/**
 * Returns an instance of [T] from the [Injector].
 *
 * Usage: `val s : String = injector.getInstance<String>(someAnnotation)`
 *
 * @param T the type of the [Key] to get
 * @param annotation the annotation instance of the [Key] to get.
 * @return the requested [T]
 * @sample [GuiceKtTest.testInjectorGetInstanceWithAnnotationInstance]
 */
inline fun <reified T> Injector.getInstance(annotation: Annotation): T =
  getInstance(key<T>(annotation))

/**
 * Returns a [com.google.inject.Provider] of [T] from the [Injector].
 *
 * Usage (no annotation): `val s : Provider<String> = injector.getProvider<String>() Usage (with
 * annotation) `val s : Provider<String> = injector.getProvider<String>(MyAnnotation::class)`
 *
 * @param T the type of the returned [com.google.inject.Provider]'s [Key]
 * @param annotation the annotation class of the returned [com.google.inject.Provider]'s [Key]. When
 *   null (the default), this will return a [com.google.inject.Provider] whose [Key] is not
 *   annotated.
 * @return the requested (possibly-annotated) [com.google.inject.Provider] of [T]
 * @sample [GuiceKtTest.testInjectorGetProvider]
 * @sample [GuiceKtTest.testInjectorGetProviderWithAnnotation]
 */
inline fun <reified T> Injector.getProvider(
  annotation: KClass<out Annotation>? = null
): Provider<T> = getProvider(key<T>(annotation))

/**
 * Returns a [Provider] of [T] from the [Injector].
 *
 * Usage: `val s : Provider<String> = injector.getProvider<String>(someAnnotation)`
 *
 * @param T the type of the returned [Provider]'s [Key]
 * @param annotation the annotation instance of the returned [Provider]'s [Key].
 * @return the requested [Provider] of [T]
 * @sample [GuiceKtTest.testInjectorGetProviderWithAnnotationInstance]
 */
inline fun <reified T> Injector.getProvider(annotation: Annotation): Provider<T> =
  getProvider(key<T>(annotation))

// Extensions for AbstractModule

/**
 * Returns an [AnnotatedBindingBuilder] for the given type.
 *
 * Usage: `bind<MyInterface>().to<...>()`
 *
 * @param T the type to bind
 * @sample [AbstractModuleExtensionsTest.testBind]
 */
inline fun <reified T> AbstractModule.bind(): ExtendedAnnotatedBindingBuilder<T> =
  `access$ExtendedAnnotatedBindingBuilderConstructor`(`access$binder`().bind(typeLiteral<T>()))

/**
 * Returns an [com.google.inject.ExtendedLinkedBindingBuilder] for the given type and annotation.
 *
 * Usage: `bind<MyInterface>(MyAnnotation::class).to<...>()`
 *
 * @param T the bound [Key]'s type
 * @param annotation the bound [Key]'s annotation
 * @sample [AbstractModuleExtensionsTest.testBindPassingAnnotation]
 */
inline fun <reified T> AbstractModule.bind(
  annotation: KClass<out Annotation>
): ExtendedLinkedBindingBuilder<T> =
  `access$ExtendedLinkedBindingBuilderConstructor`(`access$binder`().bind(key<T>(annotation)))

/**
 * Returns an [ExtendedLinkedBindingBuilder] for the given type and annotation instance.
 *
 * Usage: `bind<MyInterface>(MyAnnotation(value = "my value")).to<...>()`
 *
 * @param T the bound [Key]'s type
 * @param annotation the bound [Key]'s annotation
 * @sample [AbstractModuleExtensionsTest.testBindPassingAnnotationInstance]
 */
inline fun <reified T> AbstractModule.bind(
  annotation: Annotation
): ExtendedLinkedBindingBuilder<T> =
  `access$ExtendedLinkedBindingBuilderConstructor`(`access$binder`().bind(key<T>(annotation)))

/**
 * Returns a new [Multibinder] of [T].
 *
 * Usage (no annotation): `setBinder<String>().addBinding().to(...)`
 *
 * Usage (with annotation): `setBinder<String>(MyAnnotation::class).addBinding().to(...)`
 *
 * @param T the type of the [Multibinder]
 * @param annotation the [Multibinder]'s annotation (if non-null)
 * @sample [AbstractModuleExtensionsTest.testSetBinder]
 * @sample [AbstractModuleExtensionsTest.testSetBinderPassingAnnotation]
 */
inline fun <reified T : Any> AbstractModule.setBinder(
  annotation: KClass<out Annotation>? = null
): Multibinder<T> = Multibinder.newSetBinder(`access$binder`(), key<T>(annotation))

/**
 * Returns a new [Multibinder] of [T].
 *
 * Usage: `setBinder<String>(MyAnnotation(foo="bar")).addBinding().to(...)`
 *
 * @param T the type of the [Multibinder]
 * @param annotation the [Multibinder]'s annotation instance
 * @sample [AbstractModuleExtensionsTest.testSetBinderPassingAnnotationInstance]
 */
inline fun <reified T : Any> AbstractModule.setBinder(annotation: Annotation): Multibinder<T> =
  Multibinder.newSetBinder(`access$binder`(), key<T>(annotation))

/**
 * Returns a new [MapBinder] of [K] and [V].
 *
 * Usage (no annotation): `mapBinder<String, MyInterface>().addBinding(...).to(...)`
 *
 * Usage (with annotation): `mapBinder<String,
 * MyInterface>(MyAnnotation::class).addBinding(...).to(...)`
 *
 * @param K the [MapBinder]'s key type
 * @param V The [MapBinder]'s value type
 * @param annotation the [MapBinder]'s annotation (if non-null)
 * @sample [AbstractModuleExtensionsTest.testMapBinder]
 * @sample [AbstractModuleExtensionsTest.testMapBinderPassingAnnotation]
 */
inline fun <reified K : Any, reified V : Any> AbstractModule.mapBinder(
  annotation: KClass<out Annotation>? = null
): MapBinder<K, V> =
  if (annotation == null) {
    MapBinder.newMapBinder(`access$binder`(), typeLiteral<K>(), typeLiteral<V>())
  } else {
    MapBinder.newMapBinder(`access$binder`(), typeLiteral<K>(), typeLiteral<V>(), annotation.java)
  }

/**
 * Returns a new [MapBinder] of [K] and [V].
 *
 * Usage: `mapBinder<String, MyInterface>(MyAnnotation(foo="bar")).addBinding(...).to(...)`
 *
 * @param K the [MapBinder]'s key type
 * @param V The [MapBinder]'s value type
 * @param annotation the [MapBinder]'s annotation instance
 * @sample [AbstractModuleExtensionsTest.testMapBinderPassingAnnotationInstance]
 */
inline fun <reified K : Any, reified V : Any> AbstractModule.mapBinder(
  annotation: Annotation
): MapBinder<K, V> =
  MapBinder.newMapBinder(`access$binder`(), typeLiteral<K>(), typeLiteral<V>(), annotation)

/**
 * Returns a new [OptionalBinder] of [T].
 *
 * Usage (no annotation): `optionalBinder<String>().setBinding().to(...)`
 *
 * Usage (with annotation): `optionalBinder<String>(MyAnnotation::class).setBinding().to(...)`
 *
 * @param T the [OptionalBinder]'s type
 * @sample [AbstractModuleExtensionsTest.testOptionalBinder]
 */
inline fun <reified T> AbstractModule.optionalBinder(
  annotation: KClass<out Annotation>? = null
): OptionalBinder<T> = OptionalBinder.newOptionalBinder(`access$binder`(), key<T>(annotation))

/**
 * Returns a new [OptionalBinder] of [T].
 *
 * Usage: `optionalBinder<String>(MyAnnotation(foo="bar")).setBinding().to(...)`
 *
 * @param T the [OptionalBinder]'s type
 * @param annotation the [OptionalBinder]'s annotation instance
 * @sample [AbstractModuleExtensionsTest.testOptionalBinder]
 */
inline fun <reified T> AbstractModule.optionalBinder(annotation: Annotation): OptionalBinder<T> =
  OptionalBinder.newOptionalBinder(`access$binder`(), key<T>(annotation))

/**
 * Binds a scope to an annotation.
 *
 * Usage: `bindScope<RequestScope>(Scopes.NO_SCOPE`).
 *
 * @param T the annotation type to bind
 * @param scope the [com.google.inject.Scope] to bind the annotation to
 * @sample [AbstractModuleExtensionsTest.testAbstractModuleBindScope]
 */
inline fun <reified T : Annotation> AbstractModule.bindScope(scope: Scope): Unit =
  `access$binder`().bindScope(T::class.java, scope)

/** @see AbstractModule#install(Module) */
fun AbstractModule.install(module: Module) = `access$binder`().install(module)

/**
 * Returns a [Provider] of [T].
 *
 * Usage (no annotation): `getProvider<String>()`
 *
 * Usage (with annotation): `getProvider<String>(MyAnnotation::class)`
 *
 * @param T the [Provider]'s type
 * @param annotation the annotation for the [Provider]'s [Key]
 * @sample [AbstractModuleExtensionsTest.testGetProviderPassingAnnotation]
 */
inline fun <reified T> AbstractModule.getProvider(
  annotation: KClass<out Annotation>? = null
): Provider<T> = `access$binder`().getProvider(key<T>(annotation))

/**
 * Returns a [Provider] of [T].
 *
 * Usage: `getProvider<String>(annotationInstance)`
 *
 * @param T the [Provider]'s type
 * @param annotation the annotation instance for the [Provider]'s [Key]
 * @sample [AbstractModuleExtensionsTest.testGetProviderPassingAnnotationInstance]
 */
inline fun <reified T> AbstractModule.getProvider(annotation: Annotation): Provider<T> =
  `access$binder`().getProvider(key<T>(annotation))

@PublishedApi // needed to access the protected binder() via reflection.
internal fun AbstractModule.`access$binder`(): Binder {
  val method: Method = AbstractModule::class.java.getDeclaredMethod("binder")
  method.isAccessible = true
  return method.invoke(this) as Binder
}

@PublishedApi // needed to access an internal constructor from a protected inline function.
internal fun <T> `access$ExtendedLinkedBindingBuilderConstructor`(
  delegate: LinkedBindingBuilder<T>
): ExtendedLinkedBindingBuilder<T> = ExtendedLinkedBindingBuilder(delegate)

@PublishedApi // needed to access an internal constructor from a protected inline function.
internal fun <T> `access$ExtendedAnnotatedBindingBuilderConstructor`(
  delegate: AnnotatedBindingBuilder<T>
): ExtendedAnnotatedBindingBuilder<T> = ExtendedAnnotatedBindingBuilder(delegate)
