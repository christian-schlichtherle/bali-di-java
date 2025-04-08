[![Release Notes](https://img.shields.io/github/release/christian-schlichtherle/bali-di-java.svg)](https://github.com/christian-schlichtherle/bali-di-java/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/global.namespace.bali/bali-java)](https://search.maven.org/artifact/global.namespace.bali/bali-java)
[![Apache License 2.0](https://img.shields.io/github/license/christian-schlichtherle/bali-di-java.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Test Workflow](https://github.com/christian-schlichtherle/bali-di-java/workflows/test/badge.svg)](https://github.com/christian-schlichtherle/bali-di-java/actions?query=workflow%3Atest)

# Bali DI for Java

**Bali DI for Java** is a compile-time tool for dependency injection (DI),
implemented as a standard Java annotation processor.  
Unlike traditional DI frameworks that rely on reflection, classpath scanning, or runtime containers,
Bali DI generates type-safe code during compilation — requiring nothing more than a JDK for Java 8 or later.  
Each dependency is resolved by **both its name and type**, with **full support for generics**,
eliminating the need for qualifier annotations.  
At runtime, the dependency graph is built **lazily and just in time**, with **no runtime library and no hidden magic**
— no reflection, no classpath scanning.  
This makes Bali DI an excellent choice when you need **type safety**, **fast startup times**,
and **developer productivity** — all without the baggage.

The full documentation can be found at https://bali-di.namespace.global.

## Key Features

- **Compile-time injection** — Each component’s dependencies are wired at compile time using Java's annotation processing tool. Missing dependencies or type mismatches result in compiler errors.
- **Name and type-based resolution** — Dependencies are uniquely identified by both their name and full type (including generics), eliminating the need for qualifier annotations.
- **Full support for generics** — Generic types and methods are fully preserved and resolved, even across injection boundaries.
- **No runtime dependencies** — Bali DI produces plain Java source code that does not require a runtime library.
- **No runtime magic** — There is no runtime container, no reflection, and no classpath scanning.
- **Lazily constructed dependency graph** — Dependencies are instantiated only when needed, minimizing memory usage and improving startup time.
- **Minimalistic by design** — You write interfaces and selective factory methods; Bali DI wires them together with zero magic.
- **Explicit, readable code** — Generated code is simple, transparent, and easy to debug, making your application's behavior easy to understand and trust.
- **Straightforward testing** — You can patch the lazily constructed dependency graph with mocks ahead of time by calling generated setter methods — simple. 

## How it Works

Bali DI for Java implements a standard annotation processor that generates source code at compile time,
forming a lightweight dependency injection container by combining the mediator and factory patterns:

- The **factory pattern** is used to create instances of components.
  You write factory methods (or use default constructors), and Bali DI wires them together automatically.
- The **mediator pattern** is applied to manage dependencies between components.
  The generated container, called __module__, serves as a central point that coordinates the construction and injection
  of dependencies.

All dependencies are resolved by their **name and type**, with full support for **generic types** and
**generic methods**, so no additional annotations (like `@Named` or `@Qualifier`) are necessary. 

At runtime, dependencies are constructed **lazily and just in time**.
If a factory method has already been called, its result is cached so subsequent injections receive the same instance
— unless you explicitly request a different lifecycle via an annotation.

The generated code is plain, type-safe Java.
There’s no reflection, no runtime framework, and no hidden behavior — what you see is what gets compiled and executed.

## How it Differs from Other Tools

| Feature                    | **Bali DI**                           | **Spring**                       | **Guice**                                  |
|----------------------------|---------------------------------------|----------------------------------|--------------------------------------------|
| Injection time             | Compile-time                          | Runtime                          | Runtime                                    |
| Dependency resolution      | By name and type (including generics) | By type + `@Qualifier` or config | By type + `@Named` or `@BindingAnnotation` |
| Generic support            | Full                                  | Partial (due to type erasure)    | Partial                                    |
| Reflection                 | None                                  | Heavy use                        | Moderate use                               |
| Requires runtime container | No                                    | Yes                              | Yes                                        |
| Code transparency          | High (explicit, generated code)       | Low (reflection and proxies)     | Medium                                     |

### Getting Started

To use Bali DI for Java in a Maven project, add the following snippet to your `pom.xml`:

```xml
<dependency>
  <groupId>global.namespace.bali</groupId>
  <artifactId>bali-java</artifactId>
  <version>0.12.0</version> <!-- check for latest version at https://search.maven.org/artifact/global.namespace.bali/bali-java -->
  <scope>provided</scope> <!-- compile-time only! -->
</dependency>
```

Note that the scope of this dependency is `provided`.
This will add the JAR with the annotation processor to the compile-time class path only.
At runtime, no dependency is required.

## Examples

### A Minimal Clock

Let's start with an absolute minimal example, a clock:

```java
package bali.java.sample.minimalclock;

import bali.Module;

import java.util.Date;

@Module
public interface MinimalClockApp {

    Date get();

    default void run() {
        System.out.printf("It is now %s.\n", get());
    }

    static void main(String... args) {
        MinimalClockApp$.new$().run();
    }
}
```

The `@Module` annotation marks `MinimalClockApp` as a so-called __module interface__.
When processing this annotation, the annotation processor generates two additional source code files:

1. `bali.java.sample.minimalclock.MinimalClockApp$` is the so-called __companion interface__ for the module interface
   — note the appended `$` character.
   Its responsibility is to create components and wire them with their dependencies.
   Creating the components implements the factory pattern.
   Wiring them with their dependencies implements the mediator pattern.
   This interface is safe to extend — for example in other module interfaces.
2. `bali.java.sample.minimalclock.MinimalClockApp$$` is the so-called __companion class__ for the module interface
   — note the appended `$$` characters.
   It implements the companion interface and caches the components it creates, as needed.
   Your code should never access this class directly.

To bootstrap the generated code, each companion interface provides a static method named `new$()` unless the module
declares any abstract methods annotated with `@Lookup`.
In the test code of this project, a module interface with a static `main(...)` method is conventionally called an
__app module__.
You don't need to follow this convention in your code.

### A Generic Clock

In the previous example, the only component provided by the module is a date,
declared by the abstract method signature `Date get()`.
`java.util.Date` is a non-abstract class with a default constructor and no dependencies.
That's not a very interesting use case for a dependency injection tool,
so let's level up the game and introduce a `Supplier<Date>` as a clock:

```java
package bali.java.sample.genericclock;

import bali.Cache;
import bali.Module;

import java.util.Date;
import java.util.function.Supplier;

@Module
public interface GenericClockApp {

    @Cache
    Supplier<Date> clock();

    Date get();

    default void run() {
        System.out.printf("It is now %s.\n", clock().get());
    }

    static void main(String... args) {
        GenericClockApp$.new$().run();
    }
}
```

Finally, let's look at the generated code.
We start with the companion interface:

```java
package bali.java.sample.genericclock;

@bali.Generated( // 1.
    processor = "bali.java.AnnotationProcessor",
    round = 1,
    timestamp = "2025-04-05T22:04:32.083+02:00",
    version = "0.12.0"
)
public interface GenericClockApp$ extends GenericClockApp {

    static GenericClockApp new$() { // 2.
        return new GenericClockApp$$();
    }

    @bali.Cache(bali.CachingStrategy.THREAD_SAFE)
    @Override
    default java.util.function.Supplier<java.util.Date> clock() { // 3.
        final class Supplier$ implements java.util.function.Supplier<java.util.Date> {

            @Override
            public java.util.Date get() {
                return GenericClockApp$.this.get();
            }
        }
        return new Supplier$();
    }

    @Override
    default java.util.Date get() { // 4.
        return new java.util.Date();
    }
}
```

1. The `@Generated` annotation is for documentation and debugging purposes only.
2. The `new$()` method returns a new instance of the companion class for bootstrapping.
3. The `clock()` method implements the `Supplier` interface by implementing the `get()` method with a closure which
   simply forwards the call to the method with the same signature in the companion interface.
   This powerful combination of the factory and mediator patterns is the essence of Bali DI.
   Note that the `Supplier` interface is generic and the annotation processor chooses `Date` as its type parameter.
4. The `get()` method returns a new instance of the `Date` class.
   This is because the class is non-abstract and has a default constructor (public, no parameters):
   + If the class is abstract or an interface, the annotation processor tries to implement it the same way as it's done
     for the `Supplier` interface.
   + If the constructor is non-public or has parameters, you need to manually implement the `get()` method in the module
     interface.

Finally, let's have a look at the companion class:

```java
package bali.java.sample.genericclock;

@bali.Generated( // 1.
    processor = "bali.java.AnnotationProcessor",
    round = 1,
    timestamp = "2025-04-05T22:04:32.084+02:00",
    version = "0.12.0"
)
public final class GenericClockApp$$ implements GenericClockApp$ {

    private volatile java.util.function.Supplier<java.util.Date> clock; // 2.

    @Override
    public java.util.function.Supplier<java.util.Date> clock() { // 3.
        java.util.function.Supplier<java.util.Date> value;
        if (null == (value = this.clock)) {
            synchronized (this) {
                if (null == (value = this.clock)) {
                    this.clock = value = GenericClockApp$.super.clock();
                }
            }
        }
        return value;
    }

    public void clock(java.util.function.Supplier<java.util.Date> value) { // 4.
        synchronized(this) {
            this.clock = value;
        }
    }
}
```

1. There's another `@Generated` annotation for documentation and debugging purposes.
2. As requested by the `@Cache` annotation in the module interface, this field is used to store the cached clock.
3. The `Supplier<Date> clock()` method acts as the getter for the cached clock.
   For separation of concerns it delegates to its companion (super) interface for the actual instantiation of the clock.
4. The `void clock(Date)` method acts as the setter for the cached clock.
   To make it accessible from your application code,
   declare an abstract method with the same signature in the module interface.
   Once declared, your test code can call this method to inject a mock implementation at any time.

### More Examples

Bali DI has way more interesting features to show, e.g. support for generic methods, lookup methods, module inheritance
and module composition etc.
Unfortunately, this page is not the right place to show them all.
Therefore, please refer to the [test code folder](java-sample/src/main/java/bali/java/sample)
and please also check the [documentation website](https://bali-di.namespace.global).
