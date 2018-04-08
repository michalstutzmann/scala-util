import ReleaseTransformations._

val ScalaVersion = "2.12.5"
val AkkaVersion = "2.5.11"
val AkkaHttpVersion = "10.1.1"
val AkkaHttpJsonVersion = "1.20.1"
val CirceVersion = "0.9.2"
val CatsVersion = "1.1.0"
val JwtCirceVersion = "0.16.0"
val AlpakkaVersion = "0.17"
val AkkaStreamKafkaVersion = "0.19"
val ScalaTestVersion = "3.0.5"
val ScalaCheckVersion = "1.13.5"
val ScodecCoreVersion = "1.10.3"
val ScodecBitsVersion = "1.1.5"
val ThreetenExtraVersion = "1.3.2"
val Slf4jVersion = "1.7.25"
val LogbackVersion = "1.2.3"
val LogbackHoconVersion = "0.1.6"
val ScalaStructlogVersion = "0.1.7"
val ScalaAppVersion = "0.1.9"
val ConfigVersion = "1.3.3"
val SlickVersion = "3.2.2"
val CommonsVfs2Version = "2.1"
val CommonsPoolVersion = "1.6"
val BouncyCastleVersion = "1.59"
val Avro4SVersion = "1.8.3"
val KebsVersion = "1.5.3"

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
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion % Optional,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion % Optional,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion % Optional,
      "com.typesafe.akka" %% "akka-persistence" % AkkaVersion % Optional,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Optional,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % IntegrationTest,
      "com.typesafe.akka" %% "akka-stream-kafka" % AkkaStreamKafkaVersion % Optional,
      "de.heikoseeberger" %% "akka-http-circe" % AkkaHttpJsonVersion % Optional,
      "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % AlpakkaVersion % Optional,
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % AlpakkaVersion % Optional,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Optional,
      "org.scalacheck" %% "scalacheck" % ScalaCheckVersion % Optional,
      "com.github.mwegrz" % "logback-hocon" % LogbackHoconVersion % Optional,
      "com.github.mwegrz" %% "scala-structlog" % ScalaStructlogVersion % Optional,
      "com.github.mwegrz" %% "scala-app" % ScalaAppVersion % Optional,
      "com.typesafe" % "config" % ConfigVersion % Optional,
      "org.scodec" %% "scodec-core" % ScodecCoreVersion % Optional,
      "org.scodec" %% "scodec-bits" % ScodecBitsVersion % Optional,
      "com.typesafe.slick" %% "slick" % SlickVersion % Optional,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion % Optional,
      "io.circe" %% "circe-core" % CirceVersion % Optional,
      "io.circe" %% "circe-generic" % CirceVersion % Optional,
      "io.circe" %% "circe-generic-extras" % CirceVersion % Optional,
      "io.circe" %% "circe-parser" % CirceVersion % Optional,
      "io.circe" %% "circe-java8" % CirceVersion % Optional,
      "org.typelevel" %% "cats-core" % CatsVersion % Optional,
      "org.apache.commons" % "commons-vfs2" % CommonsVfs2Version % Optional,
      "commons-pool" % "commons-pool" % CommonsPoolVersion % Optional,
      "com.pauldijou" %% "jwt-circe" % JwtCirceVersion % Optional,
      "org.bouncycastle" % "bcpkix-jdk15on" % BouncyCastleVersion % Optional,
      "com.sksamuel.avro4s" %% "avro4s-core" % Avro4SVersion % Optional,
      "pl.iterators" %% "kebs-avro" % KebsVersion % Optional
    ),
    Defaults.itSettings,
    fork := true,
    connectInput in run := true,
    scalafmtOnCompile := true,
    // Release settings
    releaseTagName := { (version in ThisBuild).value },
    releaseTagComment := s"Release version ${(version in ThisBuild).value}",
    releaseCommitMessage := s"Set version to ${(version in ThisBuild).value}",
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
