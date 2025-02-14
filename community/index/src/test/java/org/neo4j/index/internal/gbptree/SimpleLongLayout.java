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
package org.neo4j.index.internal.gbptree;

import org.apache.commons.lang3.mutable.MutableLong;

import org.neo4j.io.pagecache.PageCursor;

public class SimpleLongLayout extends TestLayout<MutableLong,MutableLong>
{
    private final int keyPadding;

    public static class Builder
    {
        private int keyPadding;
        private int identifier = 999;
        private int majorVersion;
        private int minorVersion;
        private boolean fixedSize = true;

        public Builder withKeyPadding( int keyPadding )
        {
            this.keyPadding = keyPadding;
            return this;
        }

        public Builder withIdentifier( int identifier )
        {
            this.identifier = identifier;
            return this;
        }

        public Builder withMajorVersion( int majorVersion )
        {
            this.majorVersion = majorVersion;
            return this;
        }

        public Builder withMinorVersion( int minorVersion )
        {
            this.minorVersion = minorVersion;
            return this;
        }

        public Builder withFixedSize( boolean fixedSize )
        {
            this.fixedSize = fixedSize;
            return this;
        }

        public SimpleLongLayout build()
        {
            return new SimpleLongLayout( keyPadding, fixedSize, identifier, majorVersion, minorVersion );
        }
    }

    public static Builder longLayout()
    {
        return new Builder();
    }

    private SimpleLongLayout( int keyPadding, boolean fixedSize, int identifier, int majorVersion, int minorVersion )
    {
        super( fixedSize, identifier, majorVersion, minorVersion );
        this.keyPadding = keyPadding;
    }

    @Override
    public int compare( MutableLong o1, MutableLong o2 )
    {
        return Long.compare( o1.longValue(), o2.longValue() );
    }

    @Override
    int compareValue( MutableLong v1, MutableLong v2 )
    {
        return compare( v1, v2 );
    }

    @Override
    public MutableLong newKey()
    {
        return new MutableLong();
    }

    @Override
    public MutableLong copyKey( MutableLong key, MutableLong into )
    {
        into.setValue( key.longValue() );
        return into;
    }

    @Override
    public MutableLong newValue()
    {
        return new MutableLong();
    }

    @Override
    public int keySize( MutableLong key )
    {
        // pad the key here to affect the max key count, useful to get odd or even max key count
        return Long.BYTES + keyPadding;
    }

    @Override
    public int valueSize( MutableLong value )
    {
        return Long.BYTES;
    }

    @Override
    public void writeKey( PageCursor cursor, MutableLong key )
    {
        cursor.putLong( key.longValue() );
        cursor.putBytes( keyPadding, (byte) 0 );
    }

    @Override
    public void writeValue( PageCursor cursor, MutableLong value )
    {
        cursor.putLong( value.longValue() );
    }

    @Override
    public void readKey( PageCursor cursor, MutableLong into, int keySize )
    {
        into.setValue( cursor.getLong() );
        cursor.getBytes( new byte[keyPadding] );
    }

    @Override
    public void readValue( PageCursor cursor, MutableLong into, int valueSize )
    {
        into.setValue( cursor.getLong() );
    }

    @Override
    public MutableLong key( long seed )
    {
        MutableLong key = newKey();
        key.setValue( seed );
        return key;
    }

    @Override
    public MutableLong value( long seed )
    {
        MutableLong value = newValue();
        value.setValue( seed );
        return value;
    }

    @Override
    public long keySeed( MutableLong key )
    {
        return key.getValue();
    }

    @Override
    public long valueSeed( MutableLong value )
    {
        return value.getValue();
    }

    @Override
    public void initializeAsLowest( MutableLong key )
    {
        key.setValue( Long.MIN_VALUE );
    }

    @Override
    public void initializeAsHighest( MutableLong key )
    {
        key.setValue( Long.MAX_VALUE );
    }
}
