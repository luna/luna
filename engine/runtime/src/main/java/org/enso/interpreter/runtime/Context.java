package org.enso.interpreter.runtime;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Optional;
import java.util.UUID;
import org.enso.compiler.Compiler;
import org.enso.compiler.PackageRepository;
import org.enso.compiler.data.CompilerConfig;
import org.enso.distribution.locking.ThreadSafeFileLockManager;
import org.enso.editions.LibraryName;
import org.enso.interpreter.Language;
import org.enso.interpreter.OptionsHelper;
import org.enso.interpreter.instrument.NotificationHandler;
import org.enso.interpreter.runtime.builtin.Builtins;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.scope.TopLevelScope;
import org.enso.interpreter.runtime.util.TruffleFileSystem;
import org.enso.interpreter.util.ScalaConversions;
import org.enso.librarymanager.ProjectLoadingFailure;
import org.enso.pkg.Package;
import org.enso.pkg.PackageManager;
import org.enso.pkg.QualifiedName;
import org.enso.polyglot.LanguageInfo;
import org.enso.polyglot.RuntimeOptions;
import scala.jdk.javaapi.OptionConverters;

/**
 * The language context is the internal state of the language that is associated with each thread in
 * a running Enso program.
 */
public class Context {

  private final Language language;
  private final Env environment;
  private @CompilationFinal Compiler compiler;
  private final PrintStream out;
  private final PrintStream err;
  private final InputStream in;
  private final BufferedReader inReader;
  private @CompilationFinal PackageRepository packageRepository;
  private @CompilationFinal TopLevelScope topScope;
  private final ThreadManager threadManager;
  private final ResourceManager resourceManager;
  private final boolean isCachingDisabled;
  private final Builtins builtins;
  private final String home;
  private final CompilerConfig compilerConfig;
  private final NotificationHandler notificationHandler;
  private final TruffleLogger logger = TruffleLogger.getLogger(LanguageInfo.ID, Context.class);

  /**
   * Creates a new Enso context.
   *
   * @param language the language identifier
   * @param home language home
   * @param environment the execution environment of the {@link TruffleLanguage}
   * @param notificationHandler a handler for notifications
   */
  public Context(
      Language language, String home, Env environment, NotificationHandler notificationHandler) {
    this.language = language;
    this.environment = environment;
    this.out = new PrintStream(environment.out());
    this.err = new PrintStream(environment.err());
    this.in = environment.in();
    this.inReader = new BufferedReader(new InputStreamReader(environment.in()));
    this.threadManager = new ThreadManager();
    this.resourceManager = new ResourceManager(this);
    this.isCachingDisabled = environment.getOptions().get(RuntimeOptions.DISABLE_INLINE_CACHES_KEY);
    this.compilerConfig = new CompilerConfig(false, true);
    this.home = home;
    this.builtins = new Builtins(this);
    this.notificationHandler = notificationHandler;
  }

  /** Perform expensive initialization logic for the context. */
  public void initialize() {
    TruffleFileSystem fs = new TruffleFileSystem();
    PackageManager<TruffleFile> packageManager = new PackageManager<>(fs);

    Optional<TruffleFile> projectRoot = OptionsHelper.getProjectRoot(environment);
    Optional<Package<TruffleFile>> projectPackage =
        projectRoot.flatMap(
            file -> {
              var result = packageManager.fromDirectory(file);
              if (result.isEmpty()) {
                var projectName = file.getName();
                throw new ProjectLoadingFailure(projectName);
              }
              return ScalaConversions.asJava(result);
            });

    var languageHome =
        OptionsHelper.getLanguageHomeOverride(environment).or(() -> Optional.ofNullable(home));

    var distributionManager = RuntimeDistributionManager$.MODULE$;

    // TODO [RW] Once #1890 is implemented, this will need to connect to the Language Server's
    //  LockManager.
    var lockManager = new ThreadSafeFileLockManager(distributionManager.paths().locks());
    var resourceManager = new org.enso.distribution.locking.ResourceManager(lockManager);

    packageRepository =
        PackageRepository.initializeRepository(
            OptionConverters.toScala(projectPackage),
            OptionConverters.toScala(languageHome),
            distributionManager,
            resourceManager,
            this,
            builtins,
            notificationHandler);
    topScope = new TopLevelScope(builtins, packageRepository);
    this.compiler = new Compiler(this, builtins, packageRepository, compilerConfig);

    projectPackage.ifPresent(
        pkg -> packageRepository.registerMainProjectPackage(pkg.libraryName(), pkg));
  }

  public TruffleFile getTruffleFile(File file) {
    return getEnvironment().getInternalTruffleFile(file.getAbsolutePath());
  }

  /**
   * Gets the compiler instance.
   *
   * <p>The compiler is the portion of the interpreter that performs static analysis and
   * transformation passes on the input program. A handle to the compiler lets you execute various
   * portions of the compilation pipeline, including parsing, analysis, and final code generation.
   *
   * <p>Having this access available means that Enso programs can metaprogram Enso itself.
   *
   * @return a handle to the compiler
   */
  public final Compiler getCompiler() {
    return compiler;
  }

  /**
   * Returns the {@link Env} instance used by this context.
   *
   * @return the {@link Env} instance used by this context
   */
  public Env getEnvironment() {
    return environment;
  }

  /**
   * Gets the language to which this context belongs.
   *
   * @return the language to which this context belongs
   */
  public Language getLanguage() {
    return language;
  }

  /**
   * Returns the standard output stream for this context.
   *
   * @return the standard output stream for this context.
   */
  public PrintStream getOut() {
    return out;
  }

