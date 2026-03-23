# Keyed/Partitioned BuildStep API — Before & After

## TL;DR

```properties
# Default persistence unit + datasource
quarkus.datasource.db-kind=postgresql
quarkus.hibernate-orm.packages=org.acme.model.defaultpu
quarkus.hibernate-orm.schema-management.strategy=drop-and-create

# Named "inventory" persistence unit + datasource
quarkus.datasource."inventory".db-kind=mysql
quarkus.hibernate-orm."inventory".datasource=inventory
quarkus.hibernate-orm."inventory".packages=org.acme.model.inventory
quarkus.hibernate-orm."inventory".schema-management.strategy=update

# Named "audit" persistence unit + datasource
quarkus.datasource."audit".db-kind=h2
quarkus.hibernate-orm."audit".datasource=audit
quarkus.hibernate-orm."audit".packages=org.acme.model.audit
quarkus.hibernate-orm."audit".mapping.format.global=ignore
```

Today:

```java
@BuildStep
void process(
    List<PersistenceUnitDescriptorBuildItem> allDescriptors,
    List<JdbcDataSourceBuildItem> allJdbcDataSources,
    List<ReactiveDataSourceBuildItem> allReactiveDataSources) {

    for (var descriptor : allDescriptors) {
        String name = descriptor.getPersistenceUnitName();
        var jdbcDs = allJdbcDataSources.stream()
            .filter(ds -> ds.getName().equals(name)).findFirst();
        var reactiveDs = allReactiveDataSources.stream()
            .filter(ds -> ds.getName().equals(name)).findFirst();
        // ...
    }
}
```

After:

```java
@BuildStep
@ForEachKey(PersistenceUnitDescriptorBuildItem.class)
void process(
    String key,                                    // "inventory" or "audit"
    PersistenceUnitDescriptorBuildItem descriptor,  // the one for this key
    JdbcDataSourceBuildItem jdbcDataSource,          // the one for this key (or null)
    ReactiveDataSourceBuildItem reactiveDataSource)  // the one for this key (or null)
{
    // called once per key, items pre-filtered
}
```

---

## Problem

In Quarkus, users can define multiple named instances of the same concept in `application.properties`. For example, multiple Hibernate ORM persistence units and their datasources:

```properties
# Default persistence unit + datasource
quarkus.datasource.db-kind=postgresql
quarkus.hibernate-orm.packages=org.acme.model.defaultpu
quarkus.hibernate-orm.schema-management.strategy=drop-and-create

# Named "inventory" persistence unit + datasource
quarkus.datasource."inventory".db-kind=mysql
quarkus.hibernate-orm."inventory".datasource=inventory
quarkus.hibernate-orm."inventory".packages=org.acme.model.inventory
quarkus.hibernate-orm."inventory".schema-management.strategy=update

# Named "audit" persistence unit + datasource
quarkus.datasource."audit".db-kind=h2
quarkus.hibernate-orm."audit".datasource=audit
quarkus.hibernate-orm."audit".packages=org.acme.model.audit
quarkus.hibernate-orm."audit".mapping.format.global=ignore
```

Each persistence unit is fully independent — "inventory" cannot affect "audit" and vice versa. Yet at the framework level, extensions like Hibernate ORM and Agroal receive **all** instances mixed together in flat lists and must manually group/filter them by name. This creates:

1. **Parameter bloat** — methods with 15-20 params, many being `List<KeyedThing>`
2. **Manual multi-list join by key** — the step receives N separate lists, all keyed by the same name, and must manually join them
3. **Obscured pipeline** — the per-key independence is invisible; the code looks like it handles everything at once

---

## The Core Problem: Manual Multi-List Join By Key

The real power of keyed build items isn't just grouping one list — it's when a step consumes **multiple different `List<KeyedBuildItem>` types** that all share the same key, and must manually join them.

### The Killer Example: `producePersistenceUnitDescriptorFromConfig` (line 1057)

This private method is called once per PU from `handleHibernateORMWithNoPersistenceXml`. Look at its **18 parameters** — it receives flat lists of items from completely different extensions and must manually find the one matching this PU in each list:

