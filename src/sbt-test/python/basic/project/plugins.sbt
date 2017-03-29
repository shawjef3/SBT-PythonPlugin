{
  val pluginVersion = "1.0"//System.getProperty("plugin.version")
  if (pluginVersion == null)
    throw new RuntimeException(
      """|The system property 'plugin.version' is not defined.
        |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("com.rocketfuel.sbt" % "python-plugin" % pluginVersion)
}

logLevel := Level.Debug
