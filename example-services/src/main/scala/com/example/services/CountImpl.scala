package com.example.services

import cats.effect.IO
import cats.effect.Ref
import hello.CountService
import hello.CurrentCount
import hello.DivisionByZero
import hello.MathOp

class CountImpl(count: Ref[IO, Int]) extends CountService[IO] {
  def modify(operator: MathOp, operand: Int): IO[Unit] = operator match {
    case MathOp.DIV if operand == 0 =>
      IO.raiseError(DivisionByZero("Division by 0"))
    case other =>
      count.update(c => CountImpl.enumToOp(other)(c, operand))
  }
  def getCount(): IO[CurrentCount] = count.get.map(CurrentCount.apply)
}

object CountImpl {
  private def enumToOp(mathOp: MathOp): (Int, Int) => Int = {
    mathOp match
      case MathOp.ADD => _ + _
      case MathOp.SUB => _ - _
      case MathOp.MUL => _ * _
      case MathOp.DIV => _ / _
  }

}
