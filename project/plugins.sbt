libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.7"
libraryDependencies += "org.vafer" % "jdeb" % "1.3" artifacts Artifact("jdeb", "jar", "jar")
resolvers += "Sonatype Maven Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "0.3")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.4")
addSbtPlugin("com.github.mwegrz" % "sbt-logback" % "0.1.0-SNAPSHOT")