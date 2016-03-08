package com.rocketfuel.sbt.python

import java.nio.file._
import sbt._

object PythonPlugin extends AutoPlugin {

  val python = TaskKey[Unit]("Compile python sources.")

  val testPython = TaskKey[Unit]("Run Python tests.")

  val pythonSource = SettingKey[File]("The Python source directory.")

  val pythonManagedSource = SettingKey[File]("Directory for Python sources generated by the build.")

  val pythonBinary = SettingKey[File]("Defines the local Python binary to use for compilation, running, and testing.")

  val pythonManagedSources = TaskKey[Seq[File]]("List the Python source files in pythonManagedSource")

  val pythonTarget = SettingKey[File]("The directory for compiled Python code.")

  val pythonZip = TaskKey[Unit]("Zip the compiled Python source files.")

  def rawProjectSettings: Seq[Def.Setting[_]] =
    Seq(
      pythonTarget := Keys.target.value / "python",
      pythonSource := Keys.sourceDirectory.value / "python",
      pythonManagedSource <<= Defaults.configSrcSub(pythonManagedSource),
      Keys.sourceDirectories ++= Seq(pythonSource.value, pythonManagedSource.value)
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    inConfig(Compile)(rawProjectSettings) ++
      inConfig(Test)(rawProjectSettings) ++ Seq(
      pythonBinary := pythonBinaryPath(),
      python := {
        val pythonSourceV = pythonSource.value
        val pythonSourcePath = pythonSourceV.toPath
        val pythonTargetV = pythonTarget.value.toPath

        val filesToCopy = pythonSources(pythonSourceV)

        for (fileToCopy <- filesToCopy.map(_.toPath)) {
          val relative = pythonSourcePath.relativize(fileToCopy)
          val destination = pythonTargetV.resolve(relative)
          Files.copy(fileToCopy, destination)
        }
      },
      pythonZip := {
        python.value
        val pythonTargetV = pythonTarget.value
        val pythonSourcesV = pythonTargetV.***.get
        val pythonManagedSourceV = pythonManagedSource.value
        val pythonManagedSourcesV = pythonManagedSources.value
        val destFileV = pythonTarget.value
        val loggerV = Keys.streams.value.log

        zipFiles(pythonTargetV, pythonSourcesV, pythonManagedSourceV, pythonManagedSourcesV, destFileV, loggerV)
      }
    )

  /**
    * List all the .py files in a directory.
    *
    * @param pythonSourceDir
    * @return
    */
  def pythonSources(pythonSourceDir: File): Seq[File] = {
    pythonSourceDir ** "*.py" get
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
    * @param pythonSource The python source directory. Usually src/main/python.
    * @param pythonSources The list of files to be added to the zip.
    * @param destFile The zip file to be created.
    * @param logger
    */
  def zipFiles(
    pythonSource: File,
    pythonSources: Seq[File],
    pythonManagedSource: File,
    pythonManagedSources: Seq[File],
    destFile: File,
    logger: Logger
  ): Unit = {
    import collection.convert.wrapAsScala._
    val zipFs = FileSystems.newFileSystem(destFile.toPath, null)

    /**
      * Get the path of the file in the zip file.
      */
    def relativize(pythonSourcePath: Path, pythonSource: Path): Path = {
      val zipPathParts = pythonSourcePath.relativize(pythonSource).iterator().toSeq.map(_.toString)
      zipFs.getPath(zipPathParts.head, zipPathParts.tail: _*)
    }

    for ((sourceFile, sourceRoot) <- pythonSources.zip(Stream.continually(pythonSource.toPath)) ++ pythonManagedSources.zip(Stream.continually(pythonManagedSource.toPath))) {
      val zipPythonSource = relativize(sourceRoot, pythonSource.toPath)
      logger.info(s"Copying $sourceFile to $destFile:$zipPythonSource")
      Files.copy(pythonSource.toPath, zipPythonSource)
    }

    zipFs.close()
  }

}