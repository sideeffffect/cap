package cap

import zio.Tag

import scala.reflect.ClassTag
import scala.util.boundary
import scala.util.boundary.{Label, break}

final class Error[-E <: Exception](using label: Label[Either[E, Nothing]]):
  def raise(error: E): Nothing =
    break(Left(error))

object Error:

  def raise[E <: Exception: Tag](error: E)(using cap: Cap[Error[E]]): Nothing =
    cap.get[Error[E]].raise(error)

  def handle[A, E <: Exception: Tag, C](
      program: Cap[C & Error[E]] ?=> A
  )(using cap: Cap[C]): Either[E, A] =
    boundary[Either[E, A]]:
      val error = new Error[E]
      Right(program(using cap.add(error)))

  class HandleOnlyPartiallyApplied[
      E1 <: Exception: Tag: ClassTag,
      E2 <: Exception: Tag
  ]:
    def apply[C, A](
        program: Cap[C & Error[E1 | E2]] ?=> A
    ): Cap[C & Error[E2]] ?=> Either[E1, A] =
      val result = Error.handle[A, E1 | E2, C](program)
      result match
        case Right(result)   => Right(result)
        case Left(error: E1) => Left(error)
        case Left(error)     => Error.raise(error.asInstanceOf[E2])

  def handleOnly[E1 <: Exception: Tag: ClassTag, E2 <: Exception: Tag]
      : HandleOnlyPartiallyApplied[E1, E2] =
    new HandleOnlyPartiallyApplied

extension [C, E <: Exception: Tag, A](program: Cap[C] ?=> Either[E, A])
  def absolve: Cap[C & Error[E]] ?=> A =
    val result = program
    result match
      case Right(result) => result
      case Left(error)   => Error.raise(error)

extension [C, E <: Exception: Tag, A](program: Cap[C & Error[E]] ?=> A)

  def catchSome[E1 <: E: ClassTag]: Cap[C & Error[E]] ?=> Either[E1, A] =
    val result = Error.handle(program)
    result match
      case Right(result)   => Right(result)
      case Left(error: E1) => Left(error)
      case Left(error)     => Error.raise(error)

  def debugError: Cap[C & Error[E]] ?=> A =
    val result = Error.handle(program)
    result match
      case Right(result) =>
        println(result)
        result
      case Left(error) =>
        println(s"<FAIL> $error")
        Error.raise(error)

  def eventually: Cap[C] ?=> A =
    while (true) {
      val result = Error.handle(program)
      result match
        case _: Left[?, ?] => ()
        case Right(result) => return result
    }
    throw new Exception("unreachable")

  def handleSome(pf: PartialFunction[E, A]): Cap[C & Error[E]] ?=> A =
    val result = Error.handle(program)
    result match
      case Right(result)                        => result
      case Left(error) if pf.isDefinedAt(error) => pf(error)
      case Left(error)                          => Error.raise(error)

  def mapError[E1 <: Exception: Tag](f: E => E1): Cap[C & Error[E1]] ?=> A =
    val result = Error.handle(program)
    result match
      case Right(result) => result
      case Left(error)   => Error.raise(f(error))

  def reject[E1 >: E <: Exception: Tag](
      pf: PartialFunction[A, E1]
  ): Cap[C & Error[E1]] ?=> A =
    val result = Error.handle(program)
    result match
      case Right(result) if pf.isDefinedAt(result) => Error.raise(pf(result))
      case Right(result)                           => result
      case Left(error)                             => Error.raise(error)

extension [C, E <: Exception: Tag, A <: Exception: Tag](
    program: Cap[C & Error[E]] ?=> A
)
  def flip: Cap[C & Error[A]] ?=> E =
    val result = Error.handle(program)
    result match
      case Left(error)   => error
      case Right(result) => Error.raise(result)

extension [C, A <: Exception: Tag](program: Cap[C & Error[A]] ?=> A)
  def merge: Cap[C] ?=> A =
    val result = Error.handle(program)
    result match
      case Left(error)   => error
      case Right(result) => result

extension [C, E <: RuntimeException: Tag, A](program: Cap[C & Error[E]] ?=> A)
  def orDie: Cap[C] ?=> A =
    val result = Error.handle(program)
    result match
      case Right(result) => result
      case Left(error)   => throw error

  def refineOrDie[E1 <: Exception: Tag](
      pf: PartialFunction[E, E1]
  ): Cap[C & Error[E1]] ?=> A =
    val result = Error.handle(program)
    result match
      case Right(result)                        => result
      case Left(error) if pf.isDefinedAt(error) => Error.raise(pf(error))
      case Left(error)                          => throw error

class HandleOnlyPartiallyApplied[
    C,
    E <: Exception: Tag,
    E1 <: Exception: Tag: ClassTag,
    A
](program: Cap[C & Error[E]] ?=> A):
  def apply[E2 <: Exception: Tag](implicit
      ev: E =:= (E1 | E2)
  ): Cap[C & Error[E2]] ?=> Either[E1, A] =
    val result = Error.handle[A, E, C](program)
    result.left.map(ev) match
      case Right(result)   => Right(result)
      case Left(error: E1) => Left(error)
      case Left(error)     => Error.raise(error.asInstanceOf[E2])

extension [C, E <: Exception: Tag, A](program: Cap[C & Error[E]] ?=> A)
  def handleOnly[E1 <: Exception: Tag: ClassTag]
      : HandleOnlyPartiallyApplied[C, E, E1, A] =
    new HandleOnlyPartiallyApplied(program)

//extension [C, E1 <: Exception: Tag: ClassTag, E2 <: Exception: Tag, A](
//    program: Cap[C & Error[E1 | E2]] ?=> A
//)
//  def handleOnly: Cap[C & Error[E2]] ?=> Either[E1, A] =
//    val result = Error.handle[A, E1 | E2, C](program)
//    result match
//      case Right(result)   => Right(result)
//      case Left(error: E1) => Left(error)
//      case Left(error)     => Error.raise(error.asInstanceOf[E2])
