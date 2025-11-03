# TODO 

Injection of the Mutiny session
MixWithOnSessionOnDemandTest
MixWithTransaction
MixReactiveTransactional (old annotation)
MixStatelessStatefulSessionTest
Rename the module to quarkus-reactive-transaction
Panache needs to depend on this module
Move tests related to mixing @Transaction with Panache (e.g. `MixWithOnSessionOnDemandTest`) to Panache itself 

io/quarkus/hibernate/reactive/panache/common/runtime/SessionOperations.java:79 will be used in the interceptor
io/quarkus/hibernate/reactive/panache/common/runtime/SessionOperations.java:196 will be used to inject the Mutiny.Session in here io/quarkus/hibernate/reactive/runtime/HibernateReactiveRecorder.java:102

io/quarkus/hibernate/orm/runtime/HibernateOrmRecorder.java:172