```java
private static void producePersistenceUnitDescriptorFromConfig(
        HibernateOrmConfig hibernateOrmConfig,
        JpaModelBuildItem jpaModel,
        String persistenceUnitName,                           // <-- the key
        HibernateOrmConfigPersistenceUnit persistenceUnitConfig,
        Set<String> modelClassesAndPackages,
        List<RecordableXmlMapping> xmlMappings,
        List<JdbcDataSourceBuildItem> jdbcDataSources,        // ALL datasources, must filter
        List<ReactiveDataSourceBuildItem> reactiveDataSources, // ALL reactive DS, must filter
        ApplicationArchivesBuildItem applicationArchivesBuildItem,
        LaunchMode launchMode,
        Capabilities capabilities,
        List<SqlLoadScriptDefaultBuildItem> additionalSqlLoadScriptDefaults,  // ALL, must filter
        BuildProducer<SystemPropertyBuildItem> systemProperties,
        BuildProducer<NativeImageResourceBuildItem> nativeImageResources,
        BuildProducer<HotDeploymentWatchedFileBuildItem> hotDeploymentWatchedFiles,
        BuildProducer<PersistenceUnitDescriptorBuildItem> persistenceUnitDescriptors,
        BuildProducer<ReflectiveMethodBuildItem> reflectiveMethods,
        BuildProducer<UnremovableBeanBuildItem> unremovableBeans,
        List<DatabaseKindDialectBuildItem> dbKindMetadataBuildItems) {  // ALL, must filter

    // Step 1: manually find the JDBC datasource for THIS PU
    Optional<JdbcDataSourceBuildItem> jdbcDataSource = HibernateDataSourceUtil.findDataSourceWithNameDefault(
            persistenceUnitName, jdbcDataSources,
            JdbcDataSourceBuildItem::getName,
            JdbcDataSourceBuildItem::isDefault, persistenceUnitConfig.datasource());

    // Step 2: manually find the reactive datasource for THIS PU
    Optional<ReactiveDataSourceBuildItem> reactiveDataSource = HibernateDataSourceUtil.findDataSourceWithNameDefault(
            persistenceUnitName, reactiveDataSources,
            ReactiveDataSourceBuildItem::getName,
            ReactiveDataSourceBuildItem::isDefault, persistenceUnitConfig.datasource());

    // Step 3: manually find dialect metadata for THIS PU
    // (inside collectDialectConfig, which also takes the full list and filters)

    // ... 100+ lines of per-PU logic
}
```

**The pattern**: receive `List<X>`, `List<Y>`, `List<Z>` — all keyed by the same name — then immediately extract the ONE item per key from each list. This is essentially a manual SQL-style JOIN on the key column, repeated in every method.

And the caller (`handleHibernateORMWithNoPersistenceXml`, line 947) has the same 18 parameters just to forward them, plus its own loop:

```java
for (Entry<String, HibernateOrmConfigPersistenceUnit> persistenceUnitEntry :
        hibernateOrmConfig.namedPersistenceUnits().entrySet()) {
    producePersistenceUnitDescriptorFromConfig(
            hibernateOrmConfig, jpaModel, persistenceUnitEntry.getKey(),
            persistenceUnitEntry.getValue(), ...,
            jdbcDataSources, reactiveDataSources,  // passing ALL items through
            ..., dbKindMetadataBuildItems);
}
```

---

## Proposed API

### New base class: `KeyedMultiBuildItem`

```java
public abstract class KeyedMultiBuildItem extends MultiBuildItem {
    private final String key;
    protected KeyedMultiBuildItem(String key) { this.key = Objects.requireNonNull(key); }
    public final String getKey() { return key; }
}
```

### `@ForEachKey` — the step runs once per key, with automatic multi-list join

```java
@BuildStep
@ForEachKey(PersistenceUnitDescriptorBuildItem.class)  // "driver" item defines the key set
void processPersistenceUnit(
    String key,                                              // injected: current PU name
    PersistenceUnitDescriptorBuildItem descriptor,           // THE one for this PU
    JdbcDataSourceBuildItem jdbcDataSource,                  // THE one for this PU (or null)
    ReactiveDataSourceBuildItem reactiveDataSource,          // THE one for this PU (or null)
    List<HibernateOrmIntegrationStaticConfiguredBuildItem> integrations,  // only for this PU
    List<AdditionalJpaModelBuildItem> additionalModels,      // only for this PU
    JpaModelBuildItem jpaModel,                              // global (not keyed) — injected normally
    Capabilities capabilities,                               // global — injected normally
    BuildProducer<SomePUOutputBuildItem> output              // produced items auto-tagged with key
) {
    // This method is called once per persistence unit.
    // ALL keyed params are pre-filtered to this key.
    // ALL non-keyed params (SimpleBuildItem, Capabilities, etc.) injected as usual.
    // No iteration, no filtering, no join — just the logic for ONE PU.
}
```

