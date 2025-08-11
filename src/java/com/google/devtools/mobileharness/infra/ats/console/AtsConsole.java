/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.infra.ats.console;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.shell.ShellUtils.tokenize;
import static java.util.Arrays.asList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.common.DeviceInfraServiceUtil;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientId;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.BuiltinOlcServerFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleOutput;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.MainArgs;
import com.google.devtools.mobileharness.infra.ats.console.command.RootCommand;
import com.google.devtools.mobileharness.infra.ats.console.command.completer.CommandCompleter;
import com.google.devtools.mobileharness.infra.ats.console.command.completer.CommandCompleterHolder;
import com.google.devtools.mobileharness.infra.ats.console.command.preprocessor.CommandPreprocessor;
import com.google.devtools.mobileharness.infra.ats.console.command.preprocessor.CommandPreprocessor.PreprocessingResult;
import com.google.devtools.mobileharness.infra.ats.console.constant.AtsConsoleDirs;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.ServerLogPrinter;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.console.InterruptibleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.util.log.LogDumper;
import com.google.devtools.mobileharness.infra.ats.console.util.log.LogRecordPrinter;
import com.google.devtools.mobileharness.infra.ats.console.util.notice.NoticeMessageUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.version.VersionMessageUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.OlcServerModuleFactory;
import com.google.devtools.mobileharness.infra.client.longrunningservice.OlcServerRunner;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.inject.CommonModule;
import com.google.devtools.mobileharness.shared.util.logging.flogger.FloggerFormatter;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import com.google.devtools.mobileharness.shared.util.system.SystemInfoPrinter;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import com.google.wireless.qa.mobileharness.shared.constant.DirPreparer;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

/** ATS Console. */
public class AtsConsole {

