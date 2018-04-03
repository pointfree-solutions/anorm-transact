package com.pointfree.anorm.transact

import cats.{Applicative, Functor, Monad}

object implicits {

  implicit val transactionFunctor: Functor[DbAction] =
    new Functor[DbAction] {
      def map[A, B](fa: DbAction[A])(f: A => B) : DbAction[B] =
        DbAction(conn => {
          val v = fa.run(conn)
          f(v)
        })
    }

  implicit val transactionApplicative: Applicative[DbAction] =
    new Applicative[DbAction] {
      override def pure[A](x: A): DbAction[A] = DbAction(c => x)

      override def ap[A, B](ff: DbAction[A => B])(
        fa: DbAction[A]): DbAction[B] =
        DbAction(conn => {
          val f = ff.run(conn)
          val v = fa.run(conn)
          f(v)
        })
    }

  implicit val transactionMonad: Monad[DbAction] = new Monad[DbAction] {
    override def flatMap[A, B](fa: DbAction[A])(
      f: A => DbAction[B]): DbAction[B] =
      DbAction(conn => {
        val x = fa.run(conn)
        f(x).run(conn)
      })

    //TODO: implement this as @tailrec
    // See https://github.com/purescript/purescript-tailrec/blob/master/src/Control/Monad/Rec/Class.purs
    override def tailRecM[A, B](a: A)(
      f: A => DbAction[Either[A, B]]): DbAction[B] =
      flatMap(f(a)) {
        case Left(a2) => tailRecM(a2)(f)
        case Right(b) => pure(b)
      }

    override def pure[A](x: A): DbAction[A] = transactionApplicative.pure(x)
  }

}
