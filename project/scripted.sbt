resolvers += "typesafe-release" at "https://repo.typesafe.com/typesafe/maven-releases/"

libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-sbt" % "scripted-plugin" % sv
}