  static {
    FloggerFormatter.initialize();
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String HELP_PATTERN = "h|help";

  private static final String USE_NEW_OLC_SERVER_ENV_VAR = "USE_NEW_OLC_SERVER";
  private static final String USE_TF_RETRY_ENV_VAR = "USE_TF_RETRY";
  private static final String EMBEDDED_MODE_ENV_VAR = "OLC_SERVER_EMBEDDED_MODE";

  private static final boolean DEFAULT_EMBEDDED_MODE = false;

  private static final String OLC_SERVER_CLASS_PATH = "ats_olc_server_local_mode_deploy.jar";
  private static final String OLC_SERVER_MODULE_FACTORY_IMPL_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.client.longrunningservice.OlcServerModuleFactoryImpl";

  public static void main(String[] args)
      throws MobileHarnessException, IOException, InterruptedException {
    // Gets system properties.
    ImmutableMap<String, String> systemProperties =
        System.getProperties().entrySet().stream()
            .collect(toImmutableMap(e -> (String) e.getKey(), e -> (String) e.getValue()));

    // Parses flags.
    FlagsString deviceInfraServiceFlags =
        DeviceInfraServiceUtil.getDeviceInfraServiceFlagsFromSystemProperty();
    FlagsString finalDeviceInfraServiceFlags =
        preprocessDeviceInfraServiceFlags(deviceInfraServiceFlags);
    DeviceInfraServiceUtil.parseFlags(finalDeviceInfraServiceFlags.flags());

    // Initializes line reader.
    LineReader lineReader = createLineReader();

    // Creates modules.
    List<Module> modules = new ArrayList<>();
    modules.add(
        new AtsConsoleModule(
            "ats-console-" + UUID.randomUUID(),
            finalDeviceInfraServiceFlags,
            asList(args),
            systemProperties,
            lineReader,
            System.out,
            System.err,
            resultFuture -> {},
            /* parseCommandOnly= */ false));
    modules.add(new CommonModule());

    // Creates OLC server module for embedded mode.
    createOlcServerModuleFactory()
        .ifPresent(
            olcServerModuleFactory -> {
              Instant serverStartTime = Instant.now();
              Module olcServerModule =
                  olcServerModuleFactory.create(
                      Flags.instance().enableAtsMode.getNonNull(),
                      serverStartTime,
                      /* enableCloudPubsubMonitoring= */ false,
                      /* enableDatabase= */ false,
                      /* enableGrpcRelay= */ false);
              modules.add(olcServerModule);
            });

    // Creates Guice injector.
    Injector injector = Guice.createInjector(modules);

    // Creates ATS console.
    AtsConsole atsConsole = injector.getInstance(AtsConsole.class);
    atsConsole.injector = injector;

    // Prepares dirs.
    DirPreparer.prepareDirRoots();

    // Initializes logger.
    MobileHarnessLogger.init(
        AtsConsoleDirs.getLogDir(),
        ImmutableList.of(atsConsole.consoleUtil.getLogHandler()),
        /* disableConsoleHandler= */ true);

    // Initializes log recorder.
    LogRecorder.getInstance().initialize(atsConsole.logRecordPrinter::printLogRecord);

    // Adds shutdown hook.
    Runtime.getRuntime()
        .addShutdownHook(new Thread(atsConsole::onShutdown, "ats-console-shutdown-hook"));

    // Starts ATS console.
    try (NonThrowingAutoCloseable ignored = threadRenaming("ats-console-main-thread")) {
      atsConsole.run();
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      atsConsole.consoleUtil.printlnStderr(
          "Console received an unexpected exception (shown below); shutting down ATS Console.\n%s",
          Throwables.getStackTraceAsString(e));
      System.exit(1);
    }
  }

  private final ImmutableList<String> mainArgs;
  private final FlagsString deviceInfraServiceFlags;
  private final LineReader lineReader;
  private final InterruptibleLineReader interruptibleLineReader;
  private final PrintWriter outWriter;
  private final PrintWriter errWriter;
  private final ControlStub controlStub;
  private final String clientId;
  private final Sleeper sleeper;
  private final ConsoleUtil consoleUtil;
  private final ConsoleInfo consoleInfo;
  private final LogRecordPrinter logRecordPrinter;
  private final ServerPreparer serverPreparer;
  private final ServerLogPrinter serverLogPrinter;
  private final SystemInfoPrinter systemInfoPrinter;
  private final VersionMessageUtil versionMessageUtil;
  private final CommandCompleter commandCompleter;
  private final CommandPreprocessor commandPreprocessor;
  private final OlcServerRunner olcServerRunner;

  /** Set before {@link #run}; */
  @VisibleForTesting public volatile Injector injector;

  @Inject
  AtsConsole(
      @MainArgs ImmutableList<String> mainArgs,
      @DeviceInfraServiceFlags FlagsString deviceInfraServiceFlags,
      @ConsoleLineReader LineReader lineReader,
      InterruptibleLineReader interruptibleLineReader,
      @ConsoleOutput(ConsoleOutput.Type.OUT_WRITER) PrintWriter outWriter,
      @ConsoleOutput(ConsoleOutput.Type.ERR_WRITER) PrintWriter errWriter,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) ControlStub controlStub,
      @ClientId String clientId,
      Sleeper sleeper,
      ConsoleUtil consoleUtil,
      ConsoleInfo consoleInfo,
      LogRecordPrinter logRecordPrinter,
      ServerPreparer serverPreparer,
      ServerLogPrinter serverLogPrinter,
      SystemInfoPrinter systemInfoPrinter,
      VersionMessageUtil versionMessageUtil,
      CommandCompleter commandCompleter,
      CommandPreprocessor commandPreprocessor,
      Optional<OlcServerRunner> olcServerRunner) {
    this.mainArgs = mainArgs;
    this.deviceInfraServiceFlags = deviceInfraServiceFlags;
    this.lineReader = lineReader;
    this.interruptibleLineReader = interruptibleLineReader;
    this.outWriter = outWriter;
    this.errWriter = errWriter;
    this.controlStub = controlStub;
    this.clientId = clientId;
    this.sleeper = sleeper;
    this.consoleUtil = consoleUtil;
    this.consoleInfo = consoleInfo;
    this.logRecordPrinter = logRecordPrinter;
    this.serverPreparer = serverPreparer;
    this.serverLogPrinter = serverLogPrinter;
    this.systemInfoPrinter = systemInfoPrinter;
    this.versionMessageUtil = versionMessageUtil;
    this.commandCompleter = commandCompleter;
    this.commandPreprocessor = commandPreprocessor;
    this.olcServerRunner = olcServerRunner.orElse(null);
  }

