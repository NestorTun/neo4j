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
package org.neo4j.internal.batchimport;

import static java.lang.String.format;

import org.neo4j.token.TokenHolders;

public interface ReadBehaviour {
    ReadBehaviour INCLUSIVE_STRICT = new ReadBehaviour() {
        @Override
        public boolean shouldIncludeNode(String[] labels) {
            return true;
        }

        @Override
        public boolean shouldIncludeRelationship(String relationshipType) {
            return true;
        }

        @Override
        public String[] filterLabels(String[] labels) {
            return labels;
        }

        @Override
        public boolean shouldIncludeNodeProperty(String propertyKey, String[] labels) {
            return true;
        }

        @Override
        public boolean shouldIncludeRelationshipProperty(String propertyKey, String relationshipType) {
            return true;
        }

        @Override
        public void unused() {}

        @Override
        public void removed() {}

        @Override
        public void error(String format, Object... parameters) {
            throw new RuntimeException(format(format, parameters));
        }

        @Override
        public void error(Throwable e, String format, Object... parameters) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(format(format, parameters), e);
        }

        @Override
        public TokenHolders decorateTokenHolders(TokenHolders actual) {
            return actual;
        }
    };

    boolean shouldIncludeNode(String[] labels);

    boolean shouldIncludeRelationship(String relationshipType);

    String[] filterLabels(String[] labels);

    boolean shouldIncludeNodeProperty(String propertyKey, String[] labels);

    boolean shouldIncludeRelationshipProperty(String propertyKey, String relationshipType);

    // statistics

    void unused();

    void removed();

    void error(String format, Object... parameters);

    void error(Throwable e, String format, Object... parameters);

    TokenHolders decorateTokenHolders(TokenHolders actual);
}
