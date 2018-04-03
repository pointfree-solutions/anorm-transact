import sbt._

object Dependencies {
  val anorm = "org.playframework.anorm" %% "anorm" % "2.6.1"
  val postgres = "org.postgresql" % "postgresql" % "9.4.1208"

  object cats {
    private val version = "1.0.1"
    val core = "org.typelevel" %% "cats-core" % version
    val laws = "org.typelevel" %% "cats-laws" % version

    val scalatest = "com.ironcorelabs" %% "cats-scalatest" % "2.3.1"
  }

  val scalikejdbc = "org.scalikejdbc" %% "scalikejdbc" % "3.2.2"
  val discipline = "org.typelevel" %% "discipline" % "0.8"
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"

  val sl4jNop = "org.slf4j" % "slf4j-nop" % "1.7.25"
}
