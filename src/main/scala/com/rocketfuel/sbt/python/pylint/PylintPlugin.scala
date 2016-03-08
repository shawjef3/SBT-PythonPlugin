package com.rocketfuel.sbt.python.pylint

import com.rocketfuel.sbt.python.PythonPlugin
import sbt._

object PylintPlugin extends AutoPlugin {

  val pylint = TaskKey[Unit]("Run pylint on the files in pythonSource.")

  val pylintGenerateRc = TaskKey[Unit]("Create a default pylint.rc.")

  val pylintBinary = SettingKey[File]("The pylint binary to use.")

  val pylintRc = SettingKey[File]("The location of the pylint configuration file.")

  val pylintFormat = SettingKey[ReportFormat]("The report format for pylint.")

  val pylintFailOnError = SettingKey[Boolean]("Determines whether or not pylint failure causes task failure.")

  val pylintTarget = SettingKey[File]("The output file from pylint.")

  def rawProjectSettings: Seq[Def.Setting[_]] =
    Seq(
      pylintFailOnError := true,
      pylint := {
        PythonPlugin.python.value
        Actions.pylint(
          baseDirectory = PythonPlugin.pythonClasses.value,
          pylintBinary = pylintBinary.value,
          pylintRc = pylintRc.value,
          pylintFormat = pylintFormat.value,
          pylintTarget = pylintTarget.value,
          pylintFailOnError = pylintFailOnError.value,
          logger = Keys.streams.value.log
        )
      }

    )

  override def projectSettings: Seq[Def.Setting[_]] =
    inConfig(Compile)(rawProjectSettings) ++
      inConfig(Test)(rawProjectSettings) ++ Seq(
      pylintBinary := Actions.pylintBinary(),
      pylintRc := Keys.baseDirectory.value / "pylint.rc",
      pylintFormat := ReportFormat.Text,
      pylintGenerateRc :=
        Actions.generateRcFile(Keys.baseDirectory.value, pylintBinary.value, pylintRc.value),
      pylintTarget in Compile := Keys.target.value / ("pylint_report." + pylintFormat.value.fileExtension),
      pylintTarget in Test := Keys.target.value / ("pylint_test_report." + pylintFormat.value.fileExtension)
    )

  object Actions {
    def pylint(
      baseDirectory: File,
      pylintBinary: File,
      pylintRc: File,
      pylintFormat: ReportFormat,
      pylintTarget: File,
      pylintFailOnError: Boolean,
      logger: Logger
    ): Unit = {
      val basePath = baseDirectory.toPath
      val files =
        for (file <- baseDirectory.***.get) yield {
          basePath.relativize(file.toPath).toString
        }

      val p = Process(Seq[String](pylintBinary.getAbsolutePath, "--rcfile", pylintRc.getAbsolutePath, "-f", pylintFormat.value) ++ files) #> pylintTarget run()

      p.exitValue() match {
        case 0 =>
        case n =>
          val message = s"pylint failed with exit code $n."
          if (pylintFailOnError) sys.error(message)
          else logger.error(message)
      }
    }

    def generateRcFile(
      baseDirectory: File,
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
