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
package org.neo4j.values.storable;

import java.time.LocalDate;
import java.util.Arrays;

import org.neo4j.values.AnyValue;
import org.neo4j.values.ValueMapper;

import static org.neo4j.memory.HeapEstimator.LOCAL_DATE_SIZE;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;
import static org.neo4j.memory.HeapEstimator.sizeOfObjectArray;

public final class DateArray extends TemporalArray<LocalDate>
{
    private static final long SHALLOW_SIZE = shallowSizeOfInstance( DateArray.class );

    private final LocalDate[] value;

    DateArray( LocalDate[] value )
    {
        assert value != null;
        this.value = value;
    }

    @Override
    protected LocalDate[] value()
    {
        return value;
    }

    @Override
    public <T> T map( ValueMapper<T> mapper )
    {
        return mapper.mapDateArray( this );
    }

    @Override
    public boolean equals( Value other )
    {
        return other.equals( value );
    }

    @Override
    public boolean equals( LocalDate[] x )
    {
        return Arrays.equals( value, x );
    }

    @Override
    public <E extends Exception> void writeTo( ValueWriter<E> writer ) throws E
    {
        writeTo( writer, ValueWriter.ArrayType.DATE, value );
    }

    @Override
    public ValueRepresentation valueRepresentation()
    {
        return ValueRepresentation.DATE_ARRAY;
    }

    @Override
    protected int unsafeCompareTo( Value otherValue )
    {
        return compareToNonPrimitiveArray( (DateArray) otherValue );
    }

    @Override
    public String getTypeName()
    {
        return "DateArray";
    }

    @Override
    public long estimatedHeapUsage()
    {
        return SHALLOW_SIZE + sizeOfObjectArray( LOCAL_DATE_SIZE, value.length );
    }

    @Override
    public boolean hasCompatibleType( AnyValue value )
    {
        return value instanceof DateValue;
    }

    @Override
    public ArrayValue copyWithAppended( AnyValue added )
    {
        assert hasCompatibleType( added ) : "Incompatible types";
        LocalDate[] newArray = Arrays.copyOf( value, value.length + 1 );
        newArray[value.length] = ((DateValue) added).temporal();
        return new DateArray( newArray );
    }

    @Override
    public ArrayValue copyWithPrepended( AnyValue prepended )
    {
        assert hasCompatibleType( prepended ) : "Incompatible types";
        LocalDate[] newArray = new LocalDate[value.length + 1];
        System.arraycopy( value, 0, newArray, 1, value.length );
        newArray[0] = ((DateValue) prepended).temporal();
        return new DateArray( newArray );
    }
}
