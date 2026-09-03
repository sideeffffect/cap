package cap

import zio.Tag

import scala.reflect.ClassTag
import scala.util.control.NonFatal

opaque type Cap[+C] = zio.ZEnvironment[C]

extension [C](self: Cap[C])
  def get[A >: C: Tag]: A = self.get[A]
  def add[A: Tag](a: A): Cap[C & A] = self.add(a)

object Cap:

  private case class UnexpectedThrowable(cause: Throwable)
      extends RuntimeException(cause)

  def die(exception: RuntimeException): Cap[Any] ?=> Nothing =
    throw exception

  def unsafeRun[A](program: Cap[Any] ?=> A): A =
    program(using zio.ZEnvironment.empty)

  def wrap[A](program: => A): Cap[Any] ?=> A =
    try program
    catch
      case error: RuntimeException if NonFatal(error) => throw error
      case NonFatal(error) => throw UnexpectedThrowable(error)

  def wrapError[A](
      program: => A
  ): Cap[Error[Exception]] ?=> A =
    try program
    catch case error: Exception if NonFatal(error) => Error.raise(error)

  def wrapSomeError[A, E <: Exception: Tag: ClassTag](
      program: => A
  ): Cap[Error[E]] ?=> A =
    try program
    catch case error: E if NonFatal(error) => Error.raise(error)

extension [C, A](program: Cap[C] ?=> A)

  def debug: Cap[C] ?=> A =
    val result = program
    println(result)
    result

  def flatMap[A1, C1](f: A => Cap[C1] ?=> A1): Cap[C & C1] ?=> A1 =
    val result = program
    f(result)

  def forever: Cap[C] ?=> Nothing =
    while (true) {
      val _ = program
    }
    throw new RuntimeException("unreachable")

  def map[A1](f: A => A1): Cap[C] ?=> A1 =
    val result = program
    f(result)

  def resurrect: Cap[C & Error[RuntimeException]] ?=> A =
    try program
    catch
      case error: RuntimeException if NonFatal(error) =>
        Error.raise(error)

extension [A](program: Cap[Any] ?=> A)
  def unsafeRun: A =
    Cap.unsafeRun(program)

extension [C, C1, A](program: Cap[C] ?=> Cap[C1] ?=> A)
  def flatten: Cap[C & C1] ?=> A =
    val result = program
    result
