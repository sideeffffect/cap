package cap

type Console = Console.type

extension [A](print: Cap[Console] ?=> A)

  /** Insert a prefix before `print` */
  def prefix(
      first: Cap[Console] ?=> Unit
  ): Cap[Console] ?=> A =
    first
    print

  /** Use red foreground color when printing */
  def red: Cap[Console] ?=> A =
    Print.print(Console.RED)
    val result = print
    Print.print(Console.RESET)
    result

object Print:

  def print(msg: Any)(using cap: Cap[Console]): Unit =
    cap.get[Console].print(msg)

  def println(msg: Any)(using cap: Cap[Console]): Unit =
    cap.get[Console].println(msg)

  def handle[A, C](
      program: Cap[C & Console] ?=> A
  )(using cap: Cap[C]): A =
    program(using cap.add(Console))
