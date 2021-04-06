/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.ast

sealed trait ShowIndexType {
  val prettyPrint: String
  val description: String
}

case object AllIndexes extends ShowIndexType {
  override val prettyPrint: String = "ALL"
  override val description: String = "allIndexes"
}

case object BtreeIndexes extends ShowIndexType {
  override val prettyPrint: String = "BTREE"
  override val description: String = "btreeIndexes"
}

case object FulltextIndexes extends ShowIndexType {
  override val prettyPrint: String = "FULLTEXT"
  override val description: String = "fulltextIndexes"
}