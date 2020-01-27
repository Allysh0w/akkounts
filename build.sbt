// *****************************************************************************
// Projects
// *****************************************************************************

lazy val akkounts =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, JavaAppPackaging, DockerPlugin)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.akkaClusterShardingTyped,
        library.akkaDiscovery,
        library.akkaHttp,
        library.akkaHttpSprayJson, // Transitive dependency of Akka Management
        library.akkaMgmClusterBootstrap,
        library.akkaMgmDiscoveryK8n,
        library.akkaPersistenceCassandra,
        library.akkaPersistenceQuery,
        library.akkaPersistenceTyped,
        library.akkaStreamTyped, // Transitive dependency of Streamee
        library.borerCompatAkka,
        library.borerCore,
        library.borerDerivation,
        library.disruptor,
        library.log4jCore,
        library.log4jSlf4j,
        library.pureConfig,
        library.streamee,
        library.akkaActorTestkitTyped   % Test,
        library.akkaHttpTestkit         % Test,
        library.akkaStreamTestkit       % Test,
        library.scalaCheck              % Test,
        library.scalaTest               % Test,
        library.scalaTestPlusScalaCheck % Test,
      )
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val akka                     = "2.6.3"
      val akkaHttp                 = "10.1.11"
      val akkaMgm                  = "1.0.5"
      val akkaPersistenceCassandra = "0.102"
      val borer                    = "1.4.0"
      val disruptor                = "3.4.2"
      val log4j                    = "2.13.1"
      val pureConfig               = "0.12.3"
      val scalaCheck               = "1.14.3"
      val scalaTest                = "3.1.1"
      val scalaTestPlusScalaCheck  = "3.1.1.1"
      val streamee                 = "5.0.0"
    }
    val akkaActorTestkitTyped    = "com.typesafe.akka"             %% "akka-actor-testkit-typed"          % Version.akka
    val akkaClusterShardingTyped = "com.typesafe.akka"             %% "akka-cluster-sharding-typed"       % Version.akka
    val akkaDiscovery            = "com.typesafe.akka"             %% "akka-discovery"                    % Version.akka
    val akkaHttp                 = "com.typesafe.akka"             %% "akka-http"                         % Version.akkaHttp
    val akkaHttpSprayJson        = "com.typesafe.akka"             %% "akka-http-spray-json"              % Version.akkaHttp
    val akkaHttpTestkit          = "com.typesafe.akka"             %% "akka-http-testkit"                 % Version.akkaHttp
    val akkaMgmClusterBootstrap  = "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Version.akkaMgm
    val akkaMgmDiscoveryK8n      = "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % Version.akkaMgm
    val akkaPersistenceCassandra = "com.typesafe.akka"             %% "akka-persistence-cassandra"        % Version.akkaPersistenceCassandra
    val akkaPersistenceQuery     = "com.typesafe.akka"             %% "akka-persistence-query"            % Version.akka
    val akkaPersistenceTyped     = "com.typesafe.akka"             %% "akka-persistence-typed"            % Version.akka
    val akkaStreamTestkit        = "com.typesafe.akka"             %% "akka-stream-testkit"               % Version.akka
    val akkaStreamTyped          = "com.typesafe.akka"             %% "akka-stream-typed"                 % Version.akka
    val borerCompatAkka          = "io.bullet"                     %% "borer-compat-akka"                 % Version.borer
    val borerCore                = "io.bullet"                     %% "borer-core"                        % Version.borer
    val borerDerivation          = "io.bullet"                     %% "borer-derivation"                  % Version.borer
    val disruptor                = "com.lmax"                      %  "disruptor"                         % Version.disruptor
    val log4jCore                = "org.apache.logging.log4j"      %  "log4j-core"                        % Version.log4j
    val log4jSlf4j               = "org.apache.logging.log4j"      %  "log4j-slf4j-impl"                  % Version.log4j
    val pureConfig               = "com.github.pureconfig"         %% "pureconfig"                        % Version.pureConfig
    val scalaCheck               = "org.scalacheck"                %% "scalacheck"                        % Version.scalaCheck
    val scalaTest                = "org.scalatest"                 %% "scalatest"                         % Version.scalaTest
    val scalaTestPlusScalaCheck  = "org.scalatestplus"             %% "scalacheck-1-14"                   % Version.scalaTestPlusScalaCheck
    val streamee                 = "io.moia"                       %% "streamee"                          % Version.streamee
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
  scalafmtSettings ++
  dockerSettings ++
  commandAliases

lazy val commonSettings =
  Seq(
    scalaVersion := "2.13.1",
    organization := "rocks.heikoseeberger",
    organizationName := "Heiko Seeberger",
    startYear := Some(2020),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8",
      "-Ywarn-unused:imports",
    ),
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    Test / unmanagedSourceDirectories := Seq((Test / scalaSource).value),
)

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true,
  )

lazy val dockerSettings =
  Seq(
    Docker / maintainer := organizationName.value,
    Docker / version := "latest",
    dockerBaseImage := "adoptopenjdk:8u242-b08-jdk-hotspot",
    dockerExposedPorts := Seq(8080, 8558, 25520),
  )

lazy val commandAliases =
  addCommandAlias(
    "r1",
    """|reStart
        |---
        |-Dakka.cluster.seed-nodes.0=akka://akkounts@127.0.0.1:25520
        |-Dakka.management.cluster.bootstrap.contact-point-discovery.discovery-method=config
        |-Dakka.management.http.hostname=127.0.0.1
        |-Dakka.remote.artery.canonical.hostname=127.0.0.1
        |-Dakkounts.http-server.interface=127.0.0.1
        |-Dcassandra-journal.contact-points=127.0.0.1
        |-Dcassandra-snapshot-store.contact-points=127.0.0.1
        |""".stripMargin
  ) ++
  addCommandAlias(
    "r2",
    """|reStart
        |---
        |-Dakka.cluster.seed-nodes.0=akka://akkounts@127.0.0.1:25520
        |-Dakka.management.cluster.bootstrap.contact-point-discovery.discovery-method=config
        |-Dakka.management.http.hostname=127.0.0.2
        |-Dakka.remote.artery.canonical.hostname=127.0.0.2
        |-Dakkounts.http-server.interface=127.0.0.2
        |-Dcassandra-journal.contact-points=127.0.0.1
        |-Dcassandra-snapshot-store.contact-points=127.0.0.1
        |""".stripMargin
  )
