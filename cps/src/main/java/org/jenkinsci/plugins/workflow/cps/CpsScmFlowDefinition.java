/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.slaves.WorkspaceList;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.JOB;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

@PersistIn(JOB)
public class CpsScmFlowDefinition extends FlowDefinition {

    private final SCM scm;
    private final String scriptPath;

    @DataBoundConstructor public CpsScmFlowDefinition(SCM scm, String scriptPath) {
        this.scm = scm;
        this.scriptPath = scriptPath;
    }

    public SCM getScm() {
        return scm;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    @Override public CpsFlowExecution create(FlowExecutionOwner owner, List<? extends Action> actions) throws IOException {
        TaskListener listener = new LogTaskListener(Logger.getLogger(CpsScmFlowDefinition.class.getName()), Level.INFO); // TODO introduce an overload accepting TaskListener
        for (Action a : actions) {
            if (a instanceof CpsFlowFactoryAction2) {
                return ((CpsFlowFactoryAction2) a).create(this, owner, actions);
            }
        }
        FilePath dir;
        Queue.Executable _build = owner.getExecutable();
        if (!(_build instanceof Run)) {
            throw new IOException("can only check out SCM into a Run");
        }
        Run<?,?> build = (Run<?,?>) _build;
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IOException("Jenkins is not running");
        }
        if (build.getParent() instanceof TopLevelItem) {
            FilePath baseWorkspace = jenkins.getWorkspaceFor((TopLevelItem) build.getParent());
            assert baseWorkspace != null : "this override should actually be @Nonnull";
            dir = baseWorkspace.withSuffix("@script");
        } else { // should not happen, but just in case:
            dir = new FilePath(owner.getRootDir());
        }
        try {
            String script;
            Computer masterComputer = jenkins.toComputer();
            if (masterComputer == null) {
                throw new IOException("Master computer not available");
            }
            WorkspaceList.Lease lease = masterComputer.getWorkspaceList().acquire(dir);
            try {
                scm.checkout(build, jenkins.createLauncher(listener), dir, listener, /* TODO consider whether to add to changelog */null, /* TODO consider whether to include in polling */null);
                FilePath scriptFile = dir.child(scriptPath);
                if (!scriptFile.absolutize().getRemote().replace('\\', '/').startsWith(dir.absolutize().getRemote().replace('\\', '/') + '/')) { // TODO need some FilePath.isInside(FilePath) method
                    throw new IOException(scriptFile + " is not inside " + dir);
                }
                script = scriptFile.readToString();
            } finally {
                lease.release();
            }
            CpsFlowExecution exec = new CpsFlowExecution(script, true, owner);
            exec.flowStartNodeActions.add(new WorkspaceActionImpl(dir, null));
            return exec;
        } catch (InterruptedException x) {
            throw new IOException(x); // TODO overload should also permit InterruptedException to be thrown
        }
    }

    @Extension public static class DescriptorImpl extends FlowDefinitionDescriptor {

        @Override public String getDisplayName() {
            return "Groovy CPS DSL from SCM";
        }

        public Collection<? extends SCMDescriptor<?>> getApplicableDescriptors() {
            StaplerRequest req = Stapler.getCurrentRequest();
            Job job = req != null ? req.findAncestorObject(Job.class) : null;
            return job != null ? SCM._for(job) : SCM.all();
        }

        // TODO migrate doGenerateSnippet to a helper class

    }

}
