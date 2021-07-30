package org.enso.launcher.cli

import java.nio.file.Path
import java.util.UUID

import akka.http.scaladsl.model.Uri
import cats.data.NonEmptyList
import cats.implicits._
import nl.gn0s1s.bump.SemVer
import org.enso.cli._
import org.enso.cli.arguments.Opts.implicits._
import org.enso.cli.arguments._
import org.enso.launcher.cli.LauncherColorMode.argument
import org.enso.runtimeversionmanager.cli.Arguments._
import org.enso.runtimeversionmanager.config.DefaultVersion
import org.enso.runtimeversionmanager.runner.LanguageServerOptions
import org.enso.launcher.distribution.DefaultManagers._
import org.enso.launcher.installation.DistributionInstaller
import org.enso.launcher.installation.DistributionInstaller.BundleAction
import org.enso.launcher.upgrade.LauncherUpgrader
import org.enso.launcher.{cli, Launcher}
import org.enso.loggingservice.{ColorMode, LogLevel}

/** Defines the CLI commands and options for the program.
  *
  * Each command is parametrized with a config that describes global CLI options
  * set at the top-level and returns an integer which determines the programs
  * exit code.
  */
object LauncherApplication {
  type Config = GlobalCLIOptions

  private def versionCommand: Command[Config => Int] =
    Command(
      "version",
      "Print version of the launcher and currently selected Enso distribution."
    ) {
      val onlyLauncherFlag = Opts.flag(
        "only-launcher",
        "If set, shows only the launcher version, skipping checking for " +
        "engine versions which may involve network requests depending on " +
        "configuration.",
        showInUsage = true
      )
      onlyLauncherFlag map { onlyLauncher => (config: Config) =>
        Launcher(config).displayVersion(
          hideEngineVersion = onlyLauncher
        )
      }
    }

  private def newCommand: Command[Config => Int] =
    Command("new", "Create a new Enso project.", related = Seq("create")) {
      val nameOpt = Opts.positionalArgument[String]("PROJECT-NAME")
      val pathOpt = Opts.optionalArgument[Path](
        "PATH",
        "PATH specifies where to create the project. If it is not specified, " +
        "a directory called PROJECT-NAME is created in the current directory."
      )
      def projectTemplate = {
        Opts.optionalParameter[String](
          "new-project-template",
          "TEMPLATE-NAME",
          "Specifies a project template when creating a project."
        )
      }
      val additionalArgs = Opts.additionalArguments()

      (
        nameOpt,
        pathOpt,
        projectTemplate,
        versionOverride,
        systemJVMOverride,
        jvmOpts,
        additionalArgs
      ) mapN {
        (
          name,
          path,
          template,
          versionOverride,
          systemJVMOverride,
          jvmOpts,
          additionalArgs
        ) => (config: Config) =>
          Launcher(config).newProject(
            name                = name,
            path                = path,
            projectTemplate     = template,
            versionOverride     = versionOverride,
            useSystemJVM        = systemJVMOverride,
            jvmOpts             = jvmOpts,
            additionalArguments = additionalArgs
          )
      }
    }

  private def jvmOpts =
    Opts.prefixedParameters(
      "jvm",
      "These parameters will be passed to the launched JVM as -DKEY=VALUE."
    )
  private def systemJVMOverride =
    Opts.flag(
      "use-system-jvm",
      "Setting this flag runs the Enso engine using the system-configured " +
      "JVM instead of the one managed by the launcher. " +
      "Advanced option, use carefully.",
      showInUsage = false
    )
  private def versionOverride = {
    Opts.optionalParameter[SemVer](
      "use-enso-version",
      "VERSION",
      "Override the Enso version that would normally be used."
    )
  }
  private def engineLogLevel = {
    Opts
      .optionalParameter[LogLevel](
        "log-level",
        "(error | warning | info | debug | trace)",
        "Sets logging verbosity for the engine. Defaults to info."
      )
      .withDefault(LogLevel.Info)
  }

