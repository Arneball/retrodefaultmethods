import sbt._
import Keys._

object MyBuild extends Build {
  lazy val thatSett = Project.defaultSettings ++ Seq(
    libraryDependencies := Seq(
      "org.kohsuke" % "asm5" % "5.0.1",
      "org.scala-lang" % "scala-library" % "2.11.2"
    ),
    scalaVersion := "2.11.2"
//  ,  scalacOptions := Seq("-optimize", "-Ybackend:GenBCode")
  )

  lazy val root = Project(
    id="root",
    base=file("."),
    settings=thatSett
  )
}
