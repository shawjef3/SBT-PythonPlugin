package com.rocketfuel.sbt.python

import java.nio.file._
import sbt._

object PythonPlugin extends AutoPlugin {

  lazy val python = taskKey[Unit]("Compile python sources.")

  lazy val testPython = taskKey[Unit]("Run Python tests.")

  lazy val pythonSourceRoot = settingKey[File]("The Python source directory.")

  lazy val pythonManagedSourceRoot = settingKey[File]("Directory for Python sources generated by the build.")

  lazy val pythonBinary = taskKey[File]("Defines the local Python binary to use for compilation, running, and testing.")

  lazy val pythonSources = taskKey[Seq[File]]("List the Python source files in pythonSource.")

  lazy val pythonManagedSources = taskKey[Seq[File]]("List the Python source files in pythonManagedSource.")

  lazy val pythonClassDirectory = settingKey[File]("The directory for compiled Python code.")

  lazy val pythonZip = taskKey[Unit]("Zip the compiled Python source files.")

  def rawProjectSettings: Seq[Def.Setting[_]] =
    Seq(
      pythonClassDirectory := Keys.target.value / "python" / (Defaults.prefix(Keys.configuration.value.name) + "classes"),
      pythonSourceRoot := Keys.sourceDirectory.value / "python",
      pythonManagedSourceRoot := Keys.target.value / "python" / "src_managed" / Defaults.nameForSrc(Keys.configuration.value.name),
      pythonSources := listPythonSources(pythonSourceRoot.value, sbt.Keys.streams.value.log),
      pythonManagedSources := listPythonSources(pythonManagedSourceRoot.value, sbt.Keys.streams.value.log),
      Keys.managedSourceDirectories += pythonManagedSourceRoot.value,
      Keys.sourceDirectories ++= Seq(pythonSourceRoot.value, pythonManagedSourceRoot.value),
      python := {
        val logger = Keys.streams.value.log
        val pythonTargetV = pythonClassDirectory.value.toPath

        IO.delete(pythonTargetV.toFile)

        Files.createDirectories(pythonTargetV)

        def copyFile(root: Path, fileToCopy: Path): Unit = {
          val relative = root.relativize(fileToCopy)
          val destination = pythonTargetV.resolve(relative)
          logger.debug(s"Copying $fileToCopy to $destination.")
          Files.copy(fileToCopy, destination)
        }

        val pythonSourceRootV = pythonSourceRoot.value.toPath

        for (fileToCopy <- pythonSources.value) {
          copyFile(pythonSourceRootV, fileToCopy.toPath)
        }

        val pythonManagedSourceRootV = pythonManagedSourceRoot.value.toPath

        for (fileToCopy <- pythonManagedSources.value) {
          copyFile(pythonManagedSourceRootV, fileToCopy.toPath)
        }
      },
      pythonZip := {
        val pythonClassDirectoryV = pythonClassDirectory.value
        val projectName = Keys.name.value
        val projectVersion = Keys.version.value
        val fileSuffix = {
          val configurationName = Keys.configuration.value.name
          if (configurationName == Compile.name) ""
          else "-" + configurationName
        }
        val destFileV = Keys.target.value / "python" / s"$projectName-$projectVersion$fileSuffix.zip"
        val loggerV = Keys.streams.value.log

        zipFiles(pythonClassDirectoryV, destFileV, loggerV)
      },
      pythonZip <<= pythonZip.dependsOn(python)
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    inConfig(Compile)(rawProjectSettings) ++
      inConfig(Test)(rawProjectSettings) ++ Seq(
      pythonBinary := pythonBinaryPath()
    )

  /**
    * List all the .py files in a directory.
    *
    * @param pythonSourceDir
    * @return
    */
  def listPythonSources(pythonSourceDir: File, logger: Logger): Seq[File] = {
    logger.debug(s"looking for py files in $pythonSourceDir")
    if (Files.exists(pythonSourceDir.toPath)) {
      pythonSourceDir ** "*.py" get
    } else Seq.empty
  }

  /**
    * Find the default python executable.
    *
    * @return
    */
  def pythonBinaryPath(): File = {
    val searchCommand =
      if (System.getProperty("os.name").startsWith("Windows")) Seq("where", "python")
      else Seq("which", "python")
    file(Process(searchCommand).lines.head)
  }

  /**
    * Add the files in pythonSources to a new zip file, destFile.
    *
    * @param destFile The zip file to be created.
    * @param logger
    */
  def zipFiles(
    pythonClassesRoot: File,
    destFile: File,
    logger: Logger
  ): Unit = {
    import collection.convert.wrapAll._

    //Create a new zip file.
    val zipURI = new URI("jar:file:" + destFile.getAbsolutePath)
    Files.deleteIfExists(destFile.toPath)
    val zipFs = FileSystems.newFileSystem(zipURI, Map("create" -> "true"))

    val pythonClassesRootPath = pythonClassesRoot.toPath

    /**
      * Get the path of the file in the zip file.
      */
    def relativize(classFile: Path): Path = {
      val zipPath = pythonClassesRootPath.relativize(classFile)
      zipFs.getPath("/").resolve(zipPath.toString)
    }

    val classFiles =
      for (pythonClass <- pythonClassesRoot.***.get.filter(_.isFile)) yield {
        pythonClass.toPath
      }

    for (classFile <- classFiles) {
      val zipPythonSource = relativize(classFile)
      logger.debug(s"Copying $classFile to $destFile:$zipPythonSource")
      Files.copy(classFile, zipPythonSource)
    }

    zipFs.close()
  }

}
