package de.oopexpert.oopdi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.proxy.RequestScopeManager;

/**
 * Verifies {@link ScopedInstances} thread-state management: per-thread states are created
 * on demand and can be dropped wholesale (used by shutdown to stop pooled threads from
 * pinning destroyed beans).
 */
class TestScopedInstances {

    @Test
    void testClearThreadStatesDropsPooledThreadStates() {
        ScopedInstances scopedInstances = new ScopedInstances(new RequestScopeManager());

        InstancesState before = scopedInstances.getScopedInstancesState(Scope.THREAD);
        Assertions.assertFalse(scopedInstances.allInstanceStates().isEmpty());

        scopedInstances.clearThreadStates();

        InstancesState after = scopedInstances.getScopedInstancesState(Scope.THREAD);

        Assertions.assertNotSame(before, after,
            "Clearing must drop the thread state so the next access starts fresh instead of "
            + "pinning the previous (possibly destroyed) instances");
    }
}