  private def runCommand: Command[Config => Int] =
    Command(
      "run",
      "Run an Enso project or script. " +
      "If `auto-confirm` is set, this will install missing engines or " +
      "runtimes without asking.",
      related = Seq("exec", "execute", "build")
    ) {
      val pathOpt = Opts.optionalArgument[Path](
        "PATH",
        "If PATH points to a file, that file is run as a script (if that " +
        "script is located inside of a project, the script is run in the " +
        "context of that project). If it points to a directory, the project " +
        "from that directory is run. If a PATH is not provided, a project in " +
        "the current working directory is run."
      )
      val additionalArgs = Opts.additionalArguments()
      (
        pathOpt,
        versionOverride,
        engineLogLevel,
        systemJVMOverride,
        jvmOpts,
        additionalArgs
      ) mapN {
        (
          path,
          versionOverride,
          engineLogLevel,
          systemJVMOverride,
          jvmOpts,
          additionalArgs
        ) => (config: Config) =>
          Launcher(config).runRun(
            path                = path,
            versionOverride     = versionOverride,
            useSystemJVM        = systemJVMOverride,
            jvmOpts             = jvmOpts,
            additionalArguments = additionalArgs,
            logLevel            = engineLogLevel
          )
      }
    }

  private def languageServerCommand: Command[Config => Int] =
    Command(
      "language-server",
      "Launch the Language Server for a given project. " +
      "If `auto-confirm` is set, this will install missing engines or " +
      "runtimes without asking.",
      related = Seq("server")
    ) {
      val rootId = Opts.parameter[UUID]("root-id", "UUID", "Content root id.")
      val path =
        Opts.parameter[Path]("path", "PATH", "Path to the content root.")
      val interface =
        Opts
          .optionalParameter[String](
            "interface",
            "INTERFACE",
            "Interface for processing all incoming connections. " +
            "Defaults to `127.0.0.1`."
          )
          .withDefault("127.0.0.1")
      val rpcPort =
        Opts
          .optionalParameter[Int](
            "rpc-port",
            "PORT",
            "RPC port for processing all incoming connections. Defaults to 8080."
          )
          .withDefault(8080)
      val dataPort =
        Opts
          .optionalParameter[Int](
            "data-port",
            "PORT",
            "Data port for visualisation protocol. Defaults to 8081."
          )
          .withDefault(8081)
      val additionalArgs = Opts.additionalArguments()
      (
        rootId,
        path,
        interface,
        rpcPort,
        dataPort,
        versionOverride,
        engineLogLevel,
        systemJVMOverride,
        jvmOpts,
        additionalArgs
      ) mapN {
        (
          rootId,
          path,
          interface,
          rpcPort,
          dataPort,
          versionOverride,
          engineLogLevel,
          systemJVMOverride,
          jvmOpts,
          additionalArgs
        ) => (config: Config) =>
          Launcher(config).runLanguageServer(
            options = LanguageServerOptions(
              rootId    = rootId,
              interface = interface,
              rpcPort   = rpcPort,
              dataPort  = dataPort
            ),
            contentRoot         = path,
            versionOverride     = versionOverride,
            useSystemJVM        = systemJVMOverride,
            jvmOpts             = jvmOpts,
            additionalArguments = additionalArgs,
            logLevel            = engineLogLevel,
            logMasking          = config.internalOptions.logMasking
          )
      }
    }

  private def replCommand: Command[Config => Int] =
    Command(
      "repl",
      "Launch an Enso REPL. " +
      "If `auto-confirm` is set, this will install missing engines or " +
      "runtimes without asking."
    ) {
      val path = Opts.optionalParameter[Path](
        "path",
        "PATH",
        "Specifying this option runs the REPL in context of a project " +
        "located at the given path. The REPL is also run in context of a " +
        "project if it is launched from within a directory inside a project."
      )
      val additionalArgs = Opts.additionalArguments()
      (
        path,
        versionOverride,
        engineLogLevel,
        systemJVMOverride,
        jvmOpts,
        additionalArgs
      ) mapN {
        (
          path,
          versionOverride,
          engineLogLevel,
          systemJVMOverride,
          jvmOpts,
          additionalArgs
        ) => (config: Config) =>
          Launcher(config).runRepl(
            projectPath         = path,
            versionOverride     = versionOverride,
            useSystemJVM        = systemJVMOverride,
            jvmOpts             = jvmOpts,
            additionalArguments = additionalArgs,
            logLevel            = engineLogLevel
          )
      }
    }

