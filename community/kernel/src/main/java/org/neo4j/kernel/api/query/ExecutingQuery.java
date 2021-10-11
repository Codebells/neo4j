/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.api.query;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import org.neo4j.graphdb.ExecutionPlanDescription;
import org.neo4j.graphdb.QueryExecutionType;
import org.neo4j.internal.kernel.api.connectioninfo.ClientConnectionInfo;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.kernel.impl.locking.ActiveLock;
import org.neo4j.lock.LockTracer;
import org.neo4j.lock.LockType;
import org.neo4j.lock.LockWaitEvent;
import org.neo4j.lock.ResourceType;
import org.neo4j.memory.OptionalMemoryTracker;
import org.neo4j.resources.CpuClock;
import org.neo4j.time.SystemNanoClock;
import org.neo4j.values.virtual.MapValue;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

/**
 * Represents a currently running query.
 */
public class ExecutingQuery
{
    private static final AtomicLongFieldUpdater<ExecutingQuery> WAIT_TIME =
            newUpdater( ExecutingQuery.class, "waitTimeNanos" );
    private final long queryId;
    private final LockTracer lockTracer = this::waitForLock;
    private final String username;
    private final ClientConnectionInfo clientConnection;
    private final String rawQueryText;
    private final MapValue rawQueryParameters;
    private final long startTimeNanos;
    private final long startTimestampMillis;
    private final Map<String,Object> transactionAnnotationData;
    private final long threadExecutingTheQueryId;
    @SuppressWarnings( {"unused", "FieldCanBeLocal"} )
    private final String threadExecutingTheQueryName;
    private final SystemNanoClock clock;
    private final CpuClock cpuClock;
    private final long cpuTimeNanosWhenQueryStarted;

    /** Uses write barrier of {@link #status}. */
    private CompilerInfo compilerInfo;
    private long compilationCompletedNanos;
    private String obfuscatedQueryText;
    private MapValue obfuscatedQueryParameters;
    private QueryExecutionType.QueryType queryType;
    private Supplier<ExecutionPlanDescription> planDescriptionSupplier;
    private volatile ExecutingQueryStatus status = SimpleState.parsing();
    private volatile ExecutingQuery previousQuery;

    /** Updated through {@link #WAIT_TIME} */
    @SuppressWarnings( "unused" )
    private volatile long waitTimeNanos;

    private OptionalMemoryTracker memoryTracker;
    private TransactionBinding transactionBinding = TransactionBinding.EMPTY;

    // Accumulated statistics of transactions that have executed this query but are already committed
    private long pageHitsOfClosedTransactions;
    private long pageFaultsOfClosedTransactions;

    @Nullable
    private NamedDatabaseId namedDatabaseId;

    private long transactionId = -1L;

    public ExecutingQuery(
            long queryId, ClientConnectionInfo clientConnection, String username, String queryText, MapValue queryParameters,
            Map<String,Object> transactionAnnotationData,
            long threadExecutingTheQueryId, String threadExecutingTheQueryName, SystemNanoClock clock, CpuClock cpuClock, boolean trackQueryAllocations )
    {
        // Capture timestamps first
        this.cpuTimeNanosWhenQueryStarted = cpuClock.cpuTimeNanos( threadExecutingTheQueryId );
        this.startTimeNanos = clock.nanos();
        this.startTimestampMillis = clock.millis();
        // then continue with assigning fields
        this.queryId = queryId;
        this.clientConnection = clientConnection;
        this.username = username;
        this.rawQueryText = queryText;
        this.rawQueryParameters = queryParameters;
        this.transactionAnnotationData = transactionAnnotationData;
        this.threadExecutingTheQueryId = threadExecutingTheQueryId;
        this.threadExecutingTheQueryName = threadExecutingTheQueryName;
        this.clock = clock;
        this.cpuClock = cpuClock;
        this.memoryTracker = trackQueryAllocations ? OptionalMemoryTracker.ZERO : OptionalMemoryTracker.NONE;
    }

    // NOTE: test/benchmarking constructor
    public ExecutingQuery(
            long queryId, ClientConnectionInfo clientConnection, NamedDatabaseId namedDatabaseId, String username, String queryText, MapValue queryParameters,
            Map<String,Object> transactionAnnotationData, LongSupplier activeLockCount, LongSupplier hitsSupplier, LongSupplier faultsSupplier,
            long threadExecutingTheQueryId, String threadExecutingTheQueryName, SystemNanoClock clock, CpuClock cpuClock, boolean trackQueryAllocations )
    {
        this(
                queryId,
                clientConnection,
                username,
                queryText,
                queryParameters,
                transactionAnnotationData,
                threadExecutingTheQueryId,
                threadExecutingTheQueryName,
                clock,
                cpuClock,
                trackQueryAllocations
        );
        onTransactionBound( new TransactionBinding( namedDatabaseId, hitsSupplier, faultsSupplier, activeLockCount ) );
    }

