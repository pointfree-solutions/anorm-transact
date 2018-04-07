package com.pointfree.anorm.transact

import cats.implicits._
import java.sql.Connection

import anorm.{ResultSetParser, Row, SimpleSql}
import cats.{Applicative, Functor, Monad, MonadError}
import com.pointfree.anorm.transact._
import com.pointfree.anorm.transact.DbActionT._

import scala.util.Try

object DbActionSync {
  type DbActionSync[T] = DbActionT[Try, T]

  def apply[T](run : Connection => Try[T]) :DbActionSync[T] = DbActionT(run)

  def pure[T](v: T): DbActionSync[T] = lift[T]({ v })

  def insert[T](sql: SimpleSql[Row], resultSetParser: ResultSetParser[T]): DbActionSync[T] =
    Sql(conn => Try {sql.executeInsert(resultSetParser)(conn)})

  def update(sql: SimpleSql[Row]): DbActionSync[Int] =
    Sql(conn => Try {sql.executeUpdate()(conn)})

  def query[T](sql: SimpleSql[Row], rsp: ResultSetParser[T]): DbActionSync[T] =
    Sql(conn => Try {sql.as(rsp)(conn)})

  def lift[T](block: => T): DbActionSync[T] = DbActionT.lift[Try, T](block)

  def fail[T](err: Throwable): DbActionSync[T] = Failed(err)

  def execute[T](action: DbActionSync[T])(connection: Connection) =
    DbActionT.execute[Try, T](action)(connection)

  object implicits {
    import com.pointfree.anorm.transact.DbActionT.implicits._

    implicit val dbActionSyncFunctor : Functor[DbActionSync] = dbActionFunctor[Try]
    implicit val dbActionSyncApplicative : Applicative[DbActionSync] = dbActionApplicative[Try]
    implicit val dbActionSyncMonad : Monad[DbActionSync] = dbActionMonad[Try]
  }
}
