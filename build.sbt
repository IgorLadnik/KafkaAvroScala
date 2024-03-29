import Dependencies._

ThisBuild / scalaVersion     := "2.12.6"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "Main"

lazy val root = (project in file("."))
  .settings(
    name := "ScalaPlayground",
    libraryDependencies ++= Seq(
      //scalaTest % Test,
      kafka,
      avro,
      avroSerializer,
      //logBack,
      parser,
      akkaActor,
      akkaHttp,
      akkaStream
    )
)

//resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

//logLevel := Level.Error

//resolvers += "io.confluent" at "http://packages.confluent.io/maven/"

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
