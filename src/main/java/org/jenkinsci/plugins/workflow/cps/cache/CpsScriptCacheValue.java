package org.jenkinsci.plugins.workflow.cps.cache;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Action;
import org.jenkinsci.plugins.workflow.cps.CpsScript;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class CpsScriptCacheValue {
    @SuppressFBWarnings("EI_EXPOSE_REP")
    final CpsScript script;

    @SuppressFBWarnings("EI_EXPOSE_REP")
    final List<Action> actions;

    @SuppressFBWarnings("EI_EXPOSE_REP")
    final LibrariesCache librariesCache;

    public CpsScriptCacheValue(CpsScript script, List<Action> actions, LibrariesCache librariesCache) {
        this.script = script;
        this.actions = Collections.unmodifiableList(actions);
        this.librariesCache = librariesCache;
    }

    static class LibrariesCache {
        final static LibrariesCache EMPTY_VALUE = new LibrariesCache(null, Collections.emptyList(), Collections.emptySet());
        final String directoryName;
        final List<URL> urls;
        final Set<String> libraryNames;

        LibrariesCache(String directoryName, List<URL> urls, Set<String> libraryNames) {
            this.directoryName = directoryName;
            this.urls = urls;
            this.libraryNames = libraryNames;
        }
    }
}
