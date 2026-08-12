package io.quarkus.deployment.component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.quarkus.runtime.util.ProgrammingParadigm;
import io.quarkus.runtime.util.Reason;

/**
 * Singleton that accumulates {@link AvailabilityRule}s determining whether a named component
 * (e.g. a datasource or a persistence unit) can be created for a given {@link ProgrammingParadigm}.
 * <p>
 * Extensions register rules via {@link #checkAvailability(AvailabilityRule)} during the build.
 * When a consumer later asks whether a component is available, all matching rules are evaluated
 * and any that return {@link LookupResult.Unavailable} produce a meaningful error message
 * explaining why the component cannot be created.
 * <p>
 * The procedural API allows splitting availability checks into small, independent rules
 * rather than a single monolithic method with deeply nested branches.
 * <p>
 * Note: lookups provide information on a best-effort basis.
 * Subtle misconfiguration can still cause a component to fail even if
 * the lookup advertises it as available.
 */
public class ComponentLookup {

    public sealed interface LookupResult {
        record Unavailable(Reason reason) implements LookupResult {
        }

        record Available() implements LookupResult {
        }
    }

    public static final LookupResult AVAILABLE = new LookupResult.Available();

    public static LookupResult unavailable(Reason reason) {
        return new LookupResult.Unavailable(reason);
    }

    private final List<AvailabilityRule> rules = new ArrayList<>();

    public void checkAvailability(AvailabilityRule rule) {
        rules.add(rule);
    }

    public Set<ProgrammingParadigm> availableParadigms(String name) {
        var result = EnumSet.allOf(ProgrammingParadigm.class);
        result.removeIf(paradigm -> !unavailableReasons(name, paradigm).isEmpty());
        return result;
    }

    public List<Reason> unavailableReasons(String name, ProgrammingParadigm paradigm) {
        return rules.stream()
                .filter(rule -> rule.paradigm() == paradigm)
                .map(rule -> rule.evaluate(name))
                .filter(LookupResult.Unavailable.class::isInstance)
                .map(LookupResult.Unavailable.class::cast)
                .map(LookupResult.Unavailable::reason)
                .toList();
    }

}
