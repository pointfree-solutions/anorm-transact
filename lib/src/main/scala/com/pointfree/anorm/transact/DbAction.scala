package com.pointfree.anorm.transact


import java.sql.Connection

import anorm.{ResultSetParser, Row, SimpleSql}

import scala.util.{Failure, Try}

sealed trait DbAction[+T]

private case class Sql[T](run: Connection => Try[T]) extends DbAction[T]
private case class Lifted[T](block : Unit => Try[T]) extends DbAction[T]
private case class Failed(err : Throwable) extends DbAction[Nothing]

object DbAction {

  def apply[T](run : Connection => Try[T]) : DbAction[T] = Sql(conn => run(conn))

  def insert[A](sql: SimpleSql[Row], resultSetParser: ResultSetParser[A]): DbAction[A] =
    Sql(conn => Try{sql.executeInsert(resultSetParser)(conn)})

  def update(sql: SimpleSql[Row]): DbAction[Int] =
    Sql(conn => Try{sql.executeUpdate()(conn)})

  def query[T](sql: SimpleSql[Row], rsp: ResultSetParser[T]): DbAction[T] =
    Sql(conn => Try{sql.as(rsp)(conn)})

  def lift[T](block : => T) : DbAction[T] = Lifted(_ => Try {block})

  def fail(err : Throwable) : DbAction[Nothing] = Failed(err)

  def execute[T](action: DbAction[T])(connection: Connection): Try[T] = {
    try {
      connection.setAutoCommit(false)

      val result = action match {
        case Sql(run) => run(connection)
        case Lifted(block) => block()
        case Failed(err) => Failure(err)
      }

      connection.commit()

      result
    } catch {
      case ex: Throwable =>
        connection.rollback()
        Failure(ex)
    } finally {
      connection.close()
    }
  }
}

