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
package org.neo4j.bolt.protocol.common.message.encoder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.neo4j.bolt.protocol.common.message.result.ResponseHandler;
import org.neo4j.values.storable.Values;

class DiscardingRecordMessageWriterTest {

    @Test
    void shouldInvokeParentWhenMetadataIsPassed() {
        var parent = mock(ResponseHandler.class);
        var child = new DiscardingRecordMessageWriter(parent);

        child.addMetadata("the_answer", Values.longValue(42));
        child.addMetadata("foo", Values.stringValue("bar"));

        verify(parent).onMetadata("the_answer", Values.longValue(42));
        verify(parent).onMetadata("foo", Values.stringValue("bar"));
        verifyNoMoreInteractions(parent);
    }
}
