package com.rocketfuel.sbt.python.pylint

import com.rocketfuel.sbt.python.PythonPlugin
import sbt._

object PylintPlugin extends AutoPlugin {

  val pylint = taskKey[Unit]("Run pylint on the files in pythonSource.")

  val pylintProcessBuilder = taskKey[ProcessBuilder]("")

  val pylintGenerateRc = taskKey[Unit]("Create a default pylint.rc.")

  val pylintBinary = settingKey[File]("The pylint binary to use.")

  val pylintRc = settingKey[File]("The location of the pylint configuration file.")

  val pylintFormat = settingKey[ReportFormat]("The report format for pylint.")

  val pylintFailOnError = settingKey[Boolean]("Determines whether or not pylint failure causes task failure.")

  val pylintTarget = settingKey[File]("The output file from pylint.")

  def rawProjectSettings: Seq[Def.Setting[_]] =
    Seq(
      pylintTarget := {
        val config = Keys.configuration.value.name
        val configPart =
          if (config == Compile.name) ""
          else s"_$config"
        Keys.target.value / (s"pylint${configPart}_report." + pylintFormat.value.fileExtension)
      },
      pylintFailOnError := true,
      pylintProcessBuilder := {
        Actions.pylintBuildProcess(
          baseDirectory = PythonPlugin.pythonClassDirectory.value,
          pylintBinary = pylintBinary.value,
          pylintRc = pylintRc.value,
          pylintFormat = pylintFormat.value,
          pylintTarget = pylintTarget.value
        )
      },
      pylint := {
        Actions.pylint(
          pylintProcess = pylintProcessBuilder.value,
          pylintFailOnError = pylintFailOnError.value,
          logger = Keys.streams.value.log
        )
      },
      pylint <<= pylint.dependsOn(PythonPlugin.python)
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    inConfig(Compile)(rawProjectSettings) ++
      inConfig(Test)(rawProjectSettings) ++ Seq(
      pylintBinary := Actions.pylintBinary(),
      pylintRc := Keys.baseDirectory.value / "pylint.rc",
      pylintFormat := ReportFormat.Text,
      pylintGenerateRc :=
        Actions.generateRcFile(pylintBinary.value, pylintRc.value)
    )

  override def requires = PythonPlugin

  object Actions {
    def pylintBuildProcess(
      baseDirectory: File,
      pylintBinary: File,
      pylintRc: File,
      pylintFormat: ReportFormat,
      pylintTarget: File
    ): ProcessBuilder = {
      val basePath = baseDirectory.toPath
      val files =
        for (file <- baseDirectory.***.get) yield {
          basePath.relativize(file.toPath).toString
        }

      val rcArgs =
        if (pylintRc.exists()) Seq("--rcfile", pylintRc.getAbsolutePath)
        else Seq.empty

      Process((Seq(pylintBinary.getAbsolutePath, "-f", pylintFormat.value) ++ rcArgs ++ files).filter(_.nonEmpty), baseDirectory) #> pylintTarget
    }

    def pylint(
      pylintProcess: ProcessBuilder,
      pylintFailOnError: Boolean,
      logger: Logger
    ): Unit = {
      logger.debug(s"executing $pylintProcess")

      val p = pylintProcess.run()
      val exitValue = p.exitValue()

      if (exitValue != 0) {
        val message = s"pylint failed with exit code $exitValue."
        if (pylintFailOnError) sys.error(message)
        else logger.error(message)
      }
    }

    def generateRcFile(
      pylintBinary: File,
      pylintRc: File
    ): Process = {
      Process(Seq[String](pylintBinary.getAbsolutePath, "--generate-rcfile")) #> pylintRc run()
    }

    def pylintBinary(): File = {
      val searchCommand =
        if (System.getProperty("os.name").startsWith("Windows")) Seq("where", "pylint")
        else Seq("which", "pylint")
      file(Process(searchCommand).lines.head)
    }
  }

}
