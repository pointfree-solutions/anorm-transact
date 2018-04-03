package example

import scalikejdbc.ConnectionPool

object Db {
  Class.forName("org.postgresql.Driver")
  ConnectionPool.singleton("jdbc:postgresql://localhost/bankexample", "dev", "dev")

  def connection  = ConnectionPool.borrow()
}