  public void run() throws MobileHarnessException, InterruptedException {
    // Logs flags.
    logger.atInfo().with(IMPORTANCE, DEBUG).log("ATS console ID: %s", clientId);
    logger.atInfo().with(IMPORTANCE, DEBUG).log("Flags: %s", deviceInfraServiceFlags.flags());
    systemInfoPrinter.printSystemInfo(DEBUG);

    // Prints notice message.
    consoleUtil.printlnStdout(NoticeMessageUtil.getNoticeMessage());

    // Prints version information.
    consoleUtil.printlnStdout(versionMessageUtil.getVersionMessage());

    // Prints help information.
    consoleUtil.printlnStdout(
        "Use \"help\" to get more information on running commands. Use \"dump bugreport\" to"
            + " generate a debug info file. Log dirs: %s",
        LogDumper.getLogDirs());

    // Prints arguments.
    if (!mainArgs.isEmpty()) {
      consoleUtil.printlnStderr("Args: %s", mainArgs);
    }

    // Starts listing test plans.
    CommandCompleterHolder.getInstance().initialize(commandCompleter);
    commandCompleter.startListingTestPlans();

    if (Flags.instance().atsConsoleOlcServerEmbeddedMode.getNonNull()) {
      // Runs embedded mode OLC server.
      olcServerRunner.runBasic();
    } else {
      // Prepares OLC server.
      if (Flags.instance().enableAtsConsoleOlcServer.getNonNull()) {
        serverPreparer.prepareOlcServer();
        serverPreparer.startSendingHeartbeats();
      }

      // Prints OLC server streaming log.
      if (Flags.instance().enableAtsConsoleOlcServerLog.getNonNull()) {
        serverLogPrinter.enable(true);
      }
    }

    // Starts to read input from console.
    ImmutableList<String> args = mainArgs;
    while (true) {
      // Reads a command.
      ImmutableList<String> tokens;
      if (args.isEmpty()) {
        Optional<String> line = interruptibleLineReader.readLine();
        if (line.isEmpty()) {
          consoleUtil.printlnStderr("Input interrupted; quitting...");
          break;
        }
        try {
          tokens = tokenize(line.get());
        } catch (TokenizationException e) {
          consoleUtil.printlnStderr("Invalid input: %s", line.get());
          continue;
        }
        if (tokens.isEmpty()) {
          continue;
        }
      } else {
        consoleUtil.printlnStdout("Using commandline arguments as starting command: %s", args);
        lineReader.getHistory().add(Joiner.on(" ").join(args));
        tokens = args;
        args = ImmutableList.of();
        if (tokens.get(0).matches(HELP_PATTERN)) {
          // If starts from command line with args "--help", "-h", returns to shell.
          interruptibleLineReader.interrupt();
        }
      }

      // Preprocesses the command.
      ImmutableList<ImmutableList<String>> preprocessedCommands;
      PreprocessingResult preprocessingResult = commandPreprocessor.preprocess(tokens);
      if (preprocessingResult.errorMessage().isPresent()) {
        consoleUtil.printlnStderr(preprocessingResult.errorMessage().get());
        continue;
      }
      if (preprocessingResult.modifiedCommands().isPresent()) {
        preprocessedCommands = preprocessingResult.modifiedCommands().get();
        logger
            .atInfo()
            .with(IMPORTANCE, IMPORTANT)
            .log("Replace command %s by %s", tokens, preprocessedCommands);
      } else {
        preprocessedCommands = ImmutableList.of(tokens);
      }

      // Executes the commands.
      for (ImmutableList<String> command : preprocessedCommands) {
        // Have a new CommandLine instance to work around the issue of ArgGroup not
        // reset(b/332503867)
        CommandLine commandLine = createCommandLine();
        consoleInfo.setLastCommand(command);
        commandLine.execute(command.toArray(new String[0]));
        sleeper.sleep(Duration.ofMillis(100L));
      }
    }
  }

  private CommandLine createCommandLine() {
    return new CommandLine(RootCommand.class, new GuiceFactory(injector))
        .setCaseInsensitiveEnumValuesAllowed(true)
        .setOut(outWriter)
        .setErr(errWriter)
        .setUnmatchedOptionsArePositionalParams(true);
  }

  private void onShutdown() {
    // Dump logs.
    System.out.println(LogDumper.dumpLog());

    if (Flags.instance().atsConsoleOlcServerEmbeddedMode.getNonNull()) {
      olcServerRunner.onShutdown();
    } else {
      // Aborts all sessions of the console and kills the OLC server.
      try {
        controlStub.killServer(KillServerRequest.newBuilder().setClientId(clientId).build());
      } catch (GrpcExceptionWithErrorId e) {
        // Does nothing.
      }
    }
  }

  private static LineReader createLineReader() throws IOException {
    return LineReaderBuilder.builder()
        .appName("AtsConsole")
        .terminal(TerminalBuilder.builder().system(true).dumb(true).build())
        .history(new DefaultHistory())
        .completer(CommandCompleterHolder.getInstance())
        .build();
  }

