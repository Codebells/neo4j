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
package org.neo4j.cypher.internal.runtime

import scala.beans.BeanProperty

// Whenever you add a field here, please also update
// org.neo4j.graphdb.QueryStatistics
// org.neo4j.bolt.runtime.AbstractCypherAdapterStream#queryStats (team drivers does that)
// org.neo4j.cypher.QueryStatisticsTestSupport

case class QueryStatistics(
                            @BeanProperty nodesCreated: Int = 0,
                            @BeanProperty relationshipsCreated: Int = 0,
                            @BeanProperty propertiesSet: Int = 0,
                            @BeanProperty nodesDeleted: Int = 0,
                            @BeanProperty relationshipsDeleted: Int = 0,
                            @BeanProperty labelsAdded: Int = 0,
                            @BeanProperty labelsRemoved: Int = 0,
                            @BeanProperty indexesAdded: Int = 0,
                            @BeanProperty indexesRemoved: Int = 0,
                            uniqueConstraintsAdded: Int = 0,
                            uniqueConstraintsRemoved: Int = 0,
                            existenceConstraintsAdded: Int = 0,
                            existenceConstraintsRemoved: Int = 0,
                            nodekeyConstraintsAdded: Int = 0,
                            nodekeyConstraintsRemoved: Int = 0,
                            namedConstraintsRemoved: Int = 0,
                            transactionsCommitted: Int = 0,
                            @BeanProperty systemUpdates: Int = 0,
                          ) extends org.neo4j.graphdb.QueryStatistics {

  @BeanProperty
  val constraintsAdded: Int = uniqueConstraintsAdded + existenceConstraintsAdded + nodekeyConstraintsAdded

  @BeanProperty
  val constraintsRemoved: Int = uniqueConstraintsRemoved + existenceConstraintsRemoved + nodekeyConstraintsRemoved + namedConstraintsRemoved

  override def containsUpdates: Boolean =
    nodesCreated > 0 ||
      relationshipsCreated > 0 ||
      propertiesSet > 0 ||
      nodesDeleted > 0 ||
      relationshipsDeleted > 0 ||
      labelsAdded > 0 ||
      labelsRemoved > 0 ||
      indexesAdded > 0 ||
      indexesRemoved > 0 ||
      constraintsAdded > 0 ||
      constraintsRemoved > 0

  override def containsSystemUpdates: Boolean = systemUpdates > 0

  override def toString: String = {
    val builder = new StringBuilder

    if (containsSystemUpdates) {
      includeIfNonZero(builder, "System updates: ", systemUpdates)
    } else {
      includeIfNonZero(builder, "Nodes created: ", nodesCreated)
      includeIfNonZero(builder, "Relationships created: ", relationshipsCreated)
      includeIfNonZero(builder, "Properties set: ", propertiesSet)
      includeIfNonZero(builder, "Nodes deleted: ", nodesDeleted)
      includeIfNonZero(builder, "Relationships deleted: ", relationshipsDeleted)
      includeIfNonZero(builder, "Labels added: ", labelsAdded)
      includeIfNonZero(builder, "Labels removed: ", labelsRemoved)
      includeIfNonZero(builder, "Indexes added: ", indexesAdded)
      includeIfNonZero(builder, "Indexes removed: ", indexesRemoved)
      includeIfNonZero(builder, "Unique constraints added: ", uniqueConstraintsAdded)
      includeIfNonZero(builder, "Unique constraints removed: ", uniqueConstraintsRemoved)
      includeIfNonZero(builder, "Property existence constraints added: ", existenceConstraintsAdded)
      includeIfNonZero(builder, "Property existence constraints removed: ", existenceConstraintsRemoved)
      includeIfNonZero(builder, "Node key constraints added: ", nodekeyConstraintsAdded)
      includeIfNonZero(builder, "Node key constraints removed: ", nodekeyConstraintsRemoved)
      includeIfNonZero(builder, "Named constraints removed: ", namedConstraintsRemoved)
      includeIfNonZero(builder, "Transactions committed: ", transactionsCommitted)
    }
    val result = builder.toString()

    if (result.isEmpty) "<Nothing happened>" else result
  }

  private def includeIfNonZero(builder: StringBuilder, message: String, count: Long) = if (count > 0) {
    builder.append(message + count.toString + "\n")
  }

  def +(other: QueryStatistics): QueryStatistics = {
    QueryStatistics(
      nodesCreated = this.nodesCreated + other.nodesCreated,
      relationshipsCreated = this.relationshipsCreated + other.relationshipsCreated,
      propertiesSet = this.propertiesSet + other.propertiesSet,
      nodesDeleted = this.nodesDeleted + other.nodesDeleted,
      relationshipsDeleted = this.relationshipsDeleted + other.relationshipsDeleted,
      labelsAdded = this.labelsAdded + other.labelsAdded,
      labelsRemoved = this.labelsRemoved + other.labelsRemoved,
      indexesAdded = this.indexesAdded + other.indexesAdded,
      indexesRemoved = this.indexesRemoved + other.indexesRemoved,
      uniqueConstraintsAdded = this.uniqueConstraintsAdded + other.uniqueConstraintsAdded,
      uniqueConstraintsRemoved = this.uniqueConstraintsRemoved + other.uniqueConstraintsRemoved,
      existenceConstraintsAdded = this.existenceConstraintsAdded + other.existenceConstraintsAdded,
      existenceConstraintsRemoved = this.existenceConstraintsRemoved + other.existenceConstraintsRemoved,
      nodekeyConstraintsAdded = this.nodekeyConstraintsAdded + other.nodekeyConstraintsAdded,
      nodekeyConstraintsRemoved = this.nodekeyConstraintsRemoved + other.nodekeyConstraintsRemoved,
      namedConstraintsRemoved = this.namedConstraintsRemoved + other.namedConstraintsRemoved,
      transactionsCommitted = this.transactionsCommitted + other.transactionsCommitted,
      systemUpdates = this.systemUpdates + other.systemUpdates,
    )
  }
}

object QueryStatistics {
  val empty: QueryStatistics = QueryStatistics()

  def apply(statistics: org.neo4j.graphdb.QueryStatistics): QueryStatistics = statistics match {
    case q: QueryStatistics => q
    case _ =>
      QueryStatistics(
        nodesCreated = statistics.getNodesCreated,
        nodesDeleted = statistics.getNodesDeleted,
        relationshipsCreated = statistics.getRelationshipsCreated,
        relationshipsDeleted = statistics.getRelationshipsDeleted,
        propertiesSet = statistics.getPropertiesSet,
        labelsAdded = statistics.getLabelsAdded,
        labelsRemoved = statistics.getLabelsRemoved,
        indexesAdded = statistics.getIndexesAdded,
        indexesRemoved = statistics.getIndexesRemoved,
        uniqueConstraintsAdded = statistics.getConstraintsAdded,
        uniqueConstraintsRemoved = statistics.getConstraintsRemoved,
        systemUpdates = statistics.getSystemUpdates,
      )
  }
}
