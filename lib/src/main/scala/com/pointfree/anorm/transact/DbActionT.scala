package com.pointfree.anorm.transact


import java.sql.Connection

import cats.implicits._
import cats.{Applicative, Functor, Monad, MonadError}

sealed trait DbActionT[M[_], T]

private case class Sql[M[_], T](run: Connection => M[T]) extends DbActionT[M,T]
private case class Lifted[M[_],T](block : Unit => M[T]) extends DbActionT[M,T]
private case class Failed[M[_], T](err : Throwable) extends DbActionT[M, T]

object DbActionT {
  private def applicative[F[_]](implicit a : Applicative[F]) = a

  def apply[F[_], T](run : Connection => F[T]) : DbActionT[F,T] = Sql(conn => run(conn))

  def pure[F[_] : Applicative,T](v : T) : DbActionT[F,T] = lift[F,T]({v})

  def lift[F[_]:Applicative, T](block : => T) : DbActionT[F, T] = Lifted(_ => applicative[F].pure {block})

  def fail[M[_],T](err : Throwable) : DbActionT[M, T] = Failed(err)

  private def run[M[_], T](action : DbActionT[M,T], connection : Connection) (implicit monadError : MonadError[M, Throwable]) : M[T] =
    action match {
      case Sql(run ) => run(connection)
      case Lifted(block) => block()
      case Failed(err) => monadError.raiseError[T](err)
    }

  def execute[M[_], T](action: DbActionT[M, T])(connection: Connection)
                      (implicit monadError : MonadError[M, Throwable]): M[_ <: T] = {
    try {
      connection.setAutoCommit(false)
      val result = run(action, connection)
      connection.commit()
      result
    } catch {
      case ex: Throwable =>
        connection.rollback()
        monadError.raiseError[T](ex)
    } finally {
      connection.close()
    }
  }


  object implicits {

    implicit def dbActionFunctor[F[_]:Functor] =
      new Functor[({ type G[A] = DbActionT[F, A] })#G] {
        val functorF = implicitly[Functor[F]]

        override def map[A, B](fa: DbActionT[F, A])(f: A => B) : DbActionT[F, B] =
          fa match {
            case Sql(stmt) => DbActionT(connection => functorF.map(stmt(connection))(f))
            case Lifted(block) => Lifted(_ => functorF.map(block()) (f))
            case Failed(err) => Failed(err)
          }
      }

    implicit def dbActionApplicative[F[_]:Applicative] =
      new Applicative[({ type G[A] = DbActionT[F, A] })#G] {
        val applicativeF = implicitly[Applicative[F]]

        override def pure[A](x: A): DbActionT[F, A] = DbActionT(c => applicativeF.pure(x))

        override def ap[A, B](ff: DbActionT[F, A => B])(fa: DbActionT[F, A]): DbActionT[F, B] =
          ff match {
            case Sql(stmtF) =>

              fa match {
                case Sql(stmtA) => DbActionT(connection => applicativeF.ap(stmtF(connection))(stmtA(connection)))
                case Lifted(block) => DbActionT(connection => applicativeF.ap(stmtF(connection))(block()))
                case Failed(err) => Failed(err)
              }

            case Lifted(blockF) =>
              fa match {
                case Sql(stmtA) => DbActionT(connection => applicativeF.ap(blockF())(stmtA(connection)))
                case Lifted(blockA) => DbActionT(connection => applicativeF.ap(blockF())(blockA()))
                case Failed(err) => Failed(err)
              }

            case Failed(err) => Failed(err)
          }
      }
//    val x = StateT[Either[Throwable, _], Connection, Int]()

    implicit def dbActionMonad[M[_]](implicit monadError : MonadError[M, Throwable]) =
      new Monad[({ type G[A] = DbActionT[M, A] })#G] {
        override def flatMap[A, B](fa: DbActionT[M,A])(f: A => DbActionT[M,B]): DbActionT[M, B] =
          fa  match {
            case Failed(err) => Failed(err)
            case Lifted(block) => DbActionT(conn => block().flatMap(a => run(f(a), conn)))
            case Sql(stmt) => DbActionT(conn => stmt(conn).flatMap((a => run(f(a), conn))))
          }

        //TODO: implement this as @tailrec
        // See https://github.com/purescript/purescript-tailrec/blob/master/src/Control/Monad/Rec/Class.purs
        override def tailRecM[A, B](a: A)(
          f: A => DbActionT[M, Either[A, B]]): DbActionT[M, B] =
          flatMap(f(a)) {
            case Left(a2) => tailRecM(a2)(f)
            case Right(b) => pure(b)
          }

        override def pure[A](x: A): DbActionT[M, A] = dbActionApplicative[M].pure(x)
      }

  }
}