  /**
   * Returns the standard error stream for this context.
   *
   * @return the standard error stream for this context
   */
  public PrintStream getErr() {
    return err;
  }

  /**
   * Returns the standard input stream for this context.
   *
   * @return the standard input stream of bytes.
   */
  public InputStream getIn() {
    return in;
  }

  /** @return the standard input stream of characters. */
  public BufferedReader getInReader() {
    return inReader;
  }

  /**
   * Fetches the module name associated with a given file, using the environment packages
   * information.
   *
   * @param path the path to decode.
   * @return a qualified name of the module corresponding to the file, if exists.
   */
  public Optional<QualifiedName> getModuleNameForFile(File path) {
    TruffleFile p = getTruffleFile(path);
    return ScalaConversions.asJava(packageRepository.getLoadedPackages()).stream()
        .filter(pkg -> p.startsWith(pkg.sourceDir()))
        .map(pkg -> pkg.moduleNameForFile(p))
        .findFirst();
  }

  /**
   * Renames project in packages and modules.
   *
   * @param namespace the namespace the renamed project belongs to
   * @param oldName the old project name
   * @param newName the new project name
   */
  public void renameProject(String namespace, String oldName, String newName) {
    packageRepository.renameProject(namespace, oldName, newName);
  }

  /**
   * Fetches a module associated with a given file.
   *
   * @param path the module path to lookup.
   * @return the relevant module, if exists.
   */
  public Optional<Module> getModuleForFile(File path) {
    return getModuleNameForFile(path).flatMap(n -> getTopScope().getModule(n.toString()));
  }

  /**
   * Ensures that a module is preloaded if it can be loaded at all.
   *
   * @param moduleName name of the module to preload
   */
  public void ensureModuleIsLoaded(String moduleName) {
    LibraryName.fromModuleName(moduleName).foreach(packageRepository::ensurePackageIsLoaded);
  }

  /**
   * Fetches a module with a given name.
   *
   * @param moduleName the qualified name of the module to lookup.
   * @return the relevant module, if exists.
   */
  public Optional<Module> findModule(String moduleName) {
    return getTopScope().getModule(moduleName);
  }

  /**
   * Find a module containing the given expression id.
   *
   * @param expressionId the expression id to lookup.
   * @return the relevant module, if exists.
   */
  public Optional<Module> findModuleByExpressionId(UUID expressionId) {
    return getTopScope().getModules().stream()
        .filter(
            module ->
                module.getIr().preorder().exists(ir -> ir.getExternalId().contains(expressionId)))
        .findFirst();
  }

  /**
   * Finds the package the provided module belongs to.
   *
   * @param module the module to find the package of
   * @return {@code module}'s package, if exists
   */
  public Optional<Package<TruffleFile>> getPackageOf(TruffleFile file) {
    if (file == null) {
      return Optional.empty();
    }
    return ScalaConversions.asJava(packageRepository.getLoadedPackages()).stream()
        .filter(pkg -> file.getAbsoluteFile().startsWith(pkg.root().getAbsoluteFile()))
        .findFirst();
  }

  /**
   * Registers a new module corresponding to a given file.
   *
   * @param path the file to register.
   * @return the newly created module, if the file is a source file.
   */
  public Optional<Module> createModuleForFile(File path) {
    TruffleFile f = getTruffleFile(path);
    return getModuleNameForFile(path)
        .map(name -> getTopScope().createModule(name, getPackageOf(f).orElse(null), f));
  }

  /**
   * Gets the builtin functions from the compiler.
   *
   * @return an object containing the builtin functions
   */
  public Builtins getBuiltins() {
    return this.builtins;
  }

  /**
   * Gets the top-level language scope.
   *
   * @return an object containing the top level language scope
   */
  public TopLevelScope getTopScope() {
    return this.topScope;
  }

  /**
   * Returns the atom constructor corresponding to the {@code Nothing} type, for builtin constructs
   * that need to return an atom of this type.
   *
   * @return the builtin {@code Nothing} atom constructor
   */
  public AtomConstructor getNothing() {
    return getBuiltins().nothing();
  }

  /**
   * Checks whether the strict errors option was set for this context.
   *
   * @return true if the strict errors option is enabled, false otherwise.
   */
  public boolean isStrictErrors() {
    return getEnvironment().getOptions().get(RuntimeOptions.STRICT_ERRORS_KEY);
  }

  /**
   * Checks whether the suggestions indexing is enabled for project files.
   *
   * @return true if project-level suggestion indexing is enabled.
   */
  public boolean isProjectSuggestionsEnabled() {
    return getEnvironment().getOptions().get(RuntimeOptions.ENABLE_PROJECT_SUGGESTIONS_KEY);
  }

  /**
   * Checks whether the suggestion indexing is enabled for external libraries.
   *
   * @return true if the suggestions indexing is enabled for external libraries.
   */
  public boolean isGlobalSuggestionsEnabled() {
    return getEnvironment().getOptions().get(RuntimeOptions.ENABLE_GLOBAL_SUGGESTIONS_KEY);
  }

  /** Creates a new thread that has access to the current language context. */
  public Thread createThread(Runnable runnable) {
    return environment.createThread(runnable);
  }

  /** @return the thread manager for this context. */
  public ThreadManager getThreadManager() {
    return threadManager;
  }

  /** @return the resource manager for this context */
  public ResourceManager getResourceManager() {
    return resourceManager;
  }

  /** @return whether inline caches should be disabled for this context. */
  public boolean isCachingDisabled() {
    return isCachingDisabled;
  }

  /** @return the compiler configuration for this language */
  public CompilerConfig getCompilerConfig() {
    return compilerConfig;
  }
}
