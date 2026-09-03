# cap

A small, [`zenzike/effective`](https://github.com/zenzike/effective)-inspired,
**capability-based, direct-style effect system** for Scala 3.

`cap` shows how far you can get with just three ordinary Scala 3 features:

- **context functions** (`Cap[C] ?=> A`) to track which capabilities a program needs,
- **intersection types** (`A & B`) to accumulate those requirements in the type,
- **`scala.util.boundary` / `break`** to implement handlers in direct style, without monads.

There is no `IO`/`ZIO` wrapper type and no `flatMap` chaining in user code: effectful
code reads like plain, sequential Scala. The effects a program performs are tracked in
its type, and are discharged by *handlers* until nothing is left to handle.

> ⚠️ **Status:** experimental / educational. This is a design exploration, not a
> production library. Expect rough edges (see [Limitations](#limitations)).

---

## The idea in 30 seconds

A program's type says what it can do:

```scala
val program: Cap[Console] ?=> Unit =
  Print.println("Hello from direct-style land!")
```

`Cap[Console]` is a *capability* to write to the console. The `?=>` means the program
needs that capability as a **given** (implicit) to run. Requirements accumulate through
plain intersection types:

```scala
val program: Cap[Random & Console] ?=> Unit =
  val i = Sample.int                 // needs Random
  Print.println(s"sample: $i")       // needs Console
```

You discharge requirements by wrapping the program in a **handler**. Each handler
removes one capability from the type:

```scala
// Handle Console and Random, then run.
Cap.unsafeRun(Print.handle(Sample.handle(program)))
```

When the type is `Cap[Any] ?=> A`, there is nothing left to handle and the program can
be `unsafeRun`.

---

## Core concepts

### `Cap[C]` — a bundle of capabilities

```scala
opaque type Cap[+C] = zio.ZEnvironment[C]
```

`Cap[C]` is an opaque wrapper over ZIO's `ZEnvironment` — a typed, heterogeneous map
from types to values. It is used purely as a **capability set**; none of ZIO's runtime
is involved. Two operations are exposed:

```scala
extension [C](self: Cap[C])
  def get[A >: C: Tag]: A          // retrieve the capability of type A
  def add[A: Tag](a: A): Cap[C & A] // extend the set with a new capability
```

`C` grows via `&` as capabilities are added, and shrinks (in the *requirement* of a
program) as handlers supply them.

### Capabilities

A capability is just a value stored in the `Cap` set, keyed by its type. The library
ships three:

| Capability | Backing value      | Operations                          |
| ---------- | ------------------ | ----------------------------------- |
| `Console`  | the `Print` object | `Print.print`, `Print.println`      |
| `Random`   | `scala.util.Random`| `Sample.int`, `Sample.double`       |
| `Error[E]` | a `boundary.Label` | `Error.raise`, and many combinators |

Operations retrieve their capability from the ambient `Cap` and use it:

```scala
object Print:
  def println(msg: Any)(using cap: Cap[Console]): Unit =
    cap.get[Console].println(msg)
```

### Handlers

A handler takes a program that requires some capability, provides it, and returns a
program that no longer requires it:

```scala
object Print:
  def handle[A, C](program: Cap[C & Console] ?=> A)(using cap: Cap[C]): A =
    program(using cap.add(Console))
```

`Sample.handle` works the same way for `Random`. `Error.handle` is the interesting one:
it installs a `boundary` and turns a program that may `raise` an `E` into an
`Either[E, A]`.

### Direct-style errors via `boundary` / `break`

`Error[E]` is a capability that captures a `boundary.Label`. Raising an error is a
non-local `break` back to the enclosing `Error.handle`:

```scala
final class Error[-E <: Exception](using label: Label[Either[E, Nothing]]):
  def raise(error: E): Nothing = break(Left(error))

def handle[A, E <: Exception: Tag, C](
    program: Cap[C & Error[E]] ?=> A
)(using cap: Cap[C]): Either[E, A] =
  boundary[Either[E, A]]:
    val error = new Error[E]
    Right(program(using cap.add(error)))
```

The error type is tracked precisely, including **unions** of error types:

```scala
val program: Cap[Random & Console &
  Error[ArithmeticException | IllegalArgumentException]] ?=> Unit =
  val i = Sample.int
  if i == 0 then Error.raise(new ArithmeticException())
  else if i < 0 then Error.raise(new IllegalArgumentException())
  else Print.println(s"Positive: $i")
```

### Running a program

```scala
object Cap:
  def unsafeRun[A](program: Cap[Any] ?=> A): A =
    program(using zio.ZEnvironment.empty)
```

Only a fully-handled program (`Cap[Any] ?=> A`) can be run. There is also
`program.unsafeRun` as an extension for convenience.

---

## Combinators

Because a program is just a context function, `cap` provides familiar combinators as
extension methods.

### On any program — `Cap.scala`

| Combinator            | Meaning                                                  |
| --------------------- | -------------------------------------------------------- |
| `map(f)`              | transform the result                                     |
| `flatMap(f)`          | sequence with a program that adds more requirements      |
| `flatten`             | flatten a nested `Cap[C] ?=> Cap[C1] ?=> A`              |
| `debug`               | `println` the result, then return it                     |
| `forever`             | run the program in an infinite loop                      |
| `resurrect`           | turn thrown `RuntimeException`s into `Error[RuntimeException]` |
| `Cap.die(e)`          | throw a `RuntimeException` (a defect, not a typed error) |
| `Cap.wrap`            | run a side-effecting block, normalizing fatal throwables |
| `Cap.wrapError`       | catch `Exception`s into `Error[Exception]`               |
| `Cap.wrapSomeError`   | catch a specific `Exception` subtype into `Error[E]`     |

### On error-carrying programs — `Error.scala`

| Combinator                    | Meaning                                                          |
| ----------------------------- | ---------------------------------------------------------------- |
| `Error.handle`                | run, producing `Either[E, A]`                                    |
| `Error.handleOnly[E1, E2]`    | handle only `E1`, leaving `E2` in the requirements               |
| `catchSome[E1]`               | recover a subset of errors into an `Either`                      |
| `handleSome(pf)`              | recover errors matched by a partial function                     |
| `mapError(f)`                 | transform the error type                                         |
| `refineOrDie(pf)`             | keep matching errors typed; throw the rest as defects            |
| `orDie`                       | throw the error as a defect, removing `Error` from the type      |
| `reject(pf)`                  | turn selected *successes* into errors                            |
| `eventually`                  | retry until the program succeeds                                 |
| `flip`                        | swap success and error channels                                  |
| `merge`                       | collapse `A` and `E` when they share a type                      |
| `absolve`                     | turn an `Either[E, A]` result back into an `Error[E]` program    |
| `debugError`                  | `println` success or `<FAIL>` on error                           |

These are deliberately named after their [ZIO](https://zio.dev) counterparts, so the
mental model transfers.

---

## Example

From [`src/main/scala/cap/main.scala`](src/main/scala/cap/main.scala):

```scala
@main def main(): Unit =
  val program1: Cap[Console] ?=> Unit =
    Print.println("Hello from direct-style land!")

  // Composition: print a red "> " prefix before the message
  val program2: Cap[Console] ?=> Unit =
    Print.println("Amazing!").prefix(Print.print("> ").red)

  val program4: Cap[Random & Console &
    Error[ArithmeticException | IllegalArgumentException]] ?=> Unit =
    val i = Sample.int
    if i == 0 then Error.raise(new ArithmeticException())
    else if i > 0 then Print.println(s"Positive: $i")
    else Error.raise(new IllegalArgumentException())

  Cap.unsafeRun(Print.handle(program1))
  Cap.unsafeRun(Print.handle(program2))

  // Handle every capability, ending with the errors:
  val r: Either[ArithmeticException | IllegalArgumentException, Unit] =
    Cap.unsafeRun(Error.handle(Print.handle(Sample.handle(program4))))
  println(r)

  // Or handle only one error type, leaving the other in the type:
  val r2: Cap[Error[ArithmeticException]] ?=> Either[IllegalArgumentException, Unit] =
    Cap.unsafeRun(
      Error
        .handleOnly[IllegalArgumentException, ArithmeticException]
        .apply(Print.handle(Sample.handle(program4)))
    )
```

Note how handlers *nest* and *compose*, and how the result type of `Error.handle`
reflects exactly the errors that were declared.

---

## Getting started

Requires [sbt](https://www.scala-sbt.org/) and a JDK. The project targets Scala
`3.6.4` and depends on `dev.zio:zio` (used only for its `ZEnvironment` and `Tag`).

```bash
sbt run       # run the @main demo in main.scala
sbt compile   # just type-check / compile
sbt console   # experiment in the REPL
```

---

## How it compares

- **vs. `zenzike/effective`** — same core idea (effects as capabilities discharged by
  handlers, in direct style), expressed with Scala 3's context functions and
  `boundary`/`break` instead of the original setting.
- **vs. ZIO / Cats Effect** — no monadic `IO` type and no `for`-comprehensions in user
  code. Effects are tracked structurally in the type (`Cap[Random & Console & Error[E]]`)
  rather than in a single `R`/`E`/`A` parameter triple. `cap` reuses ZIO's
  `ZEnvironment`/`Tag` only as the underlying capability map.
- **vs. Scala 3 capture checking / `gears` / `ox`** — a lightweight, library-only take
  on the same "capabilities in direct style" theme, with no compiler features required
  beyond what ships in Scala 3.

---

## Limitations

- **`unsafeRun` is genuinely unsafe**: it provides an empty capability set, so a program
  whose requirements are not fully discharged will fail at runtime (`ZEnvironment.get`)
  rather than being rejected — the intent is that the *type* forces you to handle
  everything first.
- **No concurrency, resources, or cancellation.** This is a synchronous,
  single-threaded model. `forever` / `eventually` really do loop.
- **Errors must extend `Exception`.** The error channel is built on typed exceptions and
  `boundary`/`break`.
- **Ergonomics.** Some combinators (e.g. `handleOnly`) use partially-applied helper
  classes to work around Scala 3 type-inference limits.

Contributions and experiments welcome.

## License

No license file is currently included; treat as "all rights reserved" until one is
added.
