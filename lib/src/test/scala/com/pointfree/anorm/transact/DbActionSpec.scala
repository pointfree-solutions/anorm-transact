package acta.db7


import java.sql.Connection

import cats.Eq
import cats.laws.discipline.{ApplicativeTests, FunctorTests, MonadTests}
import com.pointfree.anorm.transact.DbAction
import com.pointfree.anorm.transact.implicits._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.typelevel.discipline.scalatest.Discipline

class DbActionSpec extends FunSuite with Discipline with GeneratorDrivenPropertyChecks{
  implicit val transactionArb : Arbitrary[DbAction[Int]] =
    Arbitrary(
      Arbitrary
        .arbInt
        .arbitrary
        .map(n => DbAction(c => n)))

  implicit val transactionFAtoBArb : Arbitrary[DbAction[Int=>Int]] =
    Arbitrary(
      Arbitrary
        .arbInt
        .arbitrary
        .map(n => DbAction(c => (x => n))))

  implicit val connectionArb : Arbitrary[Connection] = Arbitrary(Gen.const(null))

  private def arbitraryValues[A : Arbitrary]: Stream[A] = Stream.continually(implicitly[Arbitrary[A]].arbitrary.sample).flatten

  protected def equalitySamplesCount: Int = 16

  implicit val eqInt : Eq[Int] = Eq.fromUniversalEquals[Int]

  implicit def eqDbAction[T] : Eq[DbAction[T]] = Eq.instance { (t1, t2) =>
    arbitraryValues[Connection].take(equalitySamplesCount).forall(c => t1.run(c) === t2.run(c))
  }

  checkAll("Functor DbAction", FunctorTests[DbAction].functor[Int, Int, Int])
  checkAll("Applicative DbAction", ApplicativeTests[DbAction].applicative[Int, Int, Int])
  checkAll("Monad DbAction", MonadTests[DbAction].stackUnsafeMonad[Int, Int, Int])
  //TODO checkAll("Monad DbAction", MonadTests[DbAction].monad[Int, Int, Int])
}
