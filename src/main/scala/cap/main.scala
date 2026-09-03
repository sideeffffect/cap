package cap

import zio.Tag

import scala.concurrent.duration.Duration
import scala.quoted.*
import scala.reflect.ClassTag
import scala.util.boundary.*
import scala.util.control.NonFatal
import scala.util.{Random, boundary}

@main def main(): Unit =
  // Declare some `Prints`
  val program1: Cap[Console] ?=> Unit =
    Print.println("Hello from direct-style land!")

  // Composition
  val program2: Cap[Console] ?=> Unit =
    Print.println("Amazing!").prefix(Print.print("> ").red)

  val program3: Cap[Random & Console] ?=> Unit =
    val i = Sample.int
    Print.println(s"sample: $i")
    val i2 = Sample.int
    Print.println(s"sample: $i2")

  val program4: Cap[
    Random & Console & Error[ArithmeticException | IllegalArgumentException]
  ] ?=> Unit =
    val i = Sample.int
    if i == 0
    then
      Print.println(s"Zero, crashing: $i")
      Error.raise(new ArithmeticException())
    else if i > 0
    then Print.println(s"Positive: $i")
    else
      Print.println(s"Negative, crashing: $i")
      Error.raise(new IllegalArgumentException())

  // Make some output
  Cap.unsafeRun(Print.handle(program1))
  Cap.unsafeRun(Print.handle(program2))
  Cap.unsafeRun(Print.handle(Sample.handle(program3)))
  val r: Either[ArithmeticException | IllegalArgumentException, Unit] =
    Cap.unsafeRun(Error.handle(Print.handle(Sample.handle(program4))))
  println(r)

  val r2: Cap[Error[ArithmeticException]] ?=> Either[
    IllegalArgumentException,
    Unit
  ] =
    Cap.unsafeRun(
      Error
        .handleOnly[IllegalArgumentException, ArithmeticException]
        .apply(
          Print.handle(Sample.handle(program4))
        )
    )
  // println(r2)

  // val r3: Cap[Error[ArithmeticException]] ?=> Either[
  //   IllegalArgumentException,
  //   Unit
  // ] =
  //   Cap.unsafeRun(
  //     Print.handle(Sample.handle(program4.mapError[Exception](x => x)))
  //   )
  // println(r3)
