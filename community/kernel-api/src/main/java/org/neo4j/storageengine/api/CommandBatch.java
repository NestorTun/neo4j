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
package org.neo4j.storageengine.api;

import org.neo4j.common.Subject;
import org.neo4j.kernel.KernelVersion;

/**
 * Representation of a transaction that can be written to transaction log and read back later.
 */
public interface CommandBatch extends CommandStream {
    /**
     * @return an additional header of this transaction. Just arbitrary bytes that means nothing
     * to this transaction representation.
     */
    byte[] additionalHeader();

    /**
     * @return time when transaction was started, i.e. when the user started it, not when it was committed.
     * Reported in milliseconds.
     */
    long getTimeStarted();

    /**
     * @return last committed transaction id at the time when this transaction was started.
     */
    long getLatestCommittedTxWhenStarted();

    /**
     * @return time when transaction was committed. Reported in milliseconds.
     */
    long getTimeCommitted();

    /**
     * @return the identifier for the lease associated with this transaction.
     * This is only used for coordinating transaction validity in a cluster.
     */
    int getLeaseId();

    /**
     * @return the subject associated with the transaction.
     * Typically an authenticated end user that created the transaction.
     */
    Subject subject();

    KernelVersion version();

    /**
     * A to-string method that may include information about all the commands in this transaction.
     * @param includeCommands whether to include commands in the returned string.
     * @return information about this transaction representation w/ or w/o command information included.
     */
    String toString(boolean includeCommands);

    /**
     * True if command batch is the last batch in the sequence of transactional command batches.
     */
    boolean isLast();

    /**
     * True if command batch is the first batch in the sequence of transactional command batches.
     */
    boolean isFirst();
}