**The framework does the multi-list join**: for each key in the driver type, it filters every `KeyedMultiBuildItem` parameter to only include items matching that key. A single `KeyedMultiBuildItem` param (not `List`) gets the one item for this key (or null/Optional if none).

### Intermediate step: `Map<String, List<T>>` parameter

For cases where `@ForEachKey` is too aggressive (step needs cross-key logic), a `Map<String, List<T>>` parameter type auto-groups:

```java
@BuildStep
void build(
    List<PersistenceUnitDescriptorBuildItem> descriptors,
    Map<String, List<HibernateOrmIntegrationStaticConfiguredBuildItem>> integrationsByPU,
    ...) {
    // Framework grouped by key — no manual collectDescriptors() needed
}
```

---

## Before/After: The Multi-List Join

### BEFORE: `producePersistenceUnitDescriptorFromConfig` + caller (lines 947-1145)

**~200 lines across 2 methods, 18 parameters each, manual join on persistenceUnitName**

```java
// Caller: loops over PU names, passes ALL lists through
private void handleHibernateORMWithNoPersistenceXml(
        HibernateOrmConfig hibernateOrmConfig,
        CombinedIndexBuildItem index,
        List<PersistenceXmlDescriptorBuildItem> descriptors,
        List<JdbcDataSourceBuildItem> jdbcDataSources,           // ALL
        List<ReactiveDataSourceBuildItem> reactiveDataSources,   // ALL
        ...8 more params...,
        List<DatabaseKindDialectBuildItem> dbKindMetadataBuildItems) {  // ALL

    for (var entry : hibernateOrmConfig.namedPersistenceUnits().entrySet()) {
        producePersistenceUnitDescriptorFromConfig(
                hibernateOrmConfig, jpaModel, entry.getKey(), entry.getValue(), ...,
                jdbcDataSources,        // passing ALL through, just to filter inside
                reactiveDataSources,    // passing ALL through, just to filter inside
                ...,
                dbKindMetadataBuildItems);  // passing ALL through, just to filter inside
    }
}

// Callee: receives ALL lists, immediately filters each one to find the ONE matching item
private static void producePersistenceUnitDescriptorFromConfig(
        ..., String persistenceUnitName, ...,
        List<JdbcDataSourceBuildItem> jdbcDataSources,
        List<ReactiveDataSourceBuildItem> reactiveDataSources,
        ...) {

    // Manual join: find JDBC DS for this PU
    Optional<JdbcDataSourceBuildItem> jdbcDataSource =
        HibernateDataSourceUtil.findDataSourceWithNameDefault(
            persistenceUnitName, jdbcDataSources, ...);

    // Manual join: find reactive DS for this PU
    Optional<ReactiveDataSourceBuildItem> reactiveDataSource =
        HibernateDataSourceUtil.findDataSourceWithNameDefault(
            persistenceUnitName, reactiveDataSources, ...);

    // ... now finally do the actual per-PU logic
}
```

### AFTER: Single `@ForEachKey` build step

```java
@BuildStep
@ForEachKey(HibernateOrmConfigPersistenceUnitBuildItem.class)
void producePersistenceUnitDescriptor(
        String persistenceUnitName,                              // the key
        HibernateOrmConfigPersistenceUnitBuildItem puConfig,     // config for THIS PU
        Optional<JdbcDataSourceBuildItem> jdbcDataSource,        // THE datasource for THIS PU
        Optional<ReactiveDataSourceBuildItem> reactiveDataSource, // THE reactive DS for THIS PU
        List<AdditionalJpaModelBuildItem> additionalModels,      // models for THIS PU
        List<SqlLoadScriptDefaultBuildItem> sqlLoadScriptDefaults, // for THIS PU
        List<DatabaseKindDialectBuildItem> dbKindDialects,       // for THIS PU
        HibernateOrmConfig hibernateOrmConfig,                   // global config
        JpaModelBuildItem jpaModel,                              // global
        Capabilities capabilities,                               // global
        BuildProducer<PersistenceUnitDescriptorBuildItem> descriptors,
        BuildProducer<NativeImageResourceBuildItem> nativeImageResources) {

    // No loops, no filtering, no 18-parameter forwarding.
    // Just the logic for ONE persistence unit.
    // The framework did the multi-list join automatically.
}
```

