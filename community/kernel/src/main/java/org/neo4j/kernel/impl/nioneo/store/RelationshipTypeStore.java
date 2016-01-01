/**
 * Copyright (c) 2002-2016 "Neo Technology,"
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
package org.neo4j.kernel.impl.nioneo.store;

import java.io.File;

import org.neo4j.kernel.IdGeneratorFactory;
import org.neo4j.kernel.IdType;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.nioneo.store.windowpool.WindowPoolFactory;
import org.neo4j.kernel.impl.util.StringLogger;

/**
 * Implementation of the relationship type store. Uses a dynamic store to store
 * relationship type names.
 */
public class RelationshipTypeStore extends AbstractNameStore<RelationshipTypeRecord>
{
    public static abstract class Configuration
        extends AbstractNameStore.Configuration
    {
    }

    public static final String TYPE_DESCRIPTOR = "RelationshipTypeStore";
    private static final int RECORD_SIZE = 1/*inUse*/ + 4/*nameId*/;

    public RelationshipTypeStore(File fileName, Config config,
                                 IdGeneratorFactory idGeneratorFactory, WindowPoolFactory windowPoolFactory,
                                 FileSystemAbstraction fileSystemAbstraction, StringLogger stringLogger,
                                 DynamicStringStore nameStore)
    {
        super(fileName, config, IdType.RELATIONSHIP_TYPE, idGeneratorFactory, windowPoolFactory,
                fileSystemAbstraction, stringLogger, nameStore);
    }

    @Override
    public void accept( RecordStore.Processor processor, RelationshipTypeRecord record )
    {
        processor.processRelationshipType(this, record);
    }

    void markAsReserved( int id )
    {
        PersistenceWindow window = acquireWindow( id, OperationType.WRITE );
        try
        {
            markAsReserved( id, window );
        }
        finally
        {
            releaseWindow( window );
        }
    }

    private void markAsReserved( int id, PersistenceWindow window )
    {
        Buffer buffer = window.getOffsettedBuffer( id );
        buffer.put( Record.IN_USE.byteValue() ).putInt(
            Record.RESERVED.intValue() );
    }

    @Override
    protected RelationshipTypeRecord newRecord( int id )
    {
        return new RelationshipTypeRecord( id );
    }

    @Override
    public int getRecordSize()
    {
        return RECORD_SIZE;
    }

    @Override
    public String getTypeDescriptor()
    {
        return TYPE_DESCRIPTOR;
    }

    @Override
    protected boolean reserveIdsDuringRebuild()
    {
        return true;
    }
}
