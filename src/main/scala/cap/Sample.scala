package cap

import scala.util.Random

object Sample:
  def handle[A, C](
      program: Cap[C & Random] ?=> A
  )(using cap: Cap[C]): A =
    program(using cap.add(Random))

  def int(using cap: Cap[Random]): Int =
    cap.get[Random].nextInt()

  def double(using cap: Cap[Random]): Double =
    cap.get[Random].nextDouble()
