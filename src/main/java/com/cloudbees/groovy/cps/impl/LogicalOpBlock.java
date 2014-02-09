package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * Logical operator (&& and ||)
 *
 * @author Kohsuke Kawaguchi
 */
public class LogicalOpBlock implements Block {
    private final Block lhs, rhs;
    private final boolean and;

    public LogicalOpBlock(Block lhs, Block rhs, boolean and) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.and = and;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e,k).then(lhs,e,decide);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next decide(Object lhs) {
            boolean v = asBoolean(lhs);
            if (and) {
                if (!v)     return k.receive(false);    // false && ...
                else        return then(rhs,e,k);
            } else {
                if (v)      return k.receive(true);    // true || ...
                else        return then(rhs,e,k);
            }
        }
    }

    static final ContinuationPtr decide = new ContinuationPtr(ContinuationImpl.class,"decide");
}