  private def defaultCommand: Command[Config => Int] =
    Command("default", "Print or change the default Enso version.") {
      val version = Opts.optionalArgument[DefaultVersion](
        "VERSION",
        "If provided, sets default version to VERSION. " +
        "Otherwise, current default is displayed. VERSION can be an Enso " +
        "version string or `latest-installed`."
      )
      version map { version => (config: Config) =>
        val launcher = Launcher(config)
        version match {
          case Some(version) =>
            launcher.setDefaultVersion(version)
          case None =>
            launcher.printDefaultVersion()
        }
      }
    }

  private def upgradeCommand: Command[Config => Int] =
    Command("upgrade", "Upgrade the launcher.") {
      val version = Opts.optionalArgument[SemVer](
        "VERSION",
        "VERSION specifies which launcher version to upgrade to. " +
        "If not provided, defaults to latest version."
      )
      version map { version => (config: Config) =>
        Launcher(config).upgrade(version)
      }
    }

  private def installEngineCommand: Command[Config => Int] =
    Command(
      "engine",
      "Install the specified engine VERSION, defaulting to the latest if " +
      "unspecified."
    ) {
      val version = Opts.optionalArgument[SemVer]("VERSION")
      version map { version => (config: Config) =>
        temporaryDirectoryManager.tryCleaningTemporaryDirectory()
        version match {
          case Some(value) =>
            Launcher(config).installEngine(value)
          case None =>
            Launcher(config).installLatestEngine()
        }
      }
    }

  private def installDistributionCommand: Command[Config => Int] =
    Command(
      "distribution",
      "Install Enso on the system, deactivating portable mode."
    ) {

      implicit val bundleActionParser: Argument[BundleAction] = {
        case "move"   => DistributionInstaller.MoveBundles.asRight
        case "copy"   => DistributionInstaller.CopyBundles.asRight
        case "ignore" => DistributionInstaller.IgnoreBundles.asRight
        case other =>
          OptsParseError.left(
            s"`$other` is not a valid bundle-install-mode value. " +
            s"Possible values are: `move`, `copy`, `ignore`."
          )
      }
      val bundleAction = Opts.optionalParameter[BundleAction](
        "bundle-install-mode",
        "(move | copy | ignore)",
        "Specifies how bundled engines and runtimes should be treated. " +
        "If `auto-confirm` is set, defaults to move.",
        showInUsage = false
      )

      val doNotRemoveOldLauncher = Opts.flag(
        "no-remove-old-launcher",
        "If `auto-confirm` is set, the default behavior is to remove the old " +
        "launcher after installing the distribution. Setting this flag may " +
        "override this behavior to keep the original launcher. Applies only " +
        "if `auto-confirm` is set.",
        showInUsage = true
      )

      (bundleAction, doNotRemoveOldLauncher) mapN {
        (bundleAction, doNotRemoveOldLauncher) => (config: Config) =>
          Launcher(config).installDistribution(
            doNotRemoveOldLauncher,
            bundleAction
          )
      }
    }

  private def installCommand: Command[Config => Int] =
    Command(
      "install",
      "Install a new version of engine or install the distribution locally."
    ) {
      Opts.subcommands(installEngineCommand, installDistributionCommand)
    }

