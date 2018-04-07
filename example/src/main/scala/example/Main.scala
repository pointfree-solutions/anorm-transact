package example

import cats.implicits._
import com.pointfree.anorm.transact.DbActionSync
import com.pointfree.anorm.transact.DbActionSync.DbActionSync
import com.pointfree.anorm.transact.DbActionSync.implicits._

import scala.io.StdIn
import scala.util.{Failure, Success}

object Main extends App {
  override def main(args: Array[String]): Unit = {

    DbActionSync.execute(AccountTable.create)(Db.connection)

    println("Initial db: " + DbActionSync.execute(AccountTable.listAll)(Db.connection))

    DbActionSync.execute(
      for {
        _ <- AccountTable.insert(Account("account1", 100))
        _ <- AccountTable.insert(Account("account2", 100))
      } yield ())(Db.connection)

    println("After insert:" + DbActionSync.execute(AccountTable.listAll)(Db.connection))

    val transaction =
      for {
        initialAccounts <- AccountTable.listAll

        amount <- DbActionSync.lift {
          println(s"accounts created: $initialAccounts")

          println("How much to transfer from account1 to account2?")
          val amount = StdIn.readInt()
          if (amount < 0)
            //failure by eception is also possible
            throw new RuntimeException("negative amount is not allowed")
          else
            amount
        }

        _ <- transfer("account1", "account2", amount)
      } yield ()

    val result = DbActionSync.execute(transaction)(Db.connection)
    result match {
      case Success(_) => println("Transaction completed successfully.")
      case Failure(err) => println(s"Transaction failed: ${err.getMessage}.")
    }

    val finalAcocunts =  DbActionSync.execute(AccountTable.listAll)(Db.connection)
    println(s"Final accounts: ${finalAcocunts.get}")

    DbActionSync.execute(AccountTable.drop)(Db.connection)
  }

  def transfer(from: String, to: String, amount: Long): DbActionSync[Unit] =
    for {

      fromAccount <- AccountTable.find(from).flatMap {
          case None => throw new RuntimeException(s"Account $from not found")
          case Some(a) => DbActionSync.pure(a)
        }

      toAccount <- AccountTable.find(to).flatMap {
        case None => throw new RuntimeException(s"Account $to not found")
        case Some(a) => DbActionSync.pure(a)
      }

      _ <- if (fromAccount.amount >= amount)
            for {
              _ <- AccountTable.withdraw(from, amount)
              _ <- AccountTable.deposit(to, amount)
            } yield ()
          else
            throw new RuntimeException("Insuficient funds")
    } yield ()
}

