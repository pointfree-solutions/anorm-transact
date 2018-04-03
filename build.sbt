import sbt._
import Dependencies._

lazy val commonSettings = Seq(
  organization := "com.pointfree",
  scalaVersion := "2.12.4",
  version := "0.1.0-SNAPSHOT")

lazy val lib =
  (project in file("lib"))
    .settings(
      inThisBuild(commonSettings),
      name := "anorm-transact",
      libraryDependencies ++= Seq (
        anorm,
        postgres,
        cats.core,
        scalatest % Test,
        cats.laws % Test,
        cats.scalatest % Test,
        discipline % Test)
    )

lazy val example =
  (project in file("example"))
    .settings(
      inThisBuild(commonSettings),
      name := "anorm-transact-example",
      libraryDependencies ++= Seq (
        sl4jNop,
        postgres,
        cats.core,
        scalikejdbc
        ))
    .dependsOn(lib)
