/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.kernel.impl.transaction.xaframework;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.neo4j.kernel.impl.nioneo.xa.command.Command;
import org.neo4j.kernel.impl.util.Consumer;

public class PhysicalTransactionCursor implements TransactionCursor
{
    private final ReadableLogChannel channel;
    private final LogEntryReader<ReadableLogChannel> entryReader;
    private final List<Command> entries = new ArrayList<>();

    public PhysicalTransactionCursor( ReadableLogChannel channel, LogEntryReader<ReadableLogChannel> entryReader )
    {
        this.channel = channel;
        this.entryReader = entryReader;
    }

    @Override
    public boolean next( Consumer<TransactionRepresentation, IOException> consumer ) throws IOException
    {
        entries.clear();
        LogEntry entry = entryReader.readLogEntry( channel );
        assert entry instanceof LogEntry.Start;
        LogEntry.Start startEntry = (LogEntry.Start) entry;
        while ( true )
        {
            entry = entryReader.readLogEntry( channel );
            if ( entry instanceof LogEntry.Commit )
            {
                break;
            }

            entries.add( ((LogEntry.Command) entry).getXaCommand() );
        }

        PhysicalTransactionRepresentation transaction = new PhysicalTransactionRepresentation( entries, true );
        transaction.setHeader( startEntry.getAdditionalHeader(), startEntry.getMasterId(),
                startEntry.getLocalId(), startEntry.getTimeWritten(),
                startEntry.getLastCommittedTxWhenTransactionStarted() );
        return consumer.accept( transaction );
    }

    @Override
    public void close() throws IOException
    {
        channel.close();
    }
}
