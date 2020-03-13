addSbtPlugin("com.dwijnand"      % "sbt-dynver"          % "4.0.0")
addSbtPlugin("com.thesamet"      % "sbt-protoc"          % "0.99.28")
addSbtPlugin("com.typesafe.sbt"  % "sbt-native-packager" % "1.6.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header"          % "5.4.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"        % "2.3.2")
addSbtPlugin("io.spray"          % "sbt-revolver"        % "0.9.1")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.2"