**What changed**:
- **2 methods (18 params each) collapsed into 1 method (12 params)**
- **Eliminated**: the caller method, the for-loop, 3 manual `findDataSourceWithNameDefault()` calls, the `HibernateDataSourceUtil` helper
- **Each keyed param** (`JdbcDataSourceBuildItem`, `ReactiveDataSourceBuildItem`, `DatabaseKindDialectBuildItem`) goes from `List<T>` → `Optional<T>` or `T`, because the framework already filtered to this key
- **Non-keyed params** (`HibernateOrmConfig`, `Capabilities`, `JpaModelBuildItem`) are injected unchanged

---

## Another Multi-List Join: `HibernateOrmProcessor.build` (line 588)

### BEFORE:
```java
@BuildStep
public void build(...,
        List<PersistenceUnitDescriptorBuildItem> persistenceUnitDescriptorBuildItems,
        List<HibernateOrmIntegrationStaticConfiguredBuildItem> integrationBuildItems, ...) {

    // Manual join: group integrations by PU name
    Map<String, List<HibernateOrmIntegrationStaticDescriptor>> integrationStaticDescriptors =
        HibernateOrmIntegrationStaticConfiguredBuildItem.collectDescriptors(integrationBuildItems);

    for (PersistenceUnitDescriptorBuildItem pud : persistenceUnitDescriptorBuildItems) {
        // Manual lookup: find integrations for THIS PU
        finalStagePUDescriptors.add(pud.asOutputPersistenceUnitDefinition(
            integrationStaticDescriptors
                .getOrDefault(pud.getPersistenceUnitName(), Collections.emptyList())));
    }
}
```

### AFTER:
```java
@BuildStep
@ForEachKey(PersistenceUnitDescriptorBuildItem.class)
public void build(...,
        String persistenceUnitName,
        PersistenceUnitDescriptorBuildItem pud,
        List<HibernateOrmIntegrationStaticConfiguredBuildItem> integrations, // for THIS PU only
        BuildProducer<QuarkusPersistenceUnitDefinitionBuildItem> output, ...) {

    // No collectDescriptors(), no getOrDefault() — integrations already filtered
    output.produce(new QuarkusPersistenceUnitDefinitionBuildItem(
        pud.asOutputPersistenceUnitDefinition(
            integrations.stream().map(i -> i.toDescriptor()).toList())));
}
```

---

## Agroal Multi-List Join: `generateDataSourceBeans` (line 254)

### BEFORE:
```java
for (var config : aggregatedBuildTimeConfigBuildItems) {
    String dataSourceName = config.getName();

    // Manual join: filter JdbcPropertyBuildItems for THIS datasource
    Map<String, String> jdbcProperties = jdbcPropertyBuildItems.stream()
            .filter(p -> dataSourceName.equals(p.dataSourceName()))
            .collect(Collectors.toMap(...));

    // ... per-datasource logic
}
```

### AFTER:
```java
@BuildStep
@ForEachKey(AggregatedDataSourceBuildTimeConfigBuildItem.class)
void generateDataSourceBean(...,
        String dataSourceName,
        AggregatedDataSourceBuildTimeConfigBuildItem config,
        List<JdbcPropertyBuildItem> jdbcProperties, ...) {  // already filtered

    Map<String, String> props = jdbcProperties.stream()
            .collect(Collectors.toMap(...));
    // No .filter() needed
}
```

---

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Key type | `String` | All real-world keys are strings (PU names, datasource names). `BuildItem` forbids generics. |
| `@ForEachKey` driver | Annotation naming the "driver" type | Keys discovered from driver items at execution time. |
| **Multi-list join** | All `KeyedMultiBuildItem` params auto-filtered to current key | This is the core value — eliminates manual join across N lists |
| Single keyed param | `T` or `Optional<T>` instead of `List<T>` | When only one item per key exists (e.g., one datasource per PU) |
| Non-keyed params | Injected normally (global) | `Capabilities`, config objects, recorders — no filtering |
| Cross-key steps | Don't use `@ForEachKey`, use `List<T>` or `Map<String, List<T>>` | Some steps legitimately need all items |
| Backward compat | `KeyedMultiBuildItem extends MultiBuildItem` | Existing `List<MultiBuildItem>` params still work |

## What This Does NOT Change

- The DAG / dependency graph — same structure, same scheduling
- The execution model — `@ForEachKey` runs ONE step that internally loops per key
- Existing step signatures — no existing code breaks; changes are opt-in
- `SimpleBuildItem` — this is only for `MultiBuildItem` with a key dimension
