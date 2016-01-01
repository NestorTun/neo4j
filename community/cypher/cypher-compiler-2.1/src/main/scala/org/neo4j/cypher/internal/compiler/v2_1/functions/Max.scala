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
package org.neo4j.cypher.internal.compiler.v2_1.functions

import org.neo4j.cypher.internal.compiler.v2_1._
import ast.convert.ExpressionConverters._
import commands.{expressions => commandexpressions}
import commands.expressions.{Expression => CommandExpression}
import symbols._

case object Max extends AggregatingFunction with SimpleTypedFunction {
  def name = "max"

  val signatures = Vector(
    Signature(argumentTypes = Vector(CTInteger), outputType = CTInteger),
    Signature(argumentTypes = Vector(CTFloat), outputType = CTFloat)
  )

  def asCommandExpression(invocation: ast.FunctionInvocation) = {
    val inner = invocation.arguments(0).asCommandExpression
    val command = commandexpressions.Max(inner)
    if (invocation.distinct)
      commandexpressions.Distinct(command, inner)
    else
      command
  }
}
