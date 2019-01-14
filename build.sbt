import MwegrzLibraryDependencies._

lazy val root = (project in file("."))
  .enablePlugins(MwegrzApache2LibraryPlugin)
  .settings(
    name := "scala-util",
    libraryDependencies ++= Seq(
      AkkaActor % Optional,
      AkkaStream % Optional,
      AkkaStreamTestKit % Optional,
      AkkaHttp % Optional,
      AkkaHttpTestkit % "it,test",
      AkkaPersistence % Optional,
      AkkaSlf4j % Optional,
      AkkaTestkit % Test,
      AkkaHttpCirce % Optional,
      AkkaHttpAvro4s % Optional,
      AlpakkaKafka % Optional,
      AkkaStreamAlpakkaCassandra % Optional,
      AkkaStreamAlpakkaMqtt % Optional,
      AkkaStreamAlpakkaSse % Optional,
      CirceCore % Optional,
      CirceGenericExtras % Optional,
      CirceParser % Optional,
      CatsCore % Optional,
      Avro4sCore % Optional,
      JwtCirce % Optional,
      BouncyCastle % Optional,
      ScodecCore % Optional,
      ScodecBits % Optional,
      KebsAvro % Optional,
      ScalaApp % Optional,
      Slick % Optional,
      SlickHikaricp % Optional,
      JwtCirce % Optional,
      Geodesy % Optional,
      Scalactic % Optional,
      CommonsPool % Optional,
      CommonsVfs2 % Optional,
      ScalaTest % Optional,
      ScalaCheck % Optional
    ),
    homepage := Some(url("http://github.com/mwegrz/scala-util")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/mwegrz/scala-util.git"),
        "scm:git@github.com:mwegrz/scala-util.git"
      )
    )
  )
