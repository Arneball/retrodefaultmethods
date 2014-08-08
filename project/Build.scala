import sbt.Package.ManifestAttributes
import sbt._
import Keys._
import sbtassembly.Plugin._
import AssemblyKeys._
object MyBuild extends Build {
  lazy val thatSett = Project.defaultSettings ++ assemblySettings ++ Seq(
    libraryDependencies := Seq(
      "org.kohsuke" % "asm5" % "5.0.1",
      "org.scala-lang" % "scala-library" % "2.11.2"
    ),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    scalaVersion := "2.11.2",
    packageOptions in (Compile, packageBin) := Seq(ManifestAttributes("Premain-Class" -> "PreMain", "Main-Class" -> "AsmTest"))
  ) ++ Seq(mainClass in assembly := Some("AsmTest"))

  lazy val root = Project(
    id="root",
    base=file("."),
    settings=thatSett
  )
}
