package org.jenkinsci.plugins.workflow.cps.cache;

import groovy.lang.GroovyCodeSource;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;

import java.util.Objects;

public final class CpsScriptCacheKey {
    final Whitelist whitelist;
    final GroovyCodeSource codeSource;

    public CpsScriptCacheKey(Whitelist whitelist, GroovyCodeSource codeSource) {
        this.whitelist = whitelist;
        this.codeSource = codeSource;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CpsScriptCacheKey)){
            return false;
        }
        if(this == obj) {
            return true;
        }
        CpsScriptCacheKey other = (CpsScriptCacheKey) obj;
        if(!Objects.equals(codeSource.getScriptText(), other.codeSource.getScriptText())) {
            return false;
        } else if(!Objects.equals(codeSource.getName(), other.codeSource.getName())) {
            return false;
        } else if(!Objects.equals(codeSource.isCachable(), other.codeSource.isCachable())) {
            return false;
        }
        return Objects.equals(whitelist, other.whitelist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codeSource.getScriptText(), codeSource.getName(), codeSource.isCachable(), whitelist);
    }
}
