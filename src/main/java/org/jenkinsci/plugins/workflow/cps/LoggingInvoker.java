/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import com.cloudbees.groovy.cps.impl.CallSiteBlock;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import java.util.function.Supplier;

/**
 * Captures CPS-transformed events.
 */
final class LoggingInvoker implements Invoker {

    private final Invoker delegate;

    LoggingInvoker(Invoker delegate) {
        this.delegate = delegate;
    }

    private void record(String call) {
        CpsThreadGroup g = CpsThreadGroup.current();
        if (g == null) {
            assert false : "should never happen";
            return;
        }
        CpsFlowExecution execution = g.getExecution();
        if (execution == null) {
            assert false : "should never happen";
            return;
        }
        execution.recordInternalCall(call);
    }

    private static boolean isInternal(Class<?> clazz) {
        // TODO more precise would be the logic in jenkins.security.ClassFilterImpl.isLocationWhitelisted
        // (simply checking whether the class loader can “see”, say, jenkins/model/Jenkins.class
        // would falsely mark third-party libs bundled in Jenkins plugins)
        String name = clazz.getName();
        // acc. to `find …/jenkinsci/*/src/main/java -type f -exec egrep -h '^package ' {} \; | sort | uniq` this is decent
        return name.contains("jenkins") || name.contains("hudson") || name.contains("cloudbees");
    }

    private void maybeRecord(Class<?> clazz, Supplier<String> message) {
        if (isInternal(clazz)) {
            record(message.get());
        }
    }

    private void maybeRecord(Object o, Supplier<String> message) {
        if (o != null && isInternal(o.getClass())) {
            record(message.get());
        }
    }

    @Override public Object methodCall(Object receiver, String method, Object[] args) throws Throwable {
        maybeRecord(receiver, () -> receiver.getClass().getName() + "." + method);
        return delegate.methodCall(receiver, method, args);
    }

    @Override public Object constructorCall(Class lhs, Object[] args) throws Throwable {
        maybeRecord(lhs, () -> lhs.getName() + ".<init>");
        return delegate.constructorCall(lhs, args);
    }

    @Override public Object superCall(Class senderType, Object receiver, String method, Object[] args) throws Throwable {
        maybeRecord(senderType, () -> senderType.getName() + ".<init>");
        return delegate.superCall(senderType, receiver, method, args);
    }

    @Override public Object getProperty(Object lhs, String name) throws Throwable {
        maybeRecord(lhs, () -> lhs.getClass().getName() + "." + name);
        return delegate.getProperty(lhs, name);
    }

    @Override public void setProperty(Object lhs, String name, Object value) throws Throwable {
        maybeRecord(lhs, () -> lhs.getClass().getName() + "." + name);
        delegate.setProperty(lhs, name, value);
    }

    @Override public Object getAttribute(Object lhs, String name) throws Throwable {
        maybeRecord(lhs, () -> lhs.getClass().getName() + "." + name);
        return delegate.getAttribute(lhs, name);
    }

    @Override public void setAttribute(Object lhs, String name, Object value) throws Throwable {
        maybeRecord(lhs, () -> lhs.getClass().getName() + "." + name);
        delegate.setAttribute(lhs, name, value);
    }

    @Override public Object getArray(Object lhs, Object index) throws Throwable {
        return delegate.getArray(lhs, index);
    }

    @Override public void setArray(Object lhs, Object index, Object value) throws Throwable {
        delegate.setArray(lhs, index, value);
    }

    @Override public Object methodPointer(Object lhs, String name) {
        maybeRecord(lhs, () -> lhs.getClass().getName() + "." + name);
        return delegate.methodPointer(lhs, name);
    }

    @Override public Invoker contextualize(CallSiteBlock tags) {
        Invoker contextualized = delegate.contextualize(tags);
        return contextualized instanceof LoggingInvoker ? contextualized : new LoggingInvoker(contextualized);
    }

}
