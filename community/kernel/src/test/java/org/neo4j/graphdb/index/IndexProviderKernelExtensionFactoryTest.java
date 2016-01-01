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
package org.neo4j.graphdb.index;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.kernel.ListIndexIterable;
import org.neo4j.test.ImpermanentDatabaseRule;

public class IndexProviderKernelExtensionFactoryTest
{
    private boolean loaded = false;

    @Rule
    public ImpermanentDatabaseRule db = new ImpermanentDatabaseRule()
    {
        @Override
        protected void configure( GraphDatabaseFactory databaseFactory )
        {
            ListIndexIterable indexProviders = new ListIndexIterable();
            indexProviders.setIndexProviders( Iterables.toList(Iterables.<IndexProvider, IndexProvider>iterable( new TestIndexProvider() )) );
            databaseFactory.setIndexProviders( indexProviders );
        }
    };

    @Test
    public void testIndexProviderKernelExtensionFactory()
    {
        Assert.assertThat(loaded, CoreMatchers.equalTo( true ));
    }

    private class TestIndexProvider
        extends IndexProvider
    {
        private TestIndexProvider( )
        {
            super( "TEST");
        }

        @Override
        public IndexImplementation load( DependencyResolver dependencyResolver ) throws Exception
        {
            loaded = true;
            return null;
        }
    }
}
