import ReleaseTransformations._

val ScalaVersion = "2.12.2"
val CrossScalaVersions = Seq("2.11.11", ScalaVersion)
val AkkaVersion = "2.5.2"
val AkkaHttpVersion = "10.0.6"
val AkkaHttpCirce = "1.15.0"
val AkkaSseVersion = "3.0.0"
val AlpakkaSse = "0.8"
val SprayJsonVersion = "1.3.2"
val Json4sVersion = "3.5.0"
val ScalaTestVersion = "3.0.1"
val ScalaCheckVersion = "1.13.4"
val ScodecCoreVersion = "1.10.3"
val ScodecBitsVersion = "1.1.2"
val ThreetenExtraVersion = "1.0"
val Slf4jVersion = "1.7.25"
val LogbackVersion = "1.2.3"
val LogbackHoconVersion = "0.1.0-SNAPSHOT"
val ScalaStructlogVersion = "0.1.1-SNAPSHOT"
val ScalaAppVersion = "0.1.0-SNAPSHOT"
val ConfigVersion = "1.3.1"
val CommonsVfs2Version = "2.1"
val CommonsPoolVersion = "1.6"
//val KafkaVersion = "0.9.0.1" // "0.8.2.1"
//val ZooKeeperVersion = "3.4.7"
val AkkaStreamKafkaVersion = "0.13"
val PahoVersion = "1.1.0"
val BouncyCastleVersion = "1.56"
val CassandraDriverVersion = "3.1.2"
//val AkkaStreamAlpakkaMqtt = "0.3"
val CirceVersion = "0.8.0"
val CatsVersion = "0.9.0"
val JwtCoreVersion = "0.12.0"

lazy val root = (project in file("."))
  .enablePlugins(JavaServerAppPackaging, ReleasePlugin, LogbackPlugin, ScalafmtPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "scala-util",
    organization := "com.github.mwegrz",
    scalacOptions in ThisBuild ++= Seq("-feature"),
    // Dependency management
    scalaVersion := ScalaVersion,
    crossScalaVersions := CrossScalaVersions,
    slf4jVersion := Slf4jVersion,
    logbackVersion := LogbackVersion,
    resolvers += "Sonatype Maven Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      "org.threeten" % "threeten-extra" % ThreetenExtraVersion,
      "io.spray" %% "spray-json" % SprayJsonVersion,
      "org.json4s" %% "json4s-native" % Json4sVersion,
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % IntegrationTest,
      "de.heikoseeberger" %% "akka-http-circe" % AkkaHttpCirce,
      //"de.heikoseeberger" %% "akka-sse" % AkkaSseVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-sse" % AlpakkaSse,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Optional,
      "org.scalacheck" %% "scalacheck" % ScalaCheckVersion % IntegrationTest,
      //"org.scalamock" %% "scalamock-scalatest-support" % ScalaMockVersion % Test,
      "com.github.mwegrz" % "logback-hocon" % LogbackHoconVersion,
      "com.github.mwegrz" %% "scala-structlog" % ScalaStructlogVersion,
      "com.github.mwegrz" %% "scala-app" % ScalaAppVersion,
      "com.typesafe" % "config" % ConfigVersion,
      "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % PahoVersion,
      "com.datastax.cassandra" % "cassandra-driver-core" % CassandraDriverVersion,
      "com.datastax.cassandra" % "cassandra-driver-extras" % CassandraDriverVersion,
      "org.scodec" %% "scodec-core" % ScodecCoreVersion,
      "org.scodec" %% "scodec-bits" % ScodecBitsVersion,
      "io.circe" %% "circe-core" % CirceVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "io.circe" %% "circe-parser" % CirceVersion,
      "io.circe" %% "circe-java8" % CirceVersion,
      "org.typelevel" %% "cats" % CatsVersion,
      "com.typesafe.akka" %% "akka-stream-kafka" % AkkaStreamKafkaVersion,
      "org.apache.commons" % "commons-vfs2" % CommonsVfs2Version,
      "commons-pool" % "commons-pool" % CommonsPoolVersion,
      "com.pauldijou" %% "jwt-core" % JwtCoreVersion,
      "org.bouncycastle" % "bcpkix-jdk15on" % BouncyCastleVersion
    ),
    Defaults.itSettings,
    // Publishing
    publishMavenStyle := true,
    crossPaths := true,
    autoScalaLibrary := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ =>
      false
    },
    pomExtra := (<url>http://github.com/mwegrz/scala-util</url>
        <licenses>
          <license>
            <name>Apache License 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:mwegrz/scala-util.git</url>
          <connection>scm:git:git@github.com:mwegrz/scala-util.git</connection>
        </scm>
        <developers>
          <developer>
            <id>mwegrz</id>
            <name>Michał Węgrzyn</name>
            <url>http://github.com/mwegrz</url>
          </developer>
        </developers>),
    releaseTagComment := s"Release version ${(version in ThisBuild).value}",
    releaseCommitMessage := s"Set version to ${(version in ThisBuild).value}",
    releaseProcess := Seq[ReleaseStep](
      //checkSnapshotDependencies,
      inquireVersions,
      //runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),
    offline := true,
    fork := true,
    connectInput in run := true,
    scalafmtOnCompile := true
  )
