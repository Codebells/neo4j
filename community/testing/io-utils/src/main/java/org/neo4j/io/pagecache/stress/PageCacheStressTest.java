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
package org.neo4j.io.pagecache.stress;

import java.nio.file.Path;

import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.PageSwapperFactory;
import org.neo4j.io.pagecache.impl.SingleFilePageSwapperFactory;
import org.neo4j.io.pagecache.impl.muninn.MuninnPageCache;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.scheduler.JobScheduler;
import org.neo4j.test.scheduler.ThreadPoolJobScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.io.pagecache.impl.muninn.MuninnPageCache.config;
import static org.neo4j.io.pagecache.tracing.PageCacheTracer.NULL;

/**
 * A stress test for page cache(s).
 *
 * The test will stress a page cache by mutating records and keeping an invariant for each record. Thus, before writing
 * to a record, the record is be tested to see if the invariant still holds. Also, at the end of the test all records
 * are verified in that same manner.
 *
 * The test runs using multiple threads. It relies on page cache's exclusive locks to maintain the invariant.
 *
 * The page cache covers a fraction of a file, and the access pattern is uniformly random, so that pages are loaded
 * and evicted frequently.
 *
 * Records: a record is 1x counter for each thread, indexed by the threads' number, with 1x checksum = sum of counters.
 *
 * Invariant: the sum of counters is always equal to the checksum. For a blank file, this is trivially true:
 * sum(0, 0, 0, ...) = 0. Any record mutation is a counter increment and checksum increment.
 */
public class PageCacheStressTest
{
    private final int numberOfPages;
    private final int numberOfThreads;

    private final int numberOfCachePages;

    private final PageCacheTracer tracer;
    private final Condition condition;

    private final Path workingDirectory;

    private PageCacheStressTest( Builder builder )
    {
        this.numberOfPages = builder.numberOfPages;
        this.numberOfThreads = builder.numberOfThreads;

        this.numberOfCachePages = builder.numberOfCachePages;

        this.tracer = builder.tracer;
        this.condition = builder.condition;

        this.workingDirectory = builder.workingDirectory;
    }

    public void run() throws Exception
    {
        try ( FileSystemAbstraction fs = new DefaultFileSystemAbstraction();
              JobScheduler jobScheduler = new ThreadPoolJobScheduler() )
        {
            PageSwapperFactory swapperFactory = new SingleFilePageSwapperFactory( fs, tracer );
            try ( PageCache pageCacheUnderTest = new MuninnPageCache( swapperFactory, jobScheduler, config( numberOfCachePages ).pageCacheTracer( tracer ) ) )
            {
                PageCacheStresser pageCacheStresser = new PageCacheStresser( numberOfPages, numberOfThreads, workingDirectory );
                pageCacheStresser.stress( pageCacheUnderTest, tracer, condition );
            }
        }
    }

    public static class Builder
    {
        int numberOfPages = 10000;
        int numberOfThreads = 7;

        int numberOfCachePages = 1000;

        PageCacheTracer tracer = NULL;
        Condition condition;

        Path workingDirectory;

        public PageCacheStressTest build()
        {
            assertThat( numberOfPages )
                    .describedAs( "the cache should cover only a fraction of the mapped file" )
                    .isGreaterThanOrEqualTo( 10 * numberOfCachePages );
            return new PageCacheStressTest( this );
        }

        public Builder with( PageCacheTracer tracer )
        {
            this.tracer = tracer;
            return this;
        }

        public Builder with( Condition condition )
        {
            this.condition = condition;
            return this;
        }

        public Builder withNumberOfPages( int value )
        {
            this.numberOfPages = value;
            return this;
        }

        public Builder withNumberOfThreads( int numberOfThreads )
        {
            this.numberOfThreads = numberOfThreads;
            return this;
        }

        public Builder withNumberOfCachePages( int numberOfCachePages )
        {
            this.numberOfCachePages = numberOfCachePages;
            return this;
        }

        public Builder withWorkingDirectory( Path workingDirectory )
        {
            this.workingDirectory = workingDirectory;
            return this;
        }
    }
}
