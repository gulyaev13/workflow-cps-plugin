package org.jenkinsci.plugins.workflow.cps.cache;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.Script;
import hudson.model.Queue;
import hudson.model.Run;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsScript;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CpsParseCache {

    private static final Logger LOGGER = Logger.getLogger(CpsParseCache.class.getName());

    public static final boolean ENABLE_CACHE = Boolean.getBoolean(CpsParseCache.class.getName() + ".ENABLE_CACHE");

    public static final int CACHE_SIZE = Integer.getInteger(CpsParseCache.class.getName() + ".CACHE_SIZE", 50);

    private final Map<CpsScriptCacheKey, CpsScriptCacheValue> cacheMap = new ConcurrentHashMap<>();

    //Composite key GroovyClassLoaderWhitelist and GroovyCodeSource
    public Script cacheScript(@Nonnull Whitelist whitelist, @Nonnull GroovyCodeSource codeSource,
                              @Nonnull Binding context, @Nonnull CpsFlowExecution execution,
                              @Nonnull Supplier<Script> scriptParseFunction) {
        CpsScript script = null;
        CpsScriptCacheKey cacheKey = new CpsScriptCacheKey(whitelist, codeSource);
        try {
            Queue.Executable executable = execution.getOwner().getExecutable();
            Run<?,?> build;
            if(executable instanceof Run) {
                build = (Run<?,?>) executable;
            } else {
                throw new CloneNotSupportedException("");
            }

            GroovyClassLoader trustedClassLoader = execution.getTrustedShell().getClassLoader();
            GroovyClassLoader classLoader = execution.getShell().getClassLoader();

            CpsScriptCacheValue cacheValue = cacheMap.get(cacheKey);
            if(cacheValue == null) {
                synchronized (build.getParent()) {
                    cacheValue = cacheMap.get(cacheKey);
                    if (cacheValue == null) {
                        script = (CpsScript) scriptParseFunction.get();
                        CpsScript clonedScript = script.cloneScript(new Binding(), null);
                        cacheValue = new CpsScriptCacheValue(
                                clonedScript,
                                build.getAllActions()
                                        .stream()
                                        .filter(a -> "LibrariesAction".equals(a.getClass().getSimpleName()))
                                        .collect(Collectors.toList()),
                                classLoader.getURLs(),
                                trustedClassLoader.getURLs());
                        cacheMap.put(cacheKey, cacheValue);
                        //no need deep copy
                        //return script.cloneScript(context, execution);
                        return script;
                    }
                }
            }

            script = cacheValue.script;
            cacheValue.shellURLs.forEach(classLoader::addURL);
            cacheValue.trustedShellURLs.forEach(trustedClassLoader::addURL);
            cacheValue.actions.forEach(build::addAction);

            return script.cloneScript(context, execution);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Clone script failed: {0}: {1}", new Object[]{exception, exception.getMessage()});
            LOGGER.log(Level.WARNING, Arrays.toString(exception.getStackTrace()));
        }
        if(script == null) {
            script = (CpsScript) scriptParseFunction.get();
        }
        return script;
    }
}
