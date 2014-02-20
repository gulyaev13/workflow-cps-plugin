package com.cloudbees.groovy.cps

import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class ContinuableTest extends AbstractGroovyCpsTest {
    @Test
    void resumeAndSuspend() {
        def inv = parseCps("""
            int x = 1;
            x = Continuable.suspend(x+1)
            return x+1;
        """)

        def c = new Continuable(inv.invoke(null,Continuation.HALT));
        assert c.isResumable()

        def v = c.run(null);
        assert v==2 : "Continuable.suspend(x+1) returns the control back to us";

        assert c.isResumable() : "Continuable is resumable because it has 'return x+1' to execute."
        assert c.run(3)==4 : "We resume continuable, then the control comes back from the return statement"

        assert !c.isResumable() : "We've run the program till the end, so it's no longer resumable"
    }
}
