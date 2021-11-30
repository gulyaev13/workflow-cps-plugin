package org.jenkinsci.plugins.workflow.cps.cache;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Extension
public class CpsParseCacheCleanup extends AsyncPeriodicWork {
    private final Long recurrencePeriod;

    public CpsParseCacheCleanup() {
        super("CpsParseCacheCleanup");
        recurrencePeriod = Long.getLong(CpsParseCacheCleanup.class + ".CLEANUP_PERIOD", TimeUnit.MINUTES.toMillis(10));
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        FilePath libraryCacheDir = CpsParseCache.getLibraryCacheDir();
        CpsParseCache parseCache = CpsParseCache.getInstance();
        if (parseCache == null) {
            listener.getLogger().println("Cache is disabled, clean all libraries cached");
            libraryCacheDir.deleteRecursive();
        } else {
            Set<FilePath> currentCache = parseCache.getCacheMap().values().stream()
                    .map(cacheValue -> cacheValue.libCacheDir)
                    .filter(Objects::nonNull)
                    .map(libraryCacheDir::child)
                    .collect(Collectors.toSet());
            for (FilePath cacheEntryDir : libraryCacheDir.list()) {
                if (!currentCache.contains(cacheEntryDir)) {
                    listener.getLogger().println("Delete library not existed in cache - " + cacheEntryDir.getRemote());
                    cacheEntryDir.deleteRecursive();
                }
            }
        }
    }

    @Override
    public long getRecurrencePeriod() {
        return recurrencePeriod;
    }
}
