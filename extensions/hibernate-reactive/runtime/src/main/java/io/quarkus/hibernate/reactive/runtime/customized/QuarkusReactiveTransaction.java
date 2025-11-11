package io.quarkus.hibernate.reactive.runtime.customized;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.hibernate.reactive.logging.impl.Log;
import org.hibernate.reactive.logging.impl.LoggerFactory;
import org.hibernate.reactive.mutiny.Mutiny;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Transaction;

public class QuarkusReactiveTransaction<T> implements Mutiny.Transaction {
    private final SqlConnection connection;
    boolean rollback;

    private static final Log LOG = LoggerFactory.make( Log.class, MethodHandles.lookup() );

    public QuarkusReactiveTransaction(SqlConnection connection) {
        this.connection = connection;
    }

    public Uni<T> execute(Function<Mutiny.Transaction, Uni<T>> work) {
        return executeInTransaction( work ).eventually( () -> clearTransaction(null, null) );
    }

    /**
     * Run the code assuming that a transaction has already started so that we can
     * differentiate an error starting a transaction (and therefore doesn't need to
     * roll back) and an error thrown by the work.
     */
    Uni<T> executeInTransaction(Function<Mutiny.Transaction, Uni<T>> work) {
        return Uni.createFrom()
                .deferred( () -> work.apply( this ) )
                // only flush() if the work completed with no exception
                .call( this::flush ).call( this::beforeCompletion )
                // in the case of an exception or cancellation
                // we need to roll back the transaction
                .onFailure().call( this::rollback ).onCancellation().call( this::rollback )
                // finally, when there was no exception,
                // commit or rollback the transaction
                .call( () -> rollback ? rollback() : commit() ).call( this::afterCompletion );
    }

    Uni<Void> commit() {
        return Uni.createFrom().completionStage( commitTransaction(getVertxTransaction()) );
    }

    // Copied from org/hibernate/reactive/pool/impl/SqlClientConnection.java:305
    public CompletionStage<Void> commitTransaction(io.vertx.sqlclient.Transaction transaction) {
        return transaction.commit()
                .onSuccess( v -> LOG.info( "Transaction committed: " + transaction ) )
                .onFailure( v -> LOG.info( "Failed to commit transaction: " + transaction ) )
                .toCompletionStage();
    }


    Uni<Void> rollback() {
        return Uni.createFrom().completionStage(rollbackTransaction(getVertxTransaction()));
    }

    // Copied from org/hibernate/reactive/pool/impl/SqlClientConnection.java:314
    public CompletionStage<Void> rollbackTransaction(io.vertx.sqlclient.Transaction transaction) {
        return transaction.rollback()
                .onFailure( v -> LOG.info( "Failed to rollback transaction: " + transaction ) )
                .onSuccess( v -> LOG.info( "Transaction rolled back: " + transaction ) )
                .toCompletionStage();
    }

    Uni<Void> flush() {
        return Uni.createFrom().voidItem();
    }

    Future<Transaction> begin() {
        Vertx.currentContext().putLocal("myTransaction", this);
        LOG.info("Starting the transaction ");
        return connection.begin();
    }

    private void clearTransaction(Void unused, Throwable throwable) {
        // Clear the Vertx context
        var context = Vertx.currentContext();
        context.removeLocal("myTransaction");
        System.out.println("Removing the connection");
    }

    private Transaction getVertxTransaction() {
        return connection.transaction();
    }

    private Uni<Void> beforeCompletion() {
        return Uni.createFrom().voidItem();
    }

    private Uni<Void> afterCompletion() {
        return Uni.createFrom().voidItem();
    }

    @Override
    public void markForRollback() {
        rollback = true;
    }

    @Override
    public boolean isMarkedForRollback() {
        return rollback;
    }
}
