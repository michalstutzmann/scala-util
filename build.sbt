import ReleaseTransformations._

val ScalaVersion = "2.12.3"
val AkkaVersion = "2.5.4"
val AkkaHttpVersion = "10.0.9"
val AkkaStreamKafkaVersion = "0.17"
val AkkaHttpCirce = "1.17.0"
val SprayJsonVersion = "1.3.2"
val Json4sVersion = "3.5.0"
val ScalaTestVersion = "3.0.1"
val ScalaCheckVersion = "1.13.4"
val ScodecCoreVersion = "1.10.3"
val ScodecBitsVersion = "1.1.2"
val ThreetenExtraVersion = "1.0"
val Slf4jVersion = "1.7.25"
val LogbackVersion = "1.2.3"
val LogbackHoconVersion = "0.1.3"
val ScalaStructlogVersion = "0.1.5"
val ScalaAppVersion = "0.1.5"
val ConfigVersion = "1.3.1"
val CommonsVfs2Version = "2.1"
val CommonsPoolVersion = "1.6"
val PahoVersion = "1.1.0"
val BouncyCastleVersion = "1.56"
val CassandraDriverVersion = "3.3.0"
val CirceVersion = "0.8.0"
val CatsVersion = "0.9.0"
val JwtCirceVersion = "0.14.0"

lazy val root = (project in file("."))
  .enablePlugins(ReleasePlugin, LogbackPlugin, ScalafmtPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "scala-util",
    organization := "com.github.mwegrz",
    scalacOptions in ThisBuild ++= Seq("-feature"),
    // Dependency management
    scalaVersion := ScalaVersion,
    slf4jVersion := Slf4jVersion,
    logbackVersion := LogbackVersion,
    resolvers += "Sonatype Maven Snapshots" at "https://oss.sonatype.org/content/repositories/releases",
    libraryDependencies ++= Seq(
      "org.threeten" % "threeten-extra" % ThreetenExtraVersion % Optional,
      "io.spray" %% "spray-json" % SprayJsonVersion % Optional,
      "org.json4s" %% "json4s-native" % Json4sVersion % Optional,
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion % Optional,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion % Optional,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion % Optional,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Optional,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % IntegrationTest,
      "com.typesafe.akka" %% "akka-stream-kafka" % AkkaStreamKafkaVersion % Optional,
      "de.heikoseeberger" %% "akka-http-circe" % AkkaHttpCirce % Optional,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Optional,
      "org.scalacheck" %% "scalacheck" % ScalaCheckVersion % IntegrationTest,
      //"org.scalamock" %% "scalamock-scalatest-support" % ScalaMockVersion % Test,
      "com.github.mwegrz" % "logback-hocon" % LogbackHoconVersion % Optional,
      "com.github.mwegrz" %% "scala-structlog" % ScalaStructlogVersion % Optional,
      "com.github.mwegrz" %% "scala-app" % ScalaAppVersion % Optional,
      "com.typesafe" % "config" % ConfigVersion % Optional,
      "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % PahoVersion % Optional,
      "com.datastax.cassandra" % "cassandra-driver-core" % CassandraDriverVersion % Optional,
      "com.datastax.cassandra" % "cassandra-driver-extras" % CassandraDriverVersion % Optional,
      "org.scodec" %% "scodec-core" % ScodecCoreVersion % Optional,
      "org.scodec" %% "scodec-bits" % ScodecBitsVersion % Optional,
      "io.circe" %% "circe-core" % CirceVersion % Optional,
      "io.circe" %% "circe-generic" % CirceVersion % Optional,
      "io.circe" %% "circe-parser" % CirceVersion % Optional,
      "io.circe" %% "circe-java8" % CirceVersion % Optional,
      "org.typelevel" %% "cats" % CatsVersion % Optional,
      "org.apache.commons" % "commons-vfs2" % CommonsVfs2Version % Optional,
      "commons-pool" % "commons-pool" % CommonsPoolVersion % Optional,
      "com.pauldijou" %% "jwt-circe" % JwtCirceVersion % Optional,
      "org.bouncycastle" % "bcpkix-jdk15on" % BouncyCastleVersion % Optional,
      "com.sksamuel.avro4s" %% "avro4s-core" % "1.7.0" % Optional,
      "pl.iterators" %% "kebs-avro" % "1.5.0" % Optional
    ),
    Defaults.itSettings,
    offline := true,
    fork := true,
    connectInput in run := true,
    scalafmtOnCompile := true,
    // Release settings
    releaseTagName := { (version in ThisBuild).value },
    releaseTagComment := s"Release version ${(version in ThisBuild).value}",
    releaseCommitMessage := s"Set version to ${(version in ThisBuild).value}",
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommandAndRemaining("+sonatypeReleaseAll"),
      pushChanges
    ),
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    // Publish settings
    crossPaths := true,
    autoScalaLibrary := true,
    publishTo := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    ),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ =>
      false
    },
    licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("http://github.com/mwegrz/scala-util")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/mwegrz/scala-util.git"),
        "scm:git@github.com:mwegrz/scala-util.git"
      )
    ),
    developers := List(
      Developer(
        id = "mwegrz",
        name = "Michał Węgrzyn",
        email = null,
        url = url("http://github.com/mwegrz")
      )
    )
  )
