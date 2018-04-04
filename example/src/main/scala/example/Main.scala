package example

import cats.implicits._
import com.pointfree.anorm.transact.DbAction
import com.pointfree.anorm.transact.implicits._
import scala.io.StdIn

object Main extends App {
  override def main(args: Array[String]): Unit = {

    DbAction.execute(AccountTable.create)(Db.connection)

    println("Initial db: " + DbAction.execute(AccountTable.listAll)(Db.connection))

    DbAction.execute(
      for {
        _ <- AccountTable.insert(Account("account1", 100))
        _ <- AccountTable.insert(Account("account2", 100))
      } yield ())(Db.connection)

    println("After insert:" + DbAction.execute(AccountTable.listAll)(Db.connection))

    val transaction =
      for {
        initialAccounts <- AccountTable.listAll

        amount <- DbAction.lift {
          println(s"accounts created: $initialAccounts")

          println("How much to transfer from account1 to account2?")
          StdIn.readInt()
        }

        _ <- transfer("account1", "account2", amount)
      } yield ()

    DbAction.execute(transaction)(Db.connection)

    println("Final accounts: " + DbAction.execute(AccountTable.listAll)(Db.connection))

    DbAction.execute(AccountTable.drop)(Db.connection)
  }

  def transfer(from: String, to: String, amount: Long): DbAction[Unit] =
    for {
      accounts <- AccountTable.listAll
      _ <- (accounts.find(acc => acc.id == from), accounts.find(acc => acc.id == to)) match {
        case (Some(fromAcc), Some(toAcc)) =>
          if (fromAcc.amount >= amount)
            for {
              _ <- AccountTable.withdraw(from, amount)
              _ <- AccountTable.deposit(to, amount)
            } yield ()
          else
            DbAction.fail(new RuntimeException("Insuficient funds"))

        case (None, _) => DbAction.fail(new RuntimeException(s"Account $from not found"))
        case (_, None) => DbAction.fail(new RuntimeException(s"Account $to not found"))
      }
    } yield ()
}