  private def uninstallEngineCommand: Command[Config => Int] =
    Command(
      "engine",
      "Uninstall the provided engine version. If the corresponding runtime " +
      "is not used by any remaining engine installations, it is also removed."
    ) {
      val version = Opts.positionalArgument[SemVer]("VERSION")
      version map { version => (config: Config) =>
        Launcher(config).uninstallEngine(version)
      }
    }

  private def uninstallDistributionCommand: Command[Config => Int] =
    Command(
      "distribution",
      "Uninstall whole Enso distribution and all components managed by " +
      "it. If `auto-confirm` is set, it will not attempt to remove the " +
      "ENSO_DATA_DIRECTORY and ENSO_CONFIG_DIRECTORY if they contain any " +
      "unexpected files."
    ) {
      Opts.pure(()) map { (_: Unit) => (config: Config) =>
        temporaryDirectoryManager.tryCleaningTemporaryDirectory()
        Launcher(config).uninstallDistribution()
      }
    }

  private def uninstallCommand: Command[Config => Int] =
    Command(
      "uninstall",
      "Uninstall an Enso component."
    ) {
      Opts.subcommands(uninstallEngineCommand, uninstallDistributionCommand)
    }

  private def listCommand: Command[Config => Int] =
    Command("list", "List installed components.") {
      sealed trait Components
      case object EnsoComponents    extends Components
      case object RuntimeComponents extends Components
      implicit val argumentComponent: Argument[Components] = {
        case "engine"  => EnsoComponents.asRight
        case "runtime" => RuntimeComponents.asRight
        case other =>
          OptsParseError.left(
            s"Unknown argument `$other` - expected `engine`, `runtime` " +
            "or no argument to print a general summary."
          )
      }

      val what = Opts.optionalArgument[Components](
        "COMPONENT",
        "COMPONENT can be either `engine`, `runtime` or none. " +
        "If not specified, prints a summary of all installed components."
      )
      what map { what => (config: Config) =>
        what match {
          case Some(EnsoComponents)    => Launcher(config).listEngines()
          case Some(RuntimeComponents) => Launcher(config).listRuntimes()
          case None                    => Launcher(config).listSummary()
        }
      }
    }

  private def configCommand: Command[Config => Int] =
    Command("config", "Modify global user configuration.") {
      val key = Opts.positionalArgument[String](
        "KEY",
        "Setting KEYs `author.name` and `author.email` can be used to set a" +
        " default author and maintainer for newly created projects."
      )
      val value = Opts.optionalArgument[String](
        "VALUE",
        "Setting VALUE to an empty string removes the key from the " +
        "configuration. When a VALUE is not provided, current configured " +
        "value is printed."
      )
      (key, value) mapN { (key, value) => (config: Config) =>
        value match {
          case Some(value) => Launcher(config).updateConfig(key, value)
          case None        => Launcher(config).printConfig(key)
        }
      }
    }

  private def helpCommand: Command[Config => Int] =
    Command("help", "Display summary of available commands.") {
      Opts.pure(()) map { _ => (_: Config) => printTopLevelHelp(); 0 }
    }

