[![Release Notes](https://img.shields.io/github/release/christian-schlichtherle/bali-di.svg)](https://github.com/christian-schlichtherle/bali-di/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/global.namespace.bali/bali-annotations)](https://search.maven.org/artifact/global.namespace.bali/bali-annotations)
[![Apache License 2.0](https://img.shields.io/github/license/christian-schlichtherle/bali-di.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Git Maven Workflow](https://img.shields.io/github/workflow/status/christian-schlichtherle/bali-di/Java%20CI%20with%20Maven)](https://github.com/christian-schlichtherle/bali-di/actions?query=workflow%3A%22Java+CI+with+Maven%22)

# Bali DI

Bali DI is a Java code generator for dependency injection. As a pure annotation processor, it works with any Java
compiler which is compliant to Java 8 or later.

> Bali is also an [island](https://en.wikipedia.org/wiki/Bali) between Java and Lombok in Indonesia.
> For disambiguation, the name of this project is "Bali DI", not just "Bali", where DI is an acronym for dependency
> injection.
> In code however, the term "DI" is dropped because there is no ambiguity in this context.

# Getting Started

Bali DI provides an annotation processor for the Java compiler to do its magic. If you use Maven, you need to add the
following dependency to your project:

```xml
<dependency>
    <groupId>global.namespace.bali</groupId>
    <artifactId>bali-java</artifactId>
    <version>0.5.0</version> <!-- see https://github.com/christian-schlichtherle/bali-di/releases/latest -->
    <scope>provided</scope>
</dependency>
```

Note that this is a compile-time-only dependency - there is no runtime dependency of your code on Bali DI!

# Sample Code

This project uses a modular build. The module [`bali-samples`](samples) provides lots of sample code showing how to use
the annotations and generated source code.
`bali-samples` is not published on Maven Central however - it is only available as source code. You can browse the
source code [here](samples/src/main/java/bali/sample) or clone this project.

The module `bali-samples` is organized into different packages with each package representing an individual,
self-contained showcase. For example, the package [`bali.sample.greeting`](samples/src/main/java/bali/sample/greeting)
showcases a glorified way to produce a simple "Hello world!" message by using different components with dependency
injection.

Please forgive me for not providing real-world samples, but I believe learning a new tool is simpler if you don't have
to learn about a specific problem domain first.

Enjoy!

# Motivation

Does the world really need yet another tool for an idiom as simple as dependency injection? Well, I've been frustrated
with existing popular solutions like Spring, Guice, PicoContainer, Weld/CDI, MacWire etc in various projects for years,
so I think the answer is "yes, please"!

Most of these tools try to resolve dependencies at runtime, which is already the first mistake:
By deferring the dependency resolution to runtime, you need to compile, unit-test, integration-test, package and start
up your app first. But chances are that no matter what your test coverage is, you may still find out that your app is
not working properly or not even starting up because something is wrong with its dependency graph. Spring is well-known
for being slow to start-up because it needs to scan the entire byte code of your app for its annotations. Of course, you
can avoid that by going into XML configuration hell - oh my!
And then, if something is not working because you forgot to sprinkle your code with a qualifier annotation in order to
discriminate two dependencies of the same type but with different semantics (say, a `String` representing a username and
a password - yeah, don't do that), then you may spend a lot of time debugging and analyzing this problem.

> By the way, good luck with debugging IOC containers: If your code is not called because you forgot to add an
> annotation somewhere then debugging it is pointless because... as I said, it's not called.

For worse, dependency injection at runtime is not even type-safe when it comes to generic classes due to the type
erasure. For example, your component may get a list of strings injected when it actually wanted a list of user objects.

Last but not least, all of these tools (even Macwire, which is for Scala) support dependency injection into
constructors, methods and fields. For Java, this means you either have to write a lot of boiler plate code, i.e. the
constructors plus the fields if you want to use constructor injection - which is the most preferable of the three
options - or your code gets hardwired to your DI tool by sprinkling it with even more annotations, e.g. `@Autowired` if
you're using Spring.

## A New Approach

In contrast to this, Bali DI is completely different:

1. Dependency resolution happens at compile-time by an annotation processor which generates Java source code to wire up
   your components.
2. Dependencies are created and - if desired - cached in modules, which are auto-generated, abstract classes.
3. Dependencies are made accessible to your components by calling abstract, parameterless methods.

There are many features and benefits resulting from this approach:

+ Quick trouble-shooting
  => if a dependency is missing, or doesn't have a compatible type, the compiler will tell you.
+ Completely type-safe, including generics
  => a `Map<User, List<Order>>` never gets assigned to a `Map<String, String>`.
+ Dependencies are identified by name, not just type
  => a `String username()` is different from a `String password()` without the need for any qualifier annotations.
+ All dependency access is inherently lazy
  => dependencies are created and - if desired - cached just-in-time without unsolicited overhead in memory size or
  runtime complexity.
+ Quick application startup
  => no need to scan the byte code of your app to figure the wiring of your components.
+ No reflection
  => it's not just quicker, but also avoids problems with byte code analysis or obfuscation tools.
+ No runtime dependency
  => no need to worry about the diamond dependency problem, large dependencies or dependency management at all.
+ Tailor-made code generation
  => you can inspect, test and debug the generated source code which exactly matches the requirements of your project
  without unsolicited overhead in memory size or runtime complexity.

## Quick Demo

The following simple app uses a generic clock to print the current date and time.
Again, please excuse me for not picking a less contrived example:

```java
import bali.Cache;
import bali.Module;

import java.util.Date;
import java.util.concurrent.Callable;

import static java.lang.System.out;

@Module
public interface GenericClockApp {

    @Cache
    Callable<Date> clock();

    Date call() throws Exception;

    default void run() throws Exception {
        out.printf("It is now %s.\n", clock().call());
    }

    static void main(String... args) throws Exception {
        GenericClockApp$.new$().run();
    }
}
```

This results in the annotation processor to generate the following code for you (edited to take advantage of import 
statements):

```java
import java.util.Date;
import java.util.concurrent.Callable;

public abstract class GenericClockApp$ implements GenericClockApp {

    static GenericClockApp new$() {
        return new GenericClockApp$() {
        };
    }

    private volatile Callable<Date> clock;

    @Override
    public Callable<Date> clock() {
        Callable<Date> clock;
        if (null == (clock = this.clock)) {
            synchronized (this) {
                if (null == (clock = this.clock)) {
                    this.clock = clock = new Callable$1();
                }
            }
        }
        return clock;
    }

    @Override
    public Date call() {
        return new Date();
    }

    private final class Callable$1 implements Callable<Date> {

        @Override
        public Date call() throws Exception {
            return GenericClockApp$.this.call();
        }
    }
}
```

You may recognize that the code is a blend of the following design patterns:

+ Abstract Factory:
  The interface `GenericClockApp` is an abstract factory because clients get access to the clock by calling the
  abstract method `Callable<Date> clock()` without knowing the implementation class.
  The same is true for the method `Date date()`.
+ Factory Method:
  The class `GenericClock$` implements these factory methods to create - and in case of
  `Callable<Date> clock()` also cache - the singleton clock and its current time.
  The class is abstract and provides a static method constructor to control access to instances while at the same time
  still allowing for subclassing - this gets exploited for advanced scenarios like modular apps.
+ Template Method:
  The interface `Callable<T>` uses the template method `T call() throws Exception` to return a completely generic
  instance `T`, whereby it may terminate with an exception instead of a result.
+ Mediator:
  The inner class `Callable$1` holds an implicit reference to its enclosing class `GenericApp$`.
  It uses this reference to obtain the current Date when calling its method `Date call() throws Exception`.

If there were more dependencies, these patterns would be repeatedly applied to the abstract methods of these types.

## Advanced Features

The [sample code](samples) also showcases the following advanced features: 

+ Abstract factory methods in `@Module` types to use their parameters as dependencies.
+ Caching all return values by applying `@Cache` to types instead of methods.
+ Thread-local caching by applying `@Cache(THREAD_LOCAL)` to types or methods.
+ Taking advantage of interface segregation by applying `@Make(MyImplementation.class)` to methods returning
  `MyInterface`, where `MyImplementation` is a subtype of `MyInterface`. 
+ Specifying the name of accessed methods, fields or parameters by applying `@Lookup("myname")` to accessor methods.
+ Re-using modules by inheritance or composition for multi-module applications.
