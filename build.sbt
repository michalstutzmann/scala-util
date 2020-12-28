import MichalStutzmannLibraryDependencies._

lazy val root = (project in file("."))
  .enablePlugins(MichalStutzmannApache2LibraryPlugin)
  .settings(
    name := "scala-util",
    libraryDependencies ++= Seq(
      AkkaActor % Optional,
      AkkaStream % Optional,
      AkkaStreamTestKit % Optional,
      AkkaHttp % Optional,
      AkkaHttpTestkit % "it,test",
      AkkaPersistence % Optional,
      AkkaPersistenceTyped % Optional,
      AkkaSlf4j % Optional,
      AkkaTestkit % Test,
      AkkaHttpCors % Optional,
      AkkaHttpCirce % Optional,
      AkkaHttpAvro4s % Optional,
      AlpakkaKafka % Optional,
      AkkaStreamAlpakkaCassandra % Optional,
      AkkaStreamAlpakkaMqttStreaming % Optional,
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
      ScalaApp % Optional,
      Slick % Optional,
      SlickHikaricp % Optional,
      Geodesy % Optional,
      Scalactic % Optional,
      CommonsPool % Optional,
      CommonsVfs2 % Optional,
      ScalaTest % Optional,
      ScalaCheck % Optional,
      AkkaStreamAlpakkaUdp % Optional,
      Courier % Optional,
      LettuceCore % Optional,
      Time4jBase % Optional,
      TapirCore % Optional
    ),
    homepage := Some(url("http://github.com/michalstutzmann/scala-util")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/michalstutzmann/scala-util.git"),
        "scm:git@github.com:michalstutzmann/scala-util.git"
      )
    )
  )
