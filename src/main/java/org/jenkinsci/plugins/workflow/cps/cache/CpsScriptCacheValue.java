package org.jenkinsci.plugins.workflow.cps.cache;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Action;
import org.jenkinsci.plugins.workflow.cps.CpsScript;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CpsScriptCacheValue {
    @SuppressFBWarnings("EI_EXPOSE_REP")
    final CpsScript script;

    @SuppressFBWarnings("EI_EXPOSE_REP")
    final List<Action> actions;

    @SuppressFBWarnings("EI_EXPOSE_REP")
    final List<URL> shellURLs;

    @SuppressFBWarnings("EI_EXPOSE_REP")
    final List<URL> trustedShellURLs;

    public CpsScriptCacheValue(CpsScript script, List<Action> actions, URL[] shellURLs, URL[] trustedShellURLs) {
        this.script = script;
        this.actions = Collections.unmodifiableList(actions);
        this.shellURLs = Collections.unmodifiableList(Arrays.asList(shellURLs));
        this.trustedShellURLs = Collections.unmodifiableList(Arrays.asList(trustedShellURLs));
    }
}