  private static Optional<OlcServerModuleFactory> createOlcServerModuleFactory()
      throws MobileHarnessException {
    if (!Flags.instance().atsConsoleOlcServerEmbeddedMode.getNonNull()) {
      return Optional.empty();
    }

    // Loads factory class from OLC server jar.
    Class<? extends OlcServerModuleFactory> olcServerModuleFactoryClass;
    try {
      olcServerModuleFactoryClass =
          new ReflectionUtil()
              .loadClass(
                  OLC_SERVER_MODULE_FACTORY_IMPL_CLASS_NAME,
                  OlcServerModuleFactory.class,
                  AtsConsole.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      // Gets OLC server jar location.
      Path olcServerFile;
      try {
        olcServerFile =
            Path.of(AtsConsole.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getParent()
                .resolve(OLC_SERVER_CLASS_PATH);
      } catch (URISyntaxException | RuntimeException e2) {
        e.addSuppressed(e2);
        throw MobileHarnessExceptionFactory.createUserFacingException(
            InfraErrorId.ATSC_EMBEDDED_MODE_OLC_SERVER_JAR_NOT_FOUND,
            String.format(
                "ATS console should have an OLC server jar [%s] in the same dir",
                OLC_SERVER_CLASS_PATH),
            e);
      }
      String olcServerFileAbsolutePath = olcServerFile.toAbsolutePath().toString();

      // Checks if OLC server jar exists.
      if (Files.exists(olcServerFile)) {
        throw MobileHarnessExceptionFactory.createUserFacingException(
            InfraErrorId.ATSC_EMBEDDED_MODE_INVALID_OLC_SERVER_JAR,
            String.format("Invalid OLC server jar [%s]", olcServerFileAbsolutePath),
            e);
      } else {
        throw MobileHarnessExceptionFactory.createUserFacingException(
            InfraErrorId.ATSC_EMBEDDED_MODE_OLC_SERVER_JAR_NOT_FOUND,
            String.format(
                "OLC server jar [%s] is not found in the dir of ATS console (%s)",
                OLC_SERVER_CLASS_PATH, olcServerFileAbsolutePath),
            /* cause= */ null);
      }
    }

    // Instantiates the factory.
    OlcServerModuleFactory olcServerModuleFactory =
        Guice.createInjector().getInstance(olcServerModuleFactoryClass);
    return Optional.of(olcServerModuleFactory);
  }

  private static FlagsString preprocessDeviceInfraServiceFlags(FlagsString deviceInfraServiceFlags)
      throws IOException, InterruptedException {
    // Adds builtin flags to the head.
    FlagsString flagsWithBuiltinFlags =
        deviceInfraServiceFlags.addToHead(BuiltinOlcServerFlags.get());

    // Adds extra flags from environment variables to the end.
    ImmutableList.Builder<String> extraFlags = ImmutableList.builder();
    boolean embeddedMode =
        System.getenv(EMBEDDED_MODE_ENV_VAR) == null
            ? DEFAULT_EMBEDDED_MODE
            : Boolean.parseBoolean(System.getenv(EMBEDDED_MODE_ENV_VAR));
    boolean useNewOlcServer = Boolean.parseBoolean(System.getenv(USE_NEW_OLC_SERVER_ENV_VAR));
    if (useNewOlcServer || embeddedMode) {
      // Generate random flags for new OLC server or embedded mode.
      extraFlags.addAll(getRandomServerFlags());
    }
    // Always overrides the embedded mode flag to keep it consistent with random server flags.
    extraFlags.add(String.format("--ats_console_olc_server_embedded_mode=%s", embeddedMode));
    if (System.getenv(USE_TF_RETRY_ENV_VAR) != null) {
      extraFlags.add(
          String.format(
              "--use_tf_retry=%s", Boolean.parseBoolean(System.getenv(USE_TF_RETRY_ENV_VAR))));
    }
    return flagsWithBuiltinFlags.addToEnd(extraFlags.build());
  }

  private static ImmutableList<String> getRandomServerFlags()
      throws IOException, InterruptedException {
    int olcPort = PortProber.pickUnusedPort();
    return ImmutableList.of(
        String.format("--olc_server_port=%d", olcPort),
        String.format("--public_dir=/tmp/xts_console/server_%d", olcPort),
        String.format("--tmp_dir_root=/tmp/xts_console/server_%d", olcPort),
        "--ats_console_olc_server_copy_server_resource=false");
  }
}
