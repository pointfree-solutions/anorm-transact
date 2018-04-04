package com.pointfree.anorm.transact

import cats._

import scala.util.{Failure, Success}

object implicits {

  implicit val dbActionFunctor: Functor[DbAction] =
    new Functor[DbAction] {
      override def map[A, B](fa: DbAction[A])(f: A => B) : DbAction[B] =
        fa match {
          case Sql(stmt) => DbAction(connection => stmt(connection).map(f))
          case Lifted(block) => Lifted(_ => block().map(f))
          case Failed(err) => Failed(err)
        }
    }

  implicit val dbActionApplicative: Applicative[DbAction] =
    new Applicative[DbAction] {
      override def pure[A](x: A): DbAction[A] = DbAction(c => Success(x))

      override def ap[A, B](ff: DbAction[A => B])(fa: DbAction[A]): DbAction[B] =
          ff match {
            case Sql(stmtF) =>

              fa match {
                case Sql(stmtA) => DbAction(connection =>
                  for {
                    f <- stmtF(connection)
                    a <- stmtA(connection)
                  } yield f(a))
                case Lifted(block) => DbAction(connection =>
                  for {
                    f <- stmtF(connection)
                    a <- block()
                  } yield f(a))
                case Failed(err) => Failed(err)
              }

            case Lifted(blockF) =>
              fa match {
                case Sql(stmtA) => DbAction(connection =>
                  for {
                    f <- blockF()
                    a <- stmtA(connection)
                  } yield f(a))
                case Lifted(block) => DbAction(connection =>
                  for {
                    f <- blockF()
                    a <- block()
                  } yield f(a))
                case Failed(err) => Failed(err)
              }
            case Failed(err) => Failed(err)
          }
    }

  implicit val dbActionMonad: Monad[DbAction] = new Monad[DbAction] {
    override def flatMap[A, B](fa: DbAction[A])(f: A => DbAction[B]): DbAction[B] =
      fa  match {
        case Failed(err) => Failed(err)
        case Lifted(block) => block().fold(err => Failed(err),f)
        case Sql(stmt) =>
          DbAction(conn =>
            stmt(conn)
              .flatMap(v => f(v) match {
                case Failed(err) => Failure(err)
                case Sql(stmt2) => stmt2(conn)
                case Lifted(block) => block()
              }))
      }

    //TODO: implement this as @tailrec
    // See https://github.com/purescript/purescript-tailrec/blob/master/src/Control/Monad/Rec/Class.purs
    override def tailRecM[A, B](a: A)(
      f: A => DbAction[Either[A, B]]): DbAction[B] =
      flatMap(f(a)) {
        case Left(a2) => tailRecM(a2)(f)
        case Right(b) => pure(b)
      }

    override def pure[A](x: A): DbAction[A] = dbActionApplicative.pure(x)
  }

}
