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
package org.neo4j.bolt.v44.messaging.request;

import java.util.List;

import org.neo4j.bolt.runtime.Bookmark;
import org.neo4j.values.virtual.MapValue;

public class RouteMessage extends org.neo4j.bolt.v43.messaging.request.RouteMessage
{
    private final String impersonatedUser;

    public RouteMessage( MapValue requestContext, List<Bookmark> bookmarks,
                         String databaseName, String impersonatedUser )
    {
        super( requestContext, bookmarks, databaseName );
        this.impersonatedUser = impersonatedUser;
    }

    public String impersonatedUser()
    {
        return impersonatedUser;
    }
}
