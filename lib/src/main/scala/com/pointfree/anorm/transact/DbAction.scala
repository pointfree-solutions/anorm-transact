package com.pointfree.anorm.transact


import java.sql.Connection

import anorm.{ResultSetParser, Row, SimpleSql}

sealed trait DbAction[+T] {
  val run: Connection => T
}

private case class DbActionImpl[T](val run: Connection => T)
  extends DbAction[T]

object DbAction {

  def apply[T](run : Connection => T) :DbAction[T] = DbActionImpl(run)

  def insert[A](sql: SimpleSql[Row], resultSetParser: ResultSetParser[A]): DbAction[A] =
    DbActionImpl(conn => sql.executeInsert(resultSetParser)(conn))

  def update(sql: SimpleSql[Row]): DbAction[Int] =
    DbActionImpl(conn => sql.executeUpdate()(conn))

  def query[T](sql: SimpleSql[Row], rsp: ResultSetParser[T]): DbAction[T] =
    DbActionImpl(conn => sql.as(rsp)(conn))


  def execute[T](action: DbAction[T])(connection: Connection): T = {
    try {
      connection.setAutoCommit(false)

      val result = action.run(connection)

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

