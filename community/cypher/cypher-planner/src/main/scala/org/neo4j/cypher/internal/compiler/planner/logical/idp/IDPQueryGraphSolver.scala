/*
 * Copyright (c) 2002-2019 "Neo4j,"
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
package org.neo4j.cypher.internal.compiler.planner.logical.idp

import org.neo4j.cypher.internal.compiler.planner.logical._
import org.neo4j.cypher.internal.compiler.planner.logical.steps.planShortestPaths
import org.neo4j.cypher.internal.ir.{InterestingOrder, QueryGraph}
import org.neo4j.cypher.internal.logical.plans.LogicalPlan

import scala.annotation.tailrec

trait IDPQueryGraphSolverMonitor extends IDPSolverMonitor {
  def noIDPIterationFor(graph: QueryGraph, result: LogicalPlan): Unit
  def initTableFor(graph: QueryGraph): Unit
  def startIDPIterationFor(graph: QueryGraph): Unit
  def endIDPIterationFor(graph: QueryGraph, result: LogicalPlan): Unit
  def emptyComponentPlanned(graph: QueryGraph, plan: LogicalPlan): Unit
  def startConnectingComponents(graph: QueryGraph): Unit
  def endConnectingComponents(graph: QueryGraph, result: LogicalPlan): Unit
}

object IDPQueryGraphSolver {
  val VERBOSE: Boolean = java.lang.Boolean.getBoolean("pickBestPlan.VERBOSE")
}

/**
 * This planner is based on the paper
 *
 *   "Iterative Dynamic Programming: A New Class of Query Optimization Algorithms"
 *
 * written by Donald Kossmann and Konrad Stocker
 */
case class IDPQueryGraphSolver(singleComponentSolver: SingleComponentPlannerTrait,
                               cartesianProductsOrValueJoins: JoinDisconnectedQueryGraphComponents,
                               monitor: IDPQueryGraphSolverMonitor) extends QueryGraphSolver with PatternExpressionSolving {

  private implicit val x: SingleComponentPlannerTrait = singleComponentSolver

  override def plan(queryGraph: QueryGraph, interestingOrder: InterestingOrder, context: LogicalPlanningContext): LogicalPlan = {
    val kit = kitWithShortestPathSupport(context.config.toKit(interestingOrder, context), context)
    val components = queryGraph.connectedComponents
    val plans = if (components.isEmpty) planEmptyComponent(queryGraph, context, kit) else planComponents(components, interestingOrder, context, kit)

    monitor.startConnectingComponents(queryGraph)
    val result = connectComponentsAndSolveOptionalMatch(plans.toSet, queryGraph, interestingOrder, context, kit)
    monitor.endConnectingComponents(queryGraph, result)
    result
  }

  private def kitWithShortestPathSupport(kit: QueryPlannerKit, context: LogicalPlanningContext) =
    kit.copy(select = (initialPlan: LogicalPlan, qg: QueryGraph) => selectShortestPath(kit, initialPlan, qg, context))

  private def selectShortestPath(kit: QueryPlannerKit, initialPlan: LogicalPlan, qg: QueryGraph, context: LogicalPlanningContext): LogicalPlan =
    qg.shortestPathPatterns.foldLeft(kit.select(initialPlan, qg)) {
      case (plan, sp) if sp.isFindableFrom(plan.availableSymbols) =>
        val shortestPath = planShortestPaths(plan, qg, sp, kit.interestingOrder, context)
        kit.select(shortestPath, qg)
      case (plan, _) => plan
    }

  private def planComponents(components: Seq[QueryGraph], interestingOrder: InterestingOrder, context: LogicalPlanningContext, kit: QueryPlannerKit): Seq[PlannedComponent] =
    components.map { qg =>
      PlannedComponent(qg, singleComponentSolver.planComponent(qg, context, kit, interestingOrder))
    }

  private def planEmptyComponent(queryGraph: QueryGraph, context: LogicalPlanningContext, kit: QueryPlannerKit): Seq[PlannedComponent] = {
    val plan = context.logicalPlanProducer.planQueryArgument(queryGraph, context)
    val result: LogicalPlan = kit.select(plan, queryGraph)
    monitor.emptyComponentPlanned(queryGraph, result)
    Seq(PlannedComponent(queryGraph, result))
  }

  private def connectComponentsAndSolveOptionalMatch(plans: Set[PlannedComponent], qg: QueryGraph, interestingOrder: InterestingOrder, context: LogicalPlanningContext, kit: QueryPlannerKit): LogicalPlan = {

    @tailrec
    def recurse(plans: Set[PlannedComponent], optionalMatches: Seq[QueryGraph]): (Set[PlannedComponent], Seq[QueryGraph]) = {
      if (optionalMatches.nonEmpty) {
        // If we have optional matches left to solve - start with that
        val firstOptionalMatch = optionalMatches.head
        val applicablePlan = plans.find(p => firstOptionalMatch.argumentIds subsetOf p.plan.availableSymbols)

        applicablePlan match {
          case Some(t@PlannedComponent(solvedQg, p)) =>
            val candidates = context.config.optionalSolvers.flatMap(solver => solver(firstOptionalMatch, p, interestingOrder, context))
            val best = kit.pickBest(candidates).get
            recurse(plans - t + PlannedComponent(solvedQg, best), optionalMatches.tail)

          case None =>
            // If we couldn't find any optional match we can take on, produce the best cartesian product possible
            recurse(cartesianProductsOrValueJoins(plans, qg, interestingOrder, context, kit, singleComponentSolver), optionalMatches)
        }
      } else if (plans.size > 1) {

        recurse(cartesianProductsOrValueJoins(plans, qg, interestingOrder, context, kit, singleComponentSolver), optionalMatches)
      } else (plans, optionalMatches)
    }

    val (resultingPlans, optionalMatches) = recurse(plans, qg.optionalMatches)
    require(resultingPlans.size == 1)
    require(optionalMatches.isEmpty)
    resultingPlans.head.plan
  }
}

