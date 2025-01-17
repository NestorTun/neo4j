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
package org.neo4j.cypher.internal.runtime.interpreted.commands

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.interpreted.QueryStateHelper
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.Not
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.Ors
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.Predicate
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.True
import org.neo4j.cypher.internal.util.NonEmptyList
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class OrsTest extends CypherFunSuite {
  private val state = QueryStateHelper.empty
  private val ctx = CypherRow.empty

  private val nullPredicate = mock[Predicate]
  when(nullPredicate.isMatch(ctx, state)).thenReturn(None)
  when(nullPredicate.children).thenReturn(Seq.empty)
  private val explodingPredicate = mock[Predicate]
  when(explodingPredicate.isMatch(any(), any())).thenThrow(new IllegalStateException("there is something wrong"))
  when(explodingPredicate.children).thenReturn(Seq.empty)

  test("should return null if there are no true values and one or more nulls") {
    ors(F, nullPredicate).isMatch(ctx, state) should equal(None)
  }

  test("should quit early when finding a true value") {
    ors(T, explodingPredicate).isMatch(ctx, state) should equal(Some(true))
  }

  test("should return false if all predicates evaluate to false") {
    ors(F, F).isMatch(ctx, state) should equal(Some(false))
  }

  test("should return true instead of null") {
    ors(nullPredicate, T).isMatch(ctx, state) should equal(Some(true))
  }

  private def ors(predicate: Predicate, predicates: Predicate*) = Ors(NonEmptyList(predicate, predicates: _*))
  private def T = True()
  private def F = Not(True())
}
