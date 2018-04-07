package com.pointfree.anorm.transact


import java.sql.Connection

import cats.Eq
import cats.laws.IsEq
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import cats.laws.discipline.{ApplicativeTests, FunctorTests, MonadTests}
import com.pointfree.anorm.transact.DbActionSync.DbActionSync
import com.pointfree.anorm.transact.DbActionSync.implicits._
import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalamock.scalatest.MockFactory
import org.scalatest.FunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.typelevel.discipline.scalatest.Discipline

import scala.util.Try

class DbActionSyncSpec extends FunSuite with Discipline with GeneratorDrivenPropertyChecks with MockFactory {
  implicit val connectionCoGen : Cogen[Connection] = Cogen(conn => 1) // TODO what to use here instead of 0?

  def dbActionArb[T : Arbitrary] : Arbitrary[DbActionSync[T]] = {
    val stmtGen = "DbAction.apply generator" |:
      arbitrary[Connection => Try[T]]
        .map(stmt => DbActionSync(stmt))

    val liftGen = "DbAction.lift generator" |:
      arbitrary[Unit=>T]
        .map(f => DbActionSync.lift({f()}))

    val failGen = "DbAction.fail generator" |: Gen.const(DbActionSync.fail[T](new RuntimeException("exception")))

    Arbitrary("DbAction generator" |: Gen.oneOf(stmtGen, liftGen, failGen))
  }

  implicit val dbActionArbInt = dbActionArb[Int]
  implicit val dbActionFAtoBArb = dbActionArb[Int=>Int]

  implicit val eqInt : Eq[Int] = Eq.fromUniversalEquals[Int]

  implicit def eqDbAction[T] : Eq[DbActionSync[T]] = Eq.instance { (t1, t2) =>
    val connectionMock = {
      val c = mock[Connection]
      (c.setAutoCommit (_:Boolean)).expects(false).anyNumberOfTimes().returning(())
      (c.commit _:()=>Unit).expects().anyNumberOfTimes().returning(())
      (c.close _:()=>Unit).expects().anyNumberOfTimes().returning(())
      (c.rollback _:()=>Unit).expects().anyNumberOfTimes().returning(())
      c
    }

    DbActionSync.execute(t1)(connectionMock) == DbActionSync.execute(t1)(connectionMock)
  }

  //TODO: fix the compilation errors:
//    [error] /Users/ovidiu/work/anorm-transact/lib/src/test/scala/com/pointfree/anorm/transact/DbActionSyncSpec.scala:58:78: could not find implicit value for parameter iso: cats.laws.discipline.SemigroupalTests.Isomorphisms[[T]com.pointfree.anorm.transact.DbActionT[scala.util.Try,T]]
//  [error]   checkAll("Applicative DbAction", ApplicativeTests[DbActionSync].applicative[Int, Int, Int])
//    [error]                                                                              ^
//    [error] /Users/ovidiu/work/anorm-transact/lib/src/test/scala/com/pointfree/anorm/transact/DbActionSyncSpec.scala:59:71: could not find implicit value for parameter iso: cats.laws.discipline.SemigroupalTests.Isomorphisms[[T]com.pointfree.anorm.transact.DbActionT[scala.util.Try,T]]
//  [error]   checkAll("Monad DbAction", MonadTests[DbActionSync].stackUnsafeMonad[Int, Int, Int])
//    [error]                                                                       ^
//    [error] two errors found
//  checkAll("Functor DbAction", FunctorTests[DbActionSync].functor[Int, Int, Int])
//  checkAll("Applicative DbAction", ApplicativeTests[DbActionSync].applicative[Int, Int, Int])
//  checkAll("Monad DbAction", MonadTests[DbActionSync].stackUnsafeMonad[Int, Int, Int])
  //TODO checkAll("Monad DbAction", MonadTests[DbAction].monad[Int, Int, Int])
}
