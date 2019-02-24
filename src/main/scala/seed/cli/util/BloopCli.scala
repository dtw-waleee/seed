package seed.cli.util

import seed.config.BuildConfig
import seed.{Log, model}
import seed.model.BuildEvent
import seed.model.Platform.{JVM, JavaScript, Native}
import seed.model.Platform

object BloopCli {
  /**
    * Check needed because Bloop clears screen
    *
    * See https://github.com/scalacenter/bloop/pull/639/files
    */
  def skipOutput(output: String): Boolean = output.contains("\u001b[H\u001b[2J")

  def bloopModuleNames(build: model.Build,
                       module: String,
                       platform: Option[Platform],
                       log: Log
                      ): List[String] =
    (module, platform) match {
      case (name, None) =>
        BuildConfig.linkTargets(build, name)
        if (build.module.isDefinedAt(name)) BuildConfig.linkTargets(build, name)
        else {
          log.error(s"Invalid module provided: $name")
          List()
        }

      case (name, Some(p)) =>
        if (build.module.isDefinedAt(name))
          List(BuildConfig.targetName(build, name, p))
        else {
          log.error(s"Invalid module provided: $name")
          List()
        }
    }

  def parseBloopModule(build: model.Build, bloopName: String): (String, Platform) =
    if (bloopName.endsWith("-js")) (bloopName.dropRight("-js".length), JavaScript)
    else if (bloopName.endsWith("-jvm")) (bloopName.dropRight("-jvm".length), JVM)
    else if (bloopName.endsWith("-native")) (bloopName.dropRight("-native".length), Native)
    else {
      require(build.module(bloopName).targets.length == 1, "Only one target expected")
      (bloopName, build.module(bloopName).targets.head)
    }

  def parseStdOut(build: model.Build)(message: String): Option[BuildEvent] = {
    val parts = message.split(" ")
    if (parts(0) == "Compiling") {
      val (module, platform) = parseBloopModule(build, parts(1))
      Some(BuildEvent.Compiling(module, platform))
    } else if (parts(0) == "Compiled") {
      val (module, platform) = parseBloopModule(build, parts(1))
      Some(BuildEvent.Compiled(module, platform))
    } else if (parts(0) == "Generated") {
      val offset = message.indexOf('\'')
      val path = message.substring(offset + 1).init
      Some(BuildEvent.Linked(path))
    } else if (message.endsWith("failed to compile.")) {
      val offset = message.indexOf('\'')
      val offsetEnd = message.indexOf('\'', offset + 1)
      val module = message.substring(offset + 1, offsetEnd)
      Some(BuildEvent.Failed(module))
    } else None
  }
}
