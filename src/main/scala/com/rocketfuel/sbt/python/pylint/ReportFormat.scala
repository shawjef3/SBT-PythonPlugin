package com.rocketfuel.sbt.python.pylint

sealed class ReportFormat(
  val value: String,
  val fileExtension: String
)

object ReportFormat {

  val default = Text

  case object Text extends ReportFormat("text", "txt")

  case object Html extends ReportFormat("html", "html")

  case class Reporter(value: String, fileExtension: String)

}
