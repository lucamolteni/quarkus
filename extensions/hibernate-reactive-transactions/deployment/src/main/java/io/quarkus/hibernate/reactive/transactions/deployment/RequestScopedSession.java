package io.quarkus.hibernate.reactive.transactions.deployment;

import jakarta.enterprise.context.RequestScoped;

import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.mutiny.delegation.MutinySessionDelegator;

/**
 * Allows a reactive {@linkplain Mutiny.StatelessSession stateless session}
 * to be associated with the current reactive request scope. This object is
 * a wrapper for the current stateless session.
 */
@RequestScoped
public class RequestScopedSession extends MutinySessionDelegator {

    private Mutiny.Session session;

    /**
     * Associate the given session with the request scope,
     * or disassociate it if the given session is null.
     */
    public void setSession(Mutiny.Session session) {
        if (this.session != null && session != null && this.session != session) {
            throw new IllegalStateException("Session already set");
        }
        this.session = session;
    }

    /**
     * Delegate operations of {@link Mutiny.StatelessSession}
     * to the session we've been given.
     */
    @Override
    public Mutiny.Session delegate() {
        if (session == null) {
            throw new IllegalStateException("no session");
        }
        return session;
    }
}
