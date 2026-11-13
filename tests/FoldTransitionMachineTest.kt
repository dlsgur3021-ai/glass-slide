package com.portalopen.app

private fun assertOpen(expected: Boolean, machine: FoldTransitionMachine,
                       stage: FoldStage, time: Long) {
    val observed = machine.observe(stage, time, "test")
    check(observed == expected) { "stage=$stage time=$time expected=$expected actual=$observed" }
}

fun main() {
    run {
        val m = FoldTransitionMachine()
        assertOpen(false, m, FoldStage.OPEN, 100) // launch while already unfolded
        check(m.consumeIfRecent(150) == null)
        assertOpen(false, m, FoldStage.OPEN, 200) // lock/unlock / app resume
        check(m.totalOpenEvents == 0)
    }
    run {
        val m = FoldTransitionMachine()
        assertOpen(false, m, FoldStage.CLOSED, 100)
        assertOpen(false, m, FoldStage.HALF_OPENED, 500)
        assertOpen(true, m, FoldStage.OPEN, 1100)
        check(m.consumeIfRecent(1300) == 1)
        check(m.consumeIfRecent(1400) == null) // no repeats
        assertOpen(false, m, FoldStage.OPEN, 1500)
        check(m.totalOpenEvents == 1)
        assertOpen(false, m, FoldStage.CLOSED, 2000)
        assertOpen(true, m, FoldStage.OPEN, 2500)
        check(m.consumeIfRecent(2501) == 2)
    }
    run {
        val m = FoldTransitionMachine(pendingLifetimeMs = 4000)
        assertOpen(false, m, FoldStage.CLOSED, 10)
        assertOpen(true, m, FoldStage.OPEN, 20)
        check(m.consumeIfRecent(4500) == null) // stale, no unlock replay
        assertOpen(false, m, FoldStage.CLOSED, 5000)
        assertOpen(true, m, FoldStage.OPEN, 5100)
        assertOpen(false, m, FoldStage.CLOSED, 5200) // close again cancels pending
        check(m.consumeIfRecent(5300) == null)
    }
    run {
        val m = FoldTransitionMachine()
        assertOpen(false, m, FoldStage.HALF_OPENED, 10)
        assertOpen(false, m, FoldStage.OPEN, 20) // no closed baseline
        assertOpen(false, m, FoldStage.UNKNOWN, 30)
        check(m.consumeIfRecent(40) == null)
    }
    run {
        // Official Galaxy Fold8 screen resolutions (rotation must not change the result).
        check(FoldScreenAspect.classify(1248, 1972) == FoldStage.CLOSED)
        check(FoldScreenAspect.classify(1972, 1248) == FoldStage.CLOSED)
        check(FoldScreenAspect.classify(1848, 2448) == FoldStage.OPEN)
        check(FoldScreenAspect.classify(2448, 1848) == FoldStage.OPEN)
        check(FoldScreenAspect.classify(690, 1000) == FoldStage.UNKNOWN)
        check(FoldScreenAspect.classify(0, 0) == FoldStage.UNKNOWN)
    }
    println("PASS 5 scenarios: baseline, deduplication, expiration/cancel, unknown, Fold8 screen ratios")
}
