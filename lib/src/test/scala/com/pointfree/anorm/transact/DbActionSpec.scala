package com.pointfree.anorm.transact


import java.sql.Connection

import cats.Eq
import cats.laws.discipline.{ApplicativeTests, FunctorTests, MonadTests}
import com.pointfree.anorm.transact.implicits._
import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalamock.scalatest.MockFactory
import org.scalatest.FunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.typelevel.discipline.scalatest.Discipline

import scala.util.Try

class DbActionSpec extends FunSuite with Discipline with GeneratorDrivenPropertyChecks with MockFactory {
  implicit val connectionCoGen : Cogen[Connection] = Cogen(conn => 1) // TODO what to use here instead of 0?

  def dbActionArb[T : Arbitrary] : Arbitrary[DbAction[T]] = {
    val stmtGen = "DbAction.apply generator" |:
      arbitrary[Connection => Try[T]]
        .map(stmt => DbAction(stmt))

    val liftGen = "DbAction.lift generator" |:
      arbitrary[Unit=>T]
        .map(f => DbAction.lift({f()}))

    val failGen = "DbAction.fail generator" |: Gen.const(DbAction.fail(new RuntimeException("exception")))

    Arbitrary("DbAction generator" |: Gen.oneOf(stmtGen, liftGen, failGen))
  }

  implicit val dbActionArbInt = dbActionArb[Int]
  implicit val dbActionFAtoBArb = dbActionArb[Int=>Int]

  implicit val eqInt : Eq[Int] = Eq.fromUniversalEquals[Int]

  implicit def eqDbAction[T] : Eq[DbAction[T]] = Eq.instance { (t1, t2) =>
    val connectionMock = {
      val c = mock[Connection]
      (c.setAutoCommit (_:Boolean)).expects(false).anyNumberOfTimes().returning(())
      (c.commit _:()=>Unit).expects().anyNumberOfTimes().returning(())
      (c.close _:()=>Unit).expects().anyNumberOfTimes().returning(())
      (c.rollback _:()=>Unit).expects().anyNumberOfTimes().returning(())
      c
    }

    DbAction.execute(t1)(connectionMock) == DbAction.execute(t1)(connectionMock)
  }

  checkAll("Functor DbAction", FunctorTests[DbAction].functor[Int, Int, Int])
  checkAll("Applicative DbAction", ApplicativeTests[DbAction].applicative[Int, Int, Int])
  checkAll("Monad DbAction", MonadTests[DbAction].stackUnsafeMonad[Int, Int, Int])
  //TODO checkAll("Monad DbAction", MonadTests[DbAction].monad[Int, Int, Int])
}
