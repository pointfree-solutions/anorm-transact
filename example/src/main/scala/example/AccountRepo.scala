package example

import cats.implicits._
import com.pointfree.anorm.transact.DbAction
import com.pointfree.anorm.transact.implicits._
import anorm.RowParser
import anorm.SqlParser.{long, str}
import anorm.SQL

case class Account ( id : String,
                     amount : Long
                   )

object Account {
  val rowParser: RowParser[Account] =
    for {
      id <- str("account_id")
      amount <- long("amount")
    } yield Account(id, amount)
}

object AccountTable {
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

  private def findAllById(accountId : String) : DbAction[List[Account]] =
    DbAction.query(
      SQL("select * from accounts where account_id={account_id}")
        .on("account_id" -> accountId),
      Account.rowParser.*)

  def find(accountId : String) : DbAction[Option[Account]] =
    findAllById(accountId).map(lst => lst.headOption)

  def updateById(accountId : String, amount : Long) : DbAction[Int] =
    DbAction.update(
      SQL("update accounts set amount = {new_amount} where account_id={account_id}")
        .on(
          "account_id" -> accountId,
          "new_amount" -> amount))

  private def update(accountId : String, accountDbAction : Long => Long) =
    for {
      accounts <- findAllById(accountId)

      changed <- accounts.headOption match {
        case None => throw new RuntimeException(s"Account $accountId not found")
        case Some(account) => updateById(accountId, accountDbAction(account.amount))
      }
    } yield changed

  def deposit(accountId : String, sum : Long) = update(accountId, amount => amount + sum)
  def withdraw(accountId : String, sum : Long) = update(accountId, amount => amount - sum)
}
