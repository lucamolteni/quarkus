package io.quarkus.deployment.component;

import java.util.function.Function;

import io.quarkus.deployment.component.ComponentLookup.LookupResult;
import io.quarkus.runtime.util.ProgrammingParadigm;

/**
 * A single availability check for a specific {@link ProgrammingParadigm}.
 * Given a component name, returns {@link ComponentLookup#AVAILABLE} or
 * {@link ComponentLookup#unavailable(io.quarkus.runtime.util.Reason)} with a descriptive reason.
 */
public class AvailabilityRule {

    private final ProgrammingParadigm paradigm;
    private final Function<String, LookupResult> check;

    public AvailabilityRule(ProgrammingParadigm paradigm, Function<String, LookupResult> check) {
        this.paradigm = paradigm;
        this.check = check;
    }

    ProgrammingParadigm paradigm() {
        return paradigm;
    }

    LookupResult evaluate(String name) {
        return check.apply(name);
    }

}
