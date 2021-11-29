package org.jenkinsci.plugins.workflow.cps.cache;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.Script;
import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Queue;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsScript;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CpsParseCache {

    private static final Logger LOGGER = Logger.getLogger(CpsParseCache.class.getName());

    public static final boolean ENABLE_CACHE = Boolean.getBoolean(CpsParseCache.class.getName() + ".ENABLE_CACHE");

    public static final int CACHE_SIZE = Integer.getInteger(CpsParseCache.class.getName() + ".CACHE_SIZE", 50);

    private static final String LIBRARIES_PARSE_CACHE_DIR = "parse-cache-libraries";

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

            CpsScriptCacheValue cacheValue = cacheMap.get(cacheKey);
            if(cacheValue == null) {
                synchronized (build.getParent()) {
                    cacheValue = cacheMap.get(cacheKey);
                    if (cacheValue == null) {
                        script = (CpsScript) scriptParseFunction.get();
                        cacheValue = createCacheValue(execution, script, build);
                        if (cacheValue != null) {
                            cacheMap.put(cacheKey, cacheValue);
                        }
                        //no need deep copy
                        return script;
                    }
                }
            }

            script = cacheValue.script;
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

    private CpsScriptCacheValue createCacheValue(CpsFlowExecution execution, CpsScript script, Run<?,?> build) {
        CpsScript clonedScript = script.cloneScript(new Binding(), null);
        Action librariesAction = build.getAllActions()
                .stream()
                .filter(a -> "LibrariesAction".equals(a.getClass().getSimpleName()))
                .findFirst()
                .orElse(null);
        LibrariesCache libCache = null;
        if (librariesAction != null) {
            try {
                libCache = cacheLibraries(librariesAction, execution);
            } catch (Exception e) {
                return null;
            }
        }
        return new CpsScriptCacheValue(clonedScript,
                librariesAction != null ? Collections.singletonList(librariesAction) : Collections.emptyList(),
                libCache != null ? libCache.urls : Collections.emptyList(),
                libCache != null ? libCache.dirName : null);
    }

    private LibrariesCache cacheLibraries(Action action, CpsFlowExecution execution) throws NoSuchFieldException, IllegalAccessException, InterruptedException, IOException {
        final String libraryCacheDirName = UUID.randomUUID().toString();
        final FilePath libraryCacheDir = new FilePath(getLibraryCacheDir(), libraryCacheDirName);
        final FilePath buildRootDir = new FilePath(execution.getOwner().getRootDir());

        Collection<String> librariesNames = extractLibrariesNames(action);
        URL[] sourceURls = execution.getTrustedShell().getClassLoader().getURLs();
        List<URL> targetURLs = new ArrayList<>();
        Set<String> cachedLibs = new HashSet<>();

        for (URL url : sourceURls) {
            String filePath = url.getFile();
            if (filePath.startsWith(buildRootDir.getRemote())) {
                String libName = null;
                FilePath libPath = null;
                for (String name : librariesNames) {
                    FilePath path = new FilePath(buildRootDir, "libs/" + name);
                    if(filePath.startsWith(path.getRemote())) {
                        libPath = path;
                        libName = name;
                    }
                }
                if (libName == null) {
                    throw new IllegalStateException(String.format("Founded classpath %s can't be cached because it isn't a library", filePath));
                }
                if (!cachedLibs.contains(libName)) {
                    FilePath libCachePath = libraryCacheDir.child(libName);
                    libraryCacheDir.mkdirs();
                    libPath.copyRecursiveTo(libCachePath);
                    FilePath srcDir = libCachePath.child("src");
                    if (srcDir.isDirectory()) {
                        targetURLs.add(srcDir.toURI().toURL());
                    }
                    FilePath varsDir = libCachePath.child("vars");
                    if (varsDir.isDirectory()) {
                        targetURLs.add(varsDir.toURI().toURL());
                    }
                    cachedLibs.add(libName);
                }
            } else {
                targetURLs.add(url);
            }
        }
        return new LibrariesCache(cachedLibs.isEmpty() ? null : libraryCacheDirName, targetURLs);
    }

    private Collection<String> extractLibrariesNames(Action action) throws NoSuchFieldException, IllegalAccessException {
        Field librariesF = action.getClass().getDeclaredField("libraries");
        librariesF.setAccessible(true);
        List<Object> libraries = (List<Object>) librariesF.get(action);

        Set<String> libraryNames = new HashSet<>();
        for(Object library : libraries) {
            Field nameF = library.getClass().getDeclaredField("name");
            nameF.setAccessible(true);
            libraryNames.add((String) nameF.get(library));
        }
        return libraryNames;
    }

    private static FilePath getLibraryCacheDir() {
        Jenkins jenkins = Jenkins.get();
        return new FilePath(jenkins.getRootPath(), LIBRARIES_PARSE_CACHE_DIR);
    }

    static class LibrariesCache {
        final String dirName;
        final List<URL> urls;

        LibrariesCache(String dirName, List<URL> urls) {
            this.dirName = dirName;
            this.urls = urls;
        }
    }
}
