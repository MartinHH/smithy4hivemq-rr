package com.example.services

import hello.CountService
import hello.CurrentCount
import hello.DivisionByZero
import hello.MathOp
import smithy4s.capability.MonadThrowLike

/**
 * Some basic thread safe wrapper around state.
 * 
 * (Introduced because we do not want to decide on the "effect" type here.)
 */
trait ThreadSafeVar[F[_], A] {
  def get: F[A]
  def update(f: A => A): F[Unit]
}

class CountImpl[F[_]](count: ThreadSafeVar[F, Int])(using
  F: MonadThrowLike[F]
) extends CountService[F] {
  def modify(operator: MathOp, operand: Int): F[Unit] = operator match {
    case MathOp.DIV if operand == 0 => F.raiseError(DivisionByZero(""))
    case other => count.update(c => CountImpl.enumToOp(other)(c, operand))
  }
  def getCount(): F[CurrentCount] = F.map(count.get)(CurrentCount.apply)
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
