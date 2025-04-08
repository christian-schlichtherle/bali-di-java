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

## Examples

See [Test code](java-sample/src/main/java/bali/java/sample) (TODO)

## How it Differs from Other Tools 

| Feature                    | **Bali DI**                           | **Spring**                       | **Guice**                                  |
|----------------------------|---------------------------------------|----------------------------------|--------------------------------------------|
| Injection time             | Compile-time                          | Runtime                          | Runtime                                    |
| Dependency resolution      | By name and type (including generics) | By type + `@Qualifier` or config | By type + `@Named` or `@BindingAnnotation` |
| Generic support            | Full                                  | Partial (due to type erasure)    | Partial                                    |
| Reflection                 | None                                  | Heavy use                        | Moderate use                               |
| Requires runtime container | No                                    | Yes                              | Yes                                        |
| Code transparency          | High (explicit, generated code)       | Low (reflection and proxies)     | Medium                                     |
