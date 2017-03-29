ScriptedPlugin.scriptedSettings

scriptedLaunchOpts <++= version apply { version =>
  Seq("-Xmx1024M", "-Dplugin.version=" + version)
}

scriptedBufferLog := false