  private def topLevelOpts: Opts[() => TopLevelBehavior[Config]] = {
    val version =
      Opts.flag("version", 'V', "Display version.", showInUsage = true)
    val json = Opts.flag(
      GlobalCLIOptions.USE_JSON,
      "Use JSON instead of plain text for version output.",
      showInUsage = false
    )
    val ensurePortable = Opts.flag(
      "ensure-portable",
      "Ensures that the launcher is run in portable mode.",
      showInUsage = false
    )
    val autoConfirm = Opts.flag(
      GlobalCLIOptions.AUTO_CONFIRM,
      "Proceeds without asking confirmation questions. Please see the " +
      "options for the specific subcommand you want to run for the defaults " +
      "used by this option.",
      showInUsage = false
    )
    val hideProgress = Opts.flag(
      GlobalCLIOptions.HIDE_PROGRESS,
      "Suppresses displaying progress bars for downloads and other long " +
      "running actions. May be needed if program output is piped.",
      showInUsage = false
    )
    val logLevel = Opts.optionalParameter[LogLevel](
      GlobalCLIOptions.LOG_LEVEL,
      "(error | warning | info | debug | trace)",
      "Sets logging verbosity for the launcher. If not provided, defaults to" +
      s"${LauncherLogging.defaultLogLevel}."
    )
    val connectLogger = Opts
      .optionalParameter[Uri](
        GlobalCLIOptions.CONNECT_LOGGER,
        "URI",
        "Instead of starting its own logging service, " +
        "connects to the logging service at the provided URI."
      )
      .hidden
    val noLogMasking = Opts.flag(
      GlobalCLIOptions.NO_LOG_MASKING,
      "Disable masking of personally identifiable information in logs. " +
      "Masking can be also disabled with the `NO_LOG_MASKING` environment " +
      "variable.",
      showInUsage = false
    )
    val colorMode =
      Opts
        .aliasedOptionalParameter[ColorMode](
          GlobalCLIOptions.COLOR_MODE,
          "colour",
          "colors"
        )(
          "(auto | yes | always | no | never)",
          "Specifies if colors should be used in the output, defaults to auto."
        )
        .withDefault(ColorMode.Auto)

    val internalOpts = InternalOpts.topLevelOptions

    (
      internalOpts,
      version,
      json,
      ensurePortable,
      autoConfirm,
      hideProgress,
      logLevel,
      connectLogger,
      noLogMasking,
      colorMode
    ) mapN {
      (
        internalOptsCallback,
        version,
        useJSON,
        shouldEnsurePortable,
        autoConfirm,
        hideProgress,
        logLevel,
        connectLogger,
        disableLogMasking,
        colorMode
      ) => () =>
        if (shouldEnsurePortable) {
          Launcher.ensurePortable()
        }

        val globalCLIOptions = cli.GlobalCLIOptions(
          autoConfirm  = autoConfirm,
          hideProgress = hideProgress,
          useJSON      = useJSON,
          colorMode    = colorMode,
          internalOptions = GlobalCLIOptions.InternalOptions(
            logLevel,
            connectLogger,
            disableLogMasking
          )
        )

        internalOptsCallback(globalCLIOptions)
        LauncherUpgrader.setCLIOptions(globalCLIOptions)
        LauncherLogging.setup(
          logLevel,
          connectLogger,
          globalCLIOptions.colorMode,
          !disableLogMasking
        )
        initializeApp()

        if (version) {
          Launcher(globalCLIOptions).displayVersion(useJSON)
          TopLevelBehavior.Halt(0)
        } else
          TopLevelBehavior.Continue(globalCLIOptions)
    }
  }

  /** Application initializer that is run after handling of the internal
    * options.
    */
  private def initializeApp(): Unit = {
    // Note [Main Lock Initialization]
    defaultResourceManager.initializeMainLock()
  }

  val commands: NonEmptyList[Command[Config => Int]] = NonEmptyList
    .of(
      versionCommand,
      helpCommand,
      newCommand,
      replCommand,
      runCommand,
      languageServerCommand,
      defaultCommand,
      installCommand,
      uninstallCommand,
      upgradeCommand,
      listCommand,
      configCommand
    )

  val application: Application[Config] =
    Application(
      "enso",
      "Enso",
      "Enso Launcher",
      topLevelOpts,
      commands,
      PluginManager
    )

  private def printTopLevelHelp(): Unit = {
    CLIOutput.println(application.renderHelp())
  }
}

/* Note [Main Lock Initialization]
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * The main program lock is used by the distribution installer/uninstaller to
 * ensure that no other launcher instances are running when the distribution is
 * being installed or uninstalled.
 *
 * That lock should be acquired (in shared mode) as soon as possible, but it
 * must be acquired *after* handling the internal options. That is because,
 * acquiring any locks will initialize the DistributionManager's paths, but in
 * test-mode, the internal options may need to override the
 * DistributionManager's executable path and that must be done before their
 * initialization for it to take effect.
 */
