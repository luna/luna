package org.enso.interpreter.runtime.scope;

import com.oracle.truffle.api.CompilerDirectives;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.oracle.truffle.api.interop.TruffleObject;
import org.enso.interpreter.runtime.Module;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.error.RedefinedMethodException;

/** A representation of Enso's per-file top-level scope. */
public class ModuleScope implements TruffleObject {
  private final AtomConstructor associatedType;
  private final Module module;
  private Map<String, Object> polyglotSymbols = new HashMap<>();
  private Map<String, AtomConstructor> constructors = new HashMap<>();
  private Map<AtomConstructor, Map<String, Function>> methods = new HashMap<>();
  private Set<ModuleScope> imports = new HashSet<>();
  private Set<ModuleScope> exports = new HashSet<>();

  /**
   * Creates a new object of this class.
   *
   * @param module the module related to the newly created scope.
   */
  public ModuleScope(Module module) {
    this.module = module;
    this.associatedType = new AtomConstructor(module.getName().item(), this).initializeFields();
  }

  /**
   * Adds an Atom constructor definition to the module scope.
   *
   * @param constructor the constructor to register
   */
  public void registerConstructor(AtomConstructor constructor) {
    constructors.put(constructor.getName(), constructor);
  }

  /** @return the associated type of this module. */
  public AtomConstructor getAssociatedType() {
    return associatedType;
  }

  /** @return the module associated with this scope. */
  public Module getModule() {
    return module;
  }

  /** @return the set of modules imported by this module. */
  public Set<ModuleScope> getImports() {
    return imports;
  }

  /**
   * Looks up a constructor in the module scope locally.
   *
   * @param name the name of the module binding
   * @return the atom constructor associated with {@code name}, or {@link Optional#empty()}
   */
  public Optional<AtomConstructor> getLocalConstructor(String name) {
    if (associatedType.getName().equals(name)) {
      return Optional.of(associatedType);
    }
    return Optional.ofNullable(this.constructors.get(name));
  }

  /**
   * Looks up a constructor in the module scope.
   *
   * @param name the name of the module binding
   * @return the Atom constructor associated with {@code name}, or {@link Optional#empty()}
   */
  public Optional<AtomConstructor> getConstructor(String name) {
    Optional<AtomConstructor> locallyDefined = getLocalConstructor(name);
    if (locallyDefined.isPresent()) return locallyDefined;
    return imports.stream()
        .map(scope -> scope.getLocalConstructor(name))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  /**
   * Returns a map of methods defined in this module for a given constructor.
   *
   * @param cons the constructor for which method map is requested
   * @return a map containing all the defined methods by name
   */
  private Map<String, Function> ensureMethodMapFor(AtomConstructor cons) {
    return methods.computeIfAbsent(cons, k -> new HashMap<>());
  }

  private Map<String, Function> getMethodMapFor(AtomConstructor cons) {
    Map<String, Function> result = methods.get(cons);
    if (result == null) {
      return new HashMap<>();
    }
    return result;
  }

  /**
   * Registers a method defined for a given type.
   *
   * @param atom type the method was defined for
   * @param method method name
   * @param function the {@link Function} associated with this definition
   */
  public void registerMethod(AtomConstructor atom, String method, Function function) {
    method = method.toLowerCase();
    Map<String, Function> methodMap = ensureMethodMapFor(atom);

    if (methodMap.containsKey(method)) {
      throw new RedefinedMethodException(atom.getName(), method);
    } else {
      methodMap.put(method, function);
    }
  }

  /**
   * Registers a new symbol in the polyglot namespace.
   *
   * @param name the name of the symbol
   * @param sym the value being exposed
   */
  public void registerPolyglotSymbol(String name, Object sym) {
    polyglotSymbols.put(name, sym);
  }

  /**
   * Looks up a polyglot symbol by name.
   *
   * @param name the name of the symbol being looked up
   * @return the polyglot value registered for {@code name}, if exists.
   */
  public Optional<Object> lookupPolyglotSymbol(String name) {
    return Optional.ofNullable(polyglotSymbols.get(name));
  }

  /**
   * Looks up the definition for a given type and method name.
   *
   * <p>The resolution algorithm is first looking for methods defined at the constructor definition
   * site (i.e. non-overloads), then looks for methods defined in this scope and finally tries to
   * resolve the method in all dependencies of this module.
   *
   * @param atom type to lookup the method for.
   * @param name the method name.
   * @return the matching method definition or null if not found.
   */
  @CompilerDirectives.TruffleBoundary
  public Function lookupMethodDefinition(AtomConstructor atom, String name) {
    String lowerName = name.toLowerCase();
    Function definedWithAtom = atom.getDefinitionScope().getMethodMapFor(atom).get(lowerName);
    if (definedWithAtom != null) {
      return definedWithAtom;
    }
    Function definedHere = getMethodMapFor(atom).get(lowerName);
    if (definedHere != null) {
      return definedHere;
    }
    return imports.stream()
        .map(scope -> scope.getExportedMethod(atom, name))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private Function getExportedMethod(AtomConstructor atom, String name) {
    Function here = getMethodMapFor(atom).get(name);
    if (here != null) {
      return here;
    }
    return exports.stream()
        .map(scope -> scope.getMethodMapFor(atom).get(name))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Adds a dependency for this module.
   *
   * @param scope the scope of the newly added dependency
   */
  public void addImport(ModuleScope scope) {
    imports.add(scope);
  }

  /**
   * Adds an information about the module exporting another module.
   *
   * @param scope the exported scope
   */
  public void addExport(ModuleScope scope) {
    exports.add(scope);
  }

  public Map<String, AtomConstructor> getConstructors() {
    return constructors;
  }

  /** @return the raw method map held by this module */
  public Map<AtomConstructor, Map<String, Function>> getMethods() {
    return methods;
  }

  /** @return the polyglot symbols imported into this scope. */
  public Map<String, Object> getPolyglotSymbols() {
    return polyglotSymbols;
  }

  public void reset() {
    imports = new HashSet<>();
    exports = new HashSet<>();
    methods = new HashMap<>();
    constructors = new HashMap<>();
    polyglotSymbols = new HashMap<>();
  }
}