    public static class TransactionBinding
    {
        private final NamedDatabaseId namedDatabaseId;
        private final LongSupplier hitsSupplier;
        private final LongSupplier faultsSupplier;
        private final LongSupplier activeLockCount;
        private final long initialActiveLocks;

        public TransactionBinding( NamedDatabaseId namedDatabaseId,
                                   LongSupplier hitsSupplier,
                                   LongSupplier faultsSupplier,
                                   LongSupplier activeLockCount )
        {
            this.namedDatabaseId = namedDatabaseId;
            this.hitsSupplier = hitsSupplier;
            this.faultsSupplier = faultsSupplier;
            this.activeLockCount = activeLockCount;
            this.initialActiveLocks = activeLockCount.getAsLong();
        }

        public static final TransactionBinding EMPTY =
                new TransactionBinding( null, () -> 0L, () -> 0L, () -> 0L );
    }

    // update state

    public void onTransactionBound( TransactionBinding transactionBinding )
    {
        this.transactionBinding = transactionBinding;
        this.namedDatabaseId = transactionBinding.namedDatabaseId;
    }

    /**
     * Called when a transaction, that this query (or part of this query) has executed in, is about to close.
     * Removes the TransactionBinding for that transaction, after capturing some statistics that we might need even after the transaction has closed.
     */
    public void onTransactionUnbound()
    {
        pageHitsOfClosedTransactions += transactionBinding.hitsSupplier.getAsLong();
        pageFaultsOfClosedTransactions += transactionBinding.faultsSupplier.getAsLong();
        transactionBinding = TransactionBinding.EMPTY;
    }

    public void onObfuscatorReady( QueryObfuscator queryObfuscator )
    {
        if ( status != SimpleState.parsing() ) // might get called multiple times due to caching and/or internal queries
        {
            return;
        }

        try
        {
            obfuscatedQueryText = queryObfuscator.obfuscateText( rawQueryText );
            obfuscatedQueryParameters = queryObfuscator.obfuscateParameters( rawQueryParameters );
        }
        catch ( Exception ignore )
        {
            obfuscatedQueryText = null;
            obfuscatedQueryParameters = null;
        }

        this.status = SimpleState.planning();
    }

    public void onCompilationCompleted( CompilerInfo compilerInfo,
                                        QueryExecutionType.QueryType queryType,
                                        Supplier<ExecutionPlanDescription> planDescriptionSupplier )
    {
        assertExpectedStatus( SimpleState.planning() );

        this.compilerInfo = compilerInfo;
        this.compilationCompletedNanos = clock.nanos();
        this.planDescriptionSupplier = planDescriptionSupplier;
        this.queryType = queryType;
        this.status = SimpleState.planned(); // write barrier - must be last
    }

    public void onExecutionStarted( OptionalMemoryTracker memoryTracker )
    {
        assertExpectedStatus( SimpleState.planned() );

        this.memoryTracker = memoryTracker;
        this.status = SimpleState.running(); // write barrier - must be last
    }

    public void onRetryAttempted()
    {
        assertExpectedStatus( SimpleState.running() );

        this.compilerInfo = null;
        this.compilationCompletedNanos = 0;
        this.planDescriptionSupplier = null;
        this.queryType = null;
        this.memoryTracker = OptionalMemoryTracker.NONE;
        this.obfuscatedQueryParameters = null;
        this.obfuscatedQueryText = null;
        this.status = SimpleState.parsing();
    }

    public LockTracer lockTracer()
    {
        return lockTracer;
    }

    // snapshot state

