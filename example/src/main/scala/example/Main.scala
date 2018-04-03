package example

import cats.implicits._
import com.pointfree.anorm.transact.implicits._
import com.pointfree.anorm.transact.DbAction
import scalikejdbc._

object Db {
  Class.forName("org.postgresql.Driver")
  ConnectionPool.singleton("jdbc:postgresql://localhost/bankexample", "dev", "dev")

  def connection  = ConnectionPool.borrow()
}

object Main extends App {
  override def main(args: Array[String]): Unit = {

    DbAction.execute(AccountTable.create)(Db.connection)

    println("Initial db: " + DbAction.execute(AccountTable.listAll)(Db.connection))

    DbAction.execute(
      for {
        _ <- AccountTable.insert(Account("account1", 0))
        _ <- AccountTable.insert(Account("account2", 0))
      } yield ())(Db.connection)

    println("After insert:" + DbAction.execute(AccountTable.listAll)(Db.connection))

    DbAction.execute(
      for {
        _ <- AccountTable.deposit("account1", 100)
        _ <- AccountTable.deposit("account2", 100)
      } yield ())(Db.connection)

    DbAction.execute(transfer("account1", "account2", 100))(Db.connection)

    println("Final accounts: " + DbAction.execute(AccountTable.listAll)(Db.connection))

    DbAction.execute(AccountTable.drop)(Db.connection)
  }

  def transfer(from: String, to: String, amount: Long) : DbAction[Unit] =
    for {
      _ <- AccountTable.deposit(to, amount)
      _ <- AccountTable.withdraw(from, amount)
    } yield ()
}
