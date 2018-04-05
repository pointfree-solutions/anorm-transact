# anorm-transact

CircleCI build [![CircleCI](https://circleci.com/gh/pointfree-solutions/anorm-transact.svg?style=svg)](https://circleci.com/gh/pointfree-solutions/anorm-transact)

## Overview

Thin layer on top of Anorm for better composability. It relies on `DbAction` trait which has implementations for `Functor`, `Applicative` and `Monad`

## Usage
DB operations can be defined like:
```scala
  def insert(account : Account) =
    DbAction.update(
      SQL("insert into accounts (account_id, amount) values ({account_id}, {amount})")
        .on(
          "account_id" -> account.id,
          "amount" -> account.amount))

  val create =
    DbAction.update(
      SQL("create table accounts (account_id text PRIMARY KEY NOT NULL, amount integer NOT NULL)"))

  val drop = DbAction.update(SQL("drop table accounts"))

  def listAll =
    DbAction.query(
      SQL("select * from accounts"),
      Account.rowParser.*)

  def find(accountId : String) : DbAction[Option[Account]] =
    DbAction
      .query( SQL("select * from accounts where account_id = {account_id}")
                .on("account_id" -> accountId),
              Account.rowParser.*)
      .map(_.headOption)
```

They can be later composed into bigger `DbAction`s as `Functor`s and `Monad`s.

```scala 
  def transfer(from: String, to: String, amount: Long): DbAction[Unit] =
    for {
    
      fromAccount <- AccountTable.find(from).flatMap {
          case None => throw new RuntimeException(s"Account $from not found")
          case Some(a) => DbAction.pure(a)
        }
    
      toAccount <- AccountTable.find(to).flatMap {
        case None => throw new RuntimeException(s"Account $to not found")
        case Some(a) => DbAction.pure(a)
      }
    
      _ <- if (fromAccount.amount >= amount)
            for {
              _ <- AccountTable.withdraw(from, amount)
              _ <- AccountTable.deposit(to, amount)
            } yield ()
          else
            throw new RuntimeException("Insuficient funds")
    } yield ()
```

For a working sample see [the example project](example)

## License

[Simplified BSD License](https://opensource.org/licenses/bsd-license.php)