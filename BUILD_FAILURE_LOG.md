# Build failure log

```text
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.2.1/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 

> Task :createMinecraftArtifacts
Loaded 111 artifacts from /home/runner/work/Shortcut-Terminal/Shortcut-Terminal/build/tmp/createMinecraftArtifacts/nfrt_artifact_manifest.properties
[1m*** Started working on [4mdownloadJson[0m[0m
 [1m[92m✓[0m Completed [4mdownloadJson[0m in 0.02s
[1m*** Started working on [4mdownloadServer[0m[0m
[1m*** Started working on [4mdownloadClientMappings[0m[0m
[1m*** Started working on [4mdownloadClient[0m[0m
[1m*** Started working on [4mlistLibraries[0m[0m
 [1m[92m♻[0m Used cache of [4mlistLibraries[0m in 0.00s
 [1m[92m✓[0m Completed [4mdownloadServer[0m in 0.04s
 [1m[92m✓[0m Completed [4mdownloadClientMappings[0m in 0.04s
 [1m[92m✓[0m Completed [4mdownloadClient[0m in 0.03s
[1m*** Started working on [4mmergeMappings[0m[0m
 [1m[92m♻[0m Used cache of [4mmergeMappings[0m in 0.00s
[1m*** Started working on [4mstripClient[0m[0m
 [1m[92m♻[0m Used cache of [4mstripClient[0m in 0.00s
[1m*** Started working on [4mextractServer[0m[0m
 [1m[92m♻[0m Used cache of [4mextractServer[0m in 0.00s
[1m*** Started working on [4mstripServer[0m[0m
 [1m[92m♻[0m Used cache of [4mstripServer[0m in 0.00s
[1m*** Started working on [4mmerge[0m[0m
 [1m[92m♻[0m Used cache of [4mmerge[0m in 0.00s
[1m*** Started working on [4mrename[0m[0m
 [1m[92m♻[0m Used cache of [4mrename[0m in 0.00s
[1m*** Started working on [4mdecompile[0m[0m
 [1m[92m♻[0m Used cache of [4mdecompile[0m in 0.00s
[1m*** Started working on [4minject[0m[0m
 [1m[92m♻[0m Used cache of [4minject[0m in 0.00s
[1m*** Started working on [4mpatch[0m[0m
 [1m[92m♻[0m Used cache of [4mpatch[0m in 0.00s
[1m*** Started working on [4mapplyNeoforgePatches[0m[0m
 [1m[92m♻[0m Used cache of [4mapplyNeoforgePatches[0m in 0.00s
[1m*** Started working on [4mtransformSources[0m[0m
 [1m[92m♻[0m Used cache of [4mtransformSources[0m in 0.00s
[1m*** Started working on [4mrecompile[0m[0m
 [1m[92m♻[0m Used cache of [4mrecompile[0m in 0.00s
[1m*** Started working on [4msourcesWithNeoForge[0m[0m
 [1m[92m♻[0m Used cache of [4msourcesWithNeoForge[0m in 0.00s
[1m*** Started working on [4mcompiledWithNeoForge[0m[0m
 [1m[92m♻[0m Used cache of [4mcompiledWithNeoForge[0m in 0.00s
[1m*** Started working on [4msourcesAndCompiledWithNeoForge[0m[0m
 [1m[92m♻[0m Used cache of [4msourcesAndCompiledWithNeoForge[0m in 0.00s
Total runtime: 1.07s


> Task :compileJava
Note: Some input files use or override a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Note: /home/runner/work/Shortcut-Terminal/Shortcut-Terminal/src/main/java/unsa/st/com/remote/RemoteControlManager.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.

> Task :processResources
> Task :classes
> Task :jarJar NO-SOURCE
> Task :shadowJar
> Task :jar SKIPPED
> Task :assemble UP-TO-DATE
> Task :compileTestJava NO-SOURCE
> Task :processTestResources NO-SOURCE
> Task :testClasses UP-TO-DATE
> Task :test NO-SOURCE
> Task :check UP-TO-DATE
> Task :build UP-TO-DATE
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_2-1793202749589.json

[Incubating] Problems report is available at: file:///home/runner/work/Shortcut-Terminal/Shortcut-Terminal/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 16s
4 actionable tasks: 4 executed
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.2.1/userguide/configuration_cache_enabling.html
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.2.1/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :createMinecraftArtifacts UP-TO-DATE
> Task :compileJava UP-TO-DATE
> Task :processResources UP-TO-DATE
> Task :classes UP-TO-DATE

> Task :downloadAssets
Failed to read downloaded asset index: task ':downloadAssets' property 'assetPropertiesFile'
Loaded 111 artifacts from /home/runner/work/Shortcut-Terminal/Shortcut-Terminal/build/tmp/downloadAssets/nfrt_artifact_manifest.properties
Downloading asset index 17
Using Minecraft asset root: /home/runner/.gradle/caches/neoformruntime/assets
  ↓ https://piston-meta.mojang.com/v1/packages/cd09c6948871a888359b45607a1dfac4e97f2561/17.json

> Task :writeGameTestServerLegacyClasspath
> Task :prepareGameTestServerRun

> Task :runGameTestServer
WARN StatusConsoleListener Advanced terminal features are not available in this environment
[15:52:51] [main/INFO] [cp.mo.mo.Launcher/MODLAUNCHER]: ModLauncher running: args [--launchTarget, forgeserverdev, --gameDir, ., --fml.fmlVersion, 4.0.42, --fml.mcVersion, 1.21.1, --fml.neoForgeVersion, 21.1.219, --fml.neoFormVersion, 20240808.144430]
[15:52:51] [main/INFO] [cp.mo.mo.Launcher/MODLAUNCHER]: JVM identified as Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS
[15:52:51] [main/INFO] [cp.mo.mo.Launcher/MODLAUNCHER]: ModLauncher 11.0.5+main.901c6ea8 starting: java version 21.0.13 by Eclipse Adoptium; OS Linux arch amd64 version 7.0.0-1017-azure
[15:52:51] [main/INFO] [ne.ne.fm.lo.ImmediateWindowHandler/]: ImmediateWindowProvider not loading because launch target is forgeserverdev
[15:52:51] [main/INFO] [mixin/]: SpongePowered MIXIN Subsystem Version=0.8.7 Source=union:/home/runner/.gradle/caches/modules-2/files-2.1/net.fabricmc/sponge-mixin/0.15.2+mixin.0.8.7/2af2f021d8e02a0220dc27a7a72b4666d66d44ca/sponge-mixin-0.15.2+mixin.0.8.7.jar%2394!/ Service=ModLauncher Env=SERVER
[15:52:52] [main/INFO] [ne.ne.fm.lo.mo.lo.JarInJarDependencyLocator/]: Found 2 dependencies adding them to mods collection
[15:52:52] [main/INFO] [ne.ne.fm.lo.mo.ModDiscoverer/]: 
     Mod List:
		Name Version (Mod Id)

		Minecraft 1.21.1 (minecraft)
		NeoForge 21.1.219 (neoforge)
		Patchouli 1.21.1-93-NEOFORGE (patchouli)
		Shortcut Terminal 1.0.1(Beta) (shortcutterminal)
[15:52:53] [main/INFO] [mixin/]: Compatibility level set to JAVA_21
[15:52:53] [main/INFO] [cp.mo.mo.LaunchServiceHandler/MODLAUNCHER]: Launching target 'forgeserverdev' with arguments [--gameDir, .]
[15:52:53] [main/WARN] [mixin/]: Reference map 'patchouli.refmap.json' for patchouli_xplat.mixins.json could not be read. If this is a development environment you can ignore this message
[15:52:54] [main/INFO] [MixinExtras|Service/]: Initializing MixinExtras via com.llamalad7.mixinextras.service.MixinExtrasServiceImpl(version=0.5.0).
[15:53:00] [main/WARN] [minecraft/Commands]: Ambiguity between arguments [teleport, location] and [teleport, destination] with inputs: [0.1 -0.5 .9, 0 0 0]
[15:53:00] [main/WARN] [minecraft/Commands]: Ambiguity between arguments [teleport, location] and [teleport, targets] with inputs: [0.1 -0.5 .9, 0 0 0]
[15:53:00] [main/WARN] [minecraft/Commands]: Ambiguity between arguments [teleport, destination] and [teleport, targets] with inputs: [Player, 0123, @e, dd12be42-52a9-4a91-a8a1-11c01849e498]
[15:53:00] [main/WARN] [minecraft/Commands]: Ambiguity between arguments [teleport, targets] and [teleport, destination] with inputs: [Player, 0123, dd12be42-52a9-4a91-a8a1-11c01849e498]
[15:53:00] [main/WARN] [minecraft/Commands]: Ambiguity between arguments [teleport, targets, location] and [teleport, targets, destination] with inputs: [0.1 -0.5 .9, 0 0 0]
[15:53:00] [modloading-worker-0/INFO] [ne.ne.ne.co.NeoForgeMod/NEOFORGE-MOD]: NeoForge mod loading, version 21.1.219, for MC 1.21.1
[15:53:00] [modloading-worker-0/INFO] [un.st.co.ShortcutTerminal/]: Shortcut Terminal Mod initializing...
[15:53:00] [modloading-worker-0/INFO] [un.st.co.ShortcutTerminal/]: Remote control is only available on dedicated servers.
[15:53:00] [modloading-worker-0/INFO] [un.st.co.ShortcutTerminal/]: Shortcut Terminal: dedicated server dist, client-only systems skipped
[15:53:00] [main/ERROR] [minecraft/Settings]: Failed to load properties from file: server.properties
java.nio.file.NoSuchFileException: server.properties
	at java.base/sun.nio.fs.UnixException.translateToIOException(UnixException.java:92) ~[?:?] {}
	at java.base/sun.nio.fs.UnixException.rethrowAsIOException(UnixException.java:106) ~[?:?] {}
	at java.base/sun.nio.fs.UnixException.rethrowAsIOException(UnixException.java:111) ~[?:?] {}
	at java.base/sun.nio.fs.UnixFileSystemProvider.newByteChannel(UnixFileSystemProvider.java:261) ~[?:?] {}
	at java.base/java.nio.file.Files.newByteChannel(Files.java:380) ~[?:?] {}
	at java.base/java.nio.file.Files.newByteChannel(Files.java:432) ~[?:?] {}
	at java.base/java.nio.file.spi.FileSystemProvider.newInputStream(FileSystemProvider.java:420) ~[?:?] {}
	at java.base/java.nio.file.Files.newInputStream(Files.java:160) ~[?:?] {}
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.server.dedicated.Settings.loadFromFile(Settings.java:38) ~[neoforge-21.1.219.jar%23125!/:?] {re:classloading}
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.server.dedicated.DedicatedServerProperties.fromFile(DedicatedServerProperties.java:132) ~[neoforge-21.1.219.jar%23125!/:?] {re:classloading}
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.server.dedicated.DedicatedServerSettings.<init>(DedicatedServerSettings.java:12) ~[neoforge-21.1.219.jar%23125!/:?] {re:classloading}
	at TRANSFORMER/minecraft@1.21.1/net.minecraft.server.Main.main(Main.java:125) ~[neoforge-21.1.219.jar%23125!/:?] {re:classloading}
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[?:?] {}
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[?:?] {}
	at MC-BOOTSTRAP/fml_loader@4.0.42/net.neoforged.fml.loading.targets.CommonLaunchHandler.runTarget(CommonLaunchHandler.java:136) ~[loader-4.0.42.jar%23102!/:4.0] {}
	at MC-BOOTSTRAP/fml_loader@4.0.42/net.neoforged.fml.loading.targets.CommonLaunchHandler.serverService(CommonLaunchHandler.java:128) ~[loader-4.0.42.jar%23102!/:4.0] {}
	at MC-BOOTSTRAP/fml_loader@4.0.42/net.neoforged.fml.loading.targets.NeoForgeServerDevLaunchHandler.runService(NeoForgeServerDevLaunchHandler.java:23) ~[loader-4.0.42.jar%23102!/:4.0] {}
	at MC-BOOTSTRAP/fml_loader@4.0.42/net.neoforged.fml.loading.targets.CommonLaunchHandler.lambda$launchService$4(CommonLaunchHandler.java:118) ~[loader-4.0.42.jar%23102!/:4.0] {}
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.5/cpw.mods.modlauncher.LaunchServiceHandlerDecorator.launch(LaunchServiceHandlerDecorator.java:30) [modlauncher-11.0.5.jar%2382!/:?] {}
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.5/cpw.mods.modlauncher.LaunchServiceHandler.launch(LaunchServiceHandler.java:53) [modlauncher-11.0.5.jar%2382!/:?] {}
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.5/cpw.mods.modlauncher.LaunchServiceHandler.launch(LaunchServiceHandler.java:71) [modlauncher-11.0.5.jar%2382!/:?] {}
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.5/cpw.mods.modlauncher.Launcher.run(Launcher.java:103) [modlauncher-11.0.5.jar%2382!/:?] {}
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.5/cpw.mods.modlauncher.Launcher.main(Launcher.java:74) [modlauncher-11.0.5.jar%2382!/:?] {}
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.5/cpw.mods.modlauncher.BootstrapLaunchConsumer.accept(BootstrapLaunchConsumer.java:26) [modlauncher-11.0.5.jar%2382!/:?] {}
	at MC-BOOTSTRAP/cpw.mods.modlauncher@11.0.5/cpw.mods.modlauncher.BootstrapLaunchConsumer.accept(BootstrapLaunchConsumer.java:23) [modlauncher-11.0.5.jar%2382!/:?] {}
	at cpw.mods.bootstraplauncher@2.0.2/cpw.mods.bootstraplauncher.BootstrapLauncher.run(BootstrapLauncher.java:210) [bootstraplauncher-2.0.2.jar:?] {}
	at cpw.mods.bootstraplauncher@2.0.2/cpw.mods.bootstraplauncher.BootstrapLauncher.main(BootstrapLauncher.java:69) [bootstraplauncher-2.0.2.jar:?] {}
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[?:?] {}
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[?:?] {}
	at net.neoforged.devlaunch.Main.main(Main.java:55) [DevLaunch-1.0.1.jar:?] {}
[15:53:00] [main/INFO] [mojang/YggdrasilAuthenticationService]: Environment: Environment[sessionHost=https://sessionserver.mojang.com, servicesHost=https://api.minecraftservices.com, name=PROD]
[15:53:00] [main/WARN] [minecraft/VanillaPackResourcesBuilder]: Assets URL 'union:/home/runner/work/Shortcut-Terminal/Shortcut-Terminal/build/moddev/artifacts/neoforge-21.1.219.jar%23125!/assets/.mcassetsroot' uses unexpected schema
[15:53:00] [main/WARN] [minecraft/VanillaPackResourcesBuilder]: Assets URL 'union:/home/runner/work/Shortcut-Terminal/Shortcut-Terminal/build/moddev/artifacts/neoforge-21.1.219.jar%23125!/data/.mcassetsroot' uses unexpected schema
[15:53:00] [main/INFO] [ne.ne.ne.ga.GameTestHooks/]: Enabled Gametest Namespaces: [shortcutterminal]
[15:53:01] [Worker-Main-1/INFO] [un.st.co.ShortcutTerminal/]: Shortcut Terminal commands registered
[15:53:01] [Worker-Main-1/INFO] [un.st.co.ShortcutTerminal/]: Shortcut Terminal commands registered
[15:53:02] [main/INFO] [minecraft/RecipeManager]: Loaded 1328 recipes
[15:53:02] [main/INFO] [minecraft/AdvancementTree]: Loaded 1400 advancements
[15:53:03] [Server thread/INFO] [minecraft/MinecraftServer]: Preparing start region for dimension minecraft:overworld
[15:53:03] [worldgen/INFO] [minecraft/LoggerChunkProgressListener]: Preparing spawn area: 2%
[15:53:03] [Server thread/INFO] [minecraft/LoggerChunkProgressListener]: Time elapsed: 322 ms
[15:53:03] [Server thread/INFO] [minecraft/GameTestServer]: Started game test server
[15:53:03] [Server thread/INFO] [ne.ne.ne.se.pe.PermissionAPI/]: Successfully initialized permission handler neoforge:default_handler
[15:53:03] [Server thread/INFO] [patchouli/]: Sending reload packet to clients
[15:53:03] [Server thread/INFO] [minecraft/GameTestServer]: 4 tests are now running at position 5027419, -59, -13153134!
[15:53:04] [Server thread/INFO] [minecraft/GameTestRunner]: Running test batch 'defaultBatch:0' (4 tests)...
[15:53:04] [Server thread/INFO] [minecraft/GameTestServer]: [    ]
[15:53:04] [Server thread/ERROR] [minecraft/LogTestReporter]: lithographyfueloutputandlocking failed at 5027447, -60, -13153134! Index 6 out of bounds for length 6
[15:53:04] [Server thread/INFO] [minecraft/GameTestServer]: [++X+]
[15:53:04] [Server thread/INFO] [minecraft/GameTestServer]: ========= 4 GAME TESTS COMPLETE IN 632.2 ms ======================
[15:53:04] [Server thread/INFO] [minecraft/GameTestServer]: 1 required tests failed :(
[15:53:04] [Server thread/INFO] [minecraft/GameTestServer]:    - lithographyfueloutputandlocking
[15:53:04] [Server thread/INFO] [minecraft/GameTestServer]: ====================================================
[15:53:04] [Server thread/INFO] [minecraft/MinecraftServer]: Stopping server
[15:53:04] [Server thread/INFO] [minecraft/MinecraftServer]: Saving players
[15:53:04] [Server thread/INFO] [minecraft/MinecraftServer]: Saving worlds
[15:53:06] [Server thread/INFO] [minecraft/MinecraftServer]: Saving chunks for level 'ServerLevel[Test Level]'/minecraft:overworld
[15:53:06] [Server thread/INFO] [minecraft/MinecraftServer]: Saving chunks for level 'ServerLevel[Test Level]'/minecraft:the_end
[15:53:06] [Server thread/INFO] [minecraft/MinecraftServer]: Saving chunks for level 'ServerLevel[Test Level]'/minecraft:the_nether
[15:53:06] [Server thread/INFO] [minecraft/MinecraftServer]: ThreadedAnvilChunkStorage (world): All chunks are saved
[15:53:06] [Server thread/INFO] [minecraft/MinecraftServer]: ThreadedAnvilChunkStorage (DIM1): All chunks are saved
[15:53:06] [Server thread/INFO] [minecraft/MinecraftServer]: ThreadedAnvilChunkStorage (DIM-1): All chunks are saved
[15:53:06] [Server thread/INFO] [minecraft/MinecraftServer]: ThreadedAnvilChunkStorage: All dimensions are saved
[15:53:06] [Server thread/INFO] [minecraft/GameTestServer]: Game test server shutting down

> Task :runGameTestServer FAILED
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1793202765667.json

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':runGameTestServer'.
> Process 'command '/usr/lib/jvm/temurin-21-jdk-amd64/bin/java'' finished with non-zero exit value 1

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to generate a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':runGameTestServer'.
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.lambda$executeIfValid$1(ExecuteActionsTaskExecuter.java:135)
	at org.gradle.internal.Try$Failure.ifSuccessfulOrElse(Try.java:288)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:133)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:121)
	at org.gradle.api.internal.tasks.execution.ProblemsTaskPathTrackingTaskExecuter.execute(ProblemsTaskPathTrackingTaskExecuter.java:41)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:209)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.DefaultNodeExecutor.executeLocalTaskNode(DefaultNodeExecutor.java:55)
	at org.gradle.execution.plan.DefaultNodeExecutor.execute(DefaultNodeExecutor.java:34)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:355)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:343)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:339)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:84)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:339)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:328)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:47)
Caused by: org.gradle.process.ProcessExecutionException: Process 'command '/usr/lib/jvm/temurin-21-jdk-amd64/bin/java'' finished with non-zero exit value 1
	at org.gradle.process.internal.DefaultExecHandle$ExecResultImpl.assertNormalExitValue(DefaultExecHandle.java:450)
	at org.gradle.process.internal.DefaultJavaExecAction.execute(DefaultJavaExecAction.java:58)
	at org.gradle.api.tasks.JavaExec.exec(JavaExec.java:166)
	at net.neoforged.moddevgradle.internal.RunGameTask.exec(RunGameTask.java:51)
	at org.gradle.internal.reflect.JavaMethod.invoke(JavaMethod.java:125)
	at org.gradle.api.internal.project.taskfactory.StandardTaskAction.doExecute(StandardTaskAction.java:58)
	at org.gradle.api.internal.project.taskfactory.StandardTaskAction.execute(StandardTaskAction.java:51)
	at org.gradle.api.internal.project.taskfactory.StandardTaskAction.execute(StandardTaskAction.java:29)
	at org.gradle.api.internal.tasks.execution.TaskExecution$3.run(TaskExecution.java:252)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:29)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:26)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:47)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeAction(TaskExecution.java:237)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeActions(TaskExecution.java:220)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeWithPreviousOutputFiles(TaskExecution.java:203)
	at org.gradle.api.internal.tasks.execution.TaskExecution.execute(TaskExecution.java:170)
	at org.gradle.internal.execution.steps.ExecuteStep.executeInternal(ExecuteStep.java:105)
	at org.gradle.internal.execution.steps.ExecuteStep.access$000(ExecuteStep.java:44)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:59)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:56)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:209)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:56)
	at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:44)
	at org.gradle.internal.execution.steps.CancelExecutionStep.execute(CancelExecutionStep.java:42)
	at org.gradle.internal.execution.steps.TimeoutStep.executeWithoutTimeout(TimeoutStep.java:75)
	at org.gradle.internal.execution.steps.TimeoutStep.execute(TimeoutStep.java:55)
	at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:50)
	at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:28)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:68)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:38)
	at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:61)
	at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:26)
	at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:69)
	at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:46)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:39)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:28)
	at org.gradle.internal.execution.steps.BuildCacheStep.executeWithoutCache(BuildCacheStep.java:189)
	at org.gradle.internal.execution.steps.BuildCacheStep.lambda$execute$1(BuildCacheStep.java:75)
	at org.gradle.internal.Either$Right.fold(Either.java:176)
	at org.gradle.internal.execution.caching.CachingState.fold(CachingState.java:62)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:73)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:48)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:46)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:35)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.executeBecause(SkipUpToDateStep.java:75)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.lambda$execute$2(SkipUpToDateStep.java:53)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:53)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:35)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:37)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:27)
	at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:49)
	at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:27)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:71)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:39)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:64)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:35)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:62)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:40)
	at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:76)
	at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:45)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.executeWithNonEmptySources(AbstractSkipEmptyWorkStep.java:136)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:61)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:38)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:23)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:75)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:41)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.lambda$execute$0(AssignMutableWorkspaceStep.java:35)
	at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:297)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:31)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:22)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:40)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:23)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.lambda$execute$2(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:39)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:46)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:34)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:44)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:31)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:64)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:132)
	... 30 more


BUILD FAILED in 24s
7 actionable tasks: 4 executed, 3 up-to-date
```