    public QuerySnapshot snapshot()
    {
        // capture a consistent snapshot of the "live" state
        ExecutingQueryStatus status;
        long waitTimeNanos;
        long currentTimeNanos;
        long cpuTimeNanos;
        String queryText;
        MapValue queryParameters;
        do
        {
            status = this.status; // read barrier, must be first
            waitTimeNanos = this.waitTimeNanos; // the reason for the retry loop: don't count the wait time twice
            cpuTimeNanos = cpuClock.cpuTimeNanos( threadExecutingTheQueryId );
            currentTimeNanos = clock.nanos(); // capture the time as close to the snapshot as possible
            queryText = this.obfuscatedQueryText;
            queryParameters = this.obfuscatedQueryParameters;
        }
        while ( this.status != status );
        // guarded by barrier - unused if status is planning, stable otherwise
        long compilationCompletedNanos = this.compilationCompletedNanos;
        // guarded by barrier - like compilationCompletedNanos
        CompilerInfo planner = status.isParsingOrPlanning() ? null : this.compilerInfo;
        List<ActiveLock> waitingOnLocks = status.isWaitingOnLocks() ? status.waitingOnLocks() : Collections.emptyList();
        // activeLockCount is not atomic to capture, so we capture it after the most sensitive part.
        long totalActiveLocks = transactionBinding.activeLockCount.getAsLong();
        // just needs to be captured at some point...
        var hits = pageHitsOfClosedTransactions + transactionBinding.hitsSupplier.getAsLong();
        var faults = pageFaultsOfClosedTransactions + transactionBinding.faultsSupplier.getAsLong();
        PageCounterValues pageCounters = new PageCounterValues( hits, faults );

        // - at this point we are done capturing the "live" state, and can start computing the snapshot -
        long compilationTimeNanos = (status.isParsingOrPlanning() ? currentTimeNanos : compilationCompletedNanos) - startTimeNanos;
        long elapsedTimeNanos = currentTimeNanos - startTimeNanos;
        cpuTimeNanos -= cpuTimeNanosWhenQueryStarted;
        waitTimeNanos += status.waitTimeNanos( currentTimeNanos );

        return new QuerySnapshot(
                this,
                planner,
                pageCounters,
                NANOSECONDS.toMicros( compilationTimeNanos ),
                NANOSECONDS.toMicros( elapsedTimeNanos ),
                cpuTimeNanos == 0 && cpuTimeNanosWhenQueryStarted == -1 ? -1 : NANOSECONDS.toMicros( cpuTimeNanos ),
                NANOSECONDS.toMicros( waitTimeNanos ),
                status.name(),
                status.toMap( currentTimeNanos ),
                waitingOnLocks,
                totalActiveLocks - transactionBinding.initialActiveLocks,
                memoryTracker.totalAllocatedMemory(),
                Optional.ofNullable( queryText ),
                Optional.ofNullable( queryParameters )
        );
    }

    // basic methods

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        ExecutingQuery that = (ExecutingQuery) o;

        return queryId == that.queryId;
    }

    @Override
    public int hashCode()
    {
        return (int) (queryId ^ (queryId >>> 32));
    }

    @Override
    public String toString()
    {
        return ToStringBuilder.reflectionToString( this );
    }

    // access stable state

    public long internalQueryId()
    {
        return queryId;
    }

    public String id()
    {
        return Long.toString( internalQueryId() );
    }

    public String username()
    {
        return username;
    }

    public String rawQueryText()
    {
        return rawQueryText;
    }

    public MapValue rawQueryParameters()
    {
        return rawQueryParameters;
    }

    Supplier<ExecutionPlanDescription> planDescriptionSupplier()
    {
        return planDescriptionSupplier;
    }

    public Optional<NamedDatabaseId> databaseId()
    {
        return Optional.ofNullable( namedDatabaseId );
    }

    public long startTimestampMillis()
    {
        return startTimestampMillis;
    }

    public long elapsedNanos()
    {
        return clock.nanos() - startTimeNanos;
    }

    public Map<String,Object> transactionAnnotationData()
    {
        return transactionAnnotationData;
    }

    public long reportedWaitingTimeNanos()
    {
        return waitTimeNanos;
    }

    public long totalWaitingTimeNanos( long currentTimeNanos )
    {
        return waitTimeNanos + status.waitTimeNanos( currentTimeNanos );
    }

    ClientConnectionInfo clientConnection()
    {
        return clientConnection;
    }

    private LockWaitEvent waitForLock( LockType lockType, ResourceType resourceType, long userTransactionId, long[] resourceIds )
    {
        WaitingOnLockEvent event = new WaitingOnLockEvent(
                lockType,
                resourceType,
                userTransactionId,
                resourceIds,
                this,
                clock.nanos(),
                status );
        status = event;
        return event;
    }

    void doneWaitingOnLock( WaitingOnLockEvent waiting )
    {
        if ( status != waiting )
        {
            return; // already closed
        }
        WAIT_TIME.addAndGet( this, waiting.waitTimeNanos( clock.nanos() ) );
        status = waiting.previousStatus();
    }

    private void assertExpectedStatus( ExecutingQueryStatus expectedStatus )
    {
        if ( status != expectedStatus )
        {
            throw new IllegalStateException( String.format( "Expected query in '%s' state, actual state is '%s'.", expectedStatus.name(), status.name() ) );
        }
    }

    public ExecutingQuery getPreviousQuery()
    {
        return previousQuery;
    }

    public void setPreviousQuery( ExecutingQuery previousQuery )
    {
        this.previousQuery = previousQuery;
    }
}
