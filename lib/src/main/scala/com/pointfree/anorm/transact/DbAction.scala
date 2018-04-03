package com.pointfree.anorm.transact


import java.sql.Connection

import anorm.{ResultSetParser, Row, SimpleSql}

sealed trait DbAction[+T]
private case class Sql[T](run: Connection => T) extends DbAction[T]
private case class Lifted[T](block : Unit => T) extends DbAction[T]
private case class Failed(err : Throwable) extends DbAction[Nothing]

object DbAction {

  def apply[T](run : Connection => T) :DbAction[T] = Sql(run)

  def insert[A](sql: SimpleSql[Row], resultSetParser: ResultSetParser[A]): DbAction[A] =
    Sql(conn => sql.executeInsert(resultSetParser)(conn))

  def update(sql: SimpleSql[Row]): DbAction[Int] =
    Sql(conn => sql.executeUpdate()(conn))

  def query[T](sql: SimpleSql[Row], rsp: ResultSetParser[T]): DbAction[T] =
    Sql(conn => sql.as(rsp)(conn))

  def lift[T](block : => T) : DbAction[T] = Lifted(_ => block)

  def fail(err : Throwable) : DbAction[Nothing] = Failed(err)

  def run[T](action : DbAction[T], connection:Connection) =
    action match {
      case Sql(run) => run(connection)
      case Lifted(block) => block()
      case Failed(err) => throw err
    }

  def execute[T](action: DbAction[T])(connection: Connection): T = {
    try {
      connection.setAutoCommit(false)

      val result = run(action,connection)

      connection.commit()

      result
    } catch {
      case ex: Throwable =>
        connection.rollback()
        throw ex
    } finally {
      connection.close()
    }
  }
}

