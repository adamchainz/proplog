package sentential.ast

import cats.Show
import cats.data.{Kleisli, NonEmptyList}
import cats.instances.either._

import scala.annotation.tailrec

sealed trait Expression {
  def eval: Expression.BoundBoolean
}

sealed trait BinaryExpression extends Expression {
  def left: Expression
  def right: Expression
  def symbol: String

  def leftRight(f: (Boolean, Boolean) => Boolean): Expression.BoundBoolean = for {
    l <- left.eval
    r <- right.eval
  } yield f(l,r)
}

object Expression {
  type Bindings = Map[Char, Boolean]
  type Result[A] = Either[Error, A]
  type BoundBoolean = Kleisli[Result, Bindings, Boolean]

  implicit class ExpressionOps(exp: Expression) {
    def truthTable: NonEmptyList[Bindings] = {
      val names = varNames
      booleanCombinations(names.size).map { values =>
        names.zip(values).toMap
      }
    }

    def varNames: Set[Char] = {
      def go(e: Expression, acc: Set[Char]): Set[Char] = e match {
        case Var(c, _) => acc + c
        case Neg(ee) => go(ee, acc)
        case ee: BinaryExpression => go(ee.left, acc) ++ go(ee.right, acc)
      }
      go(exp, Set.empty[Char])
    }

    private def booleanCombinations(n: Int): NonEmptyList[List[Boolean]] = {
      val trueFalse = List(true, false)
      @tailrec
      def go(i: Int, acc: List[List[Boolean]]): List[List[Boolean]] = {
        if (i == 0)
          acc
        else
          go(i -1, acc.flatMap(xs => trueFalse.map(y => xs :+ y )))
      }
      NonEmptyList.fromListUnsafe(go(n - 1, trueFalse.map(List(_))))
    }
  }

  private def prettyPrint(exp: Expression): String = exp match {
    case Var(c, _) =>
      s"$c"
    case Neg(e) =>
      s"¬${prettyPrint(e)}"
    case e: BinaryExpression =>
      s"(${prettyPrint(e.left)} ${e.symbol} ${prettyPrint(e.right)})"
  }

  private val BracesPattern = "\\((.*?)\\)".r
  private def removeOuterBraces(s: String): String = s match {
    case BracesPattern(e) => e
    case _ => s
  }

  implicit def expShow: Show[Expression] =
    Show.show(prettyPrint _ andThen removeOuterBraces)

  sealed trait Error {
    def msg: String
  }

  final case class BindingError(label: Char) extends Error {
    override def msg = s"Unbound variable: $label"
  }
  final case class ParserError(msg: String) extends Error

  final case class Var(label: Char, eval: BoundBoolean) extends Expression
  object Var {
    def apply(c: Char): Expression =
      Var(c, Kleisli[Result, Bindings, Boolean](m =>
        m.get(c).toRight(BindingError(c))))
  }

  final case class Neg(exp: Expression) extends Expression {
    override def eval = exp.eval.map(!_)
  }

  final case class Conj(left: Expression, right: Expression) extends BinaryExpression {
    override def eval = leftRight(_ && _)
    override val symbol = "∨"
  }

  final case class Disj(left: Expression, right: Expression) extends BinaryExpression {
    override def eval = leftRight(_ || _)
    override val symbol = "∧"
  }

  final case class Impl(left: Expression, right: Expression) extends BinaryExpression {
    override def eval = leftRight { (l, r) => if (l && !r) false else true }
    override def symbol = "⇒"
  }
}

