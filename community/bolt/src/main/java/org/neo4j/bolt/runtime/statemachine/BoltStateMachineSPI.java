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
package org.neo4j.bolt.runtime.statemachine;

import java.util.Map;

import org.neo4j.bolt.runtime.Neo4jError;
import org.neo4j.bolt.security.auth.AuthenticationException;
import org.neo4j.bolt.security.auth.AuthenticationResult;
import org.neo4j.internal.kernel.api.security.LoginContext;

public interface BoltStateMachineSPI
{
    TransactionStateMachineSPIProvider transactionStateMachineSPIProvider();

    void reportError( Neo4jError err );

    AuthenticationResult authenticate( Map<String,Object> authToken ) throws AuthenticationException;

    LoginContext impersonate( LoginContext context, String userToImpersonate ) throws AuthenticationException;

    String version();
}
