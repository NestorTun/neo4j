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
package org.neo4j.internal.kernel.api.helpers;

import java.util.function.Predicate;
import org.eclipse.collections.api.iterator.LongIterator;
import org.neo4j.collection.trackable.HeapTrackingArrayList;
import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap;
import org.neo4j.internal.helpers.collection.PrefetchingIterator;
import org.neo4j.values.virtual.PathReference;
import org.neo4j.values.virtual.VirtualValues;

/**
 * This class meant to be used with {@link BiDirectionalBFS}. When {@link BiDirectionalBFS} has found an intersection,
 * it will use this class to retrace the paths through that intersection.
 *
 *
 * Implementation details (assumes usage from {@link BiDirectionalBFS}):
 * <p>
 * Retracing paths is done in two parts. The first part consists of retracing
 * the shortest paths from an intersection node to the source node (the start node of the sourceBFS),
 * and the second part is retracing the shortest paths from the same intersection node
 * to the target node. The sets of paths from each part can then be combined in a cartesian fashion to construct the
 * set of all shortest paths that contain the particular intersection node. This is then repeated
 * for each node in the intersection. This is the logic you will find in {@link PathTracingIterator#viewNextPath()}.
 * <p>
 * The parts described above are represented as {@link PathIteratorPart} instances.
 * <p>
 * Another detail is that the {@link PathTracingIterator#innerLoopPathPart} and {@link PathTracingIterator#outerLoopPathPart}
 * don't maintain their own arrays to store the paths. They modify {@link PathTracingIterator#internalNodes}
 * and {@link PathTracingIterator#internalRels} directly.
 */
public class PathTracingIterator extends PrefetchingIterator<PathReference> {
    private final int pathLength;

    private final int intersectionNodeIndex;
    private final LongIterator intersectionIterator;
    private final Predicate<PathReference> pathFilter;

    private final PathIteratorPart innerLoopPathPart;
    private final PathIteratorPart outerLoopPathPart;
    private final long[] internalNodes;
    private final long[] internalRels;

    private boolean consumedFirstPath = false;
    private boolean finished = false;

    public PathTracingIterator(
            LongIterator intersectionIterator,
            int sourceBFSDepth,
            int targetBFSDepth,
            HeapTrackingLongObjectHashMap<HeapTrackingArrayList<BiDirectionalBFS.PathTraceStep>> sourcePathTraceData,
            HeapTrackingLongObjectHashMap<HeapTrackingArrayList<BiDirectionalBFS.PathTraceStep>> targetPathTraceData,
            Predicate<PathReference> pathFilter) {
        this.intersectionIterator = intersectionIterator;
        this.pathFilter = pathFilter;
        this.pathLength = sourceBFSDepth + targetBFSDepth;
        this.intersectionNodeIndex = sourceBFSDepth;
        this.internalNodes = new long[pathLength + 1];
        this.internalRels = new long[pathLength];

        PathIteratorPart sourcePathPart = new PathIteratorPart(sourcePathTraceData, sourceBFSDepth, false);
        PathIteratorPart targetPathPart = new PathIteratorPart(targetPathTraceData, targetBFSDepth, true);

        setNextIntersectionNode();
        sourcePathPart.resetPathPartToIntersection();
        targetPathPart.resetPathPartToIntersection();

        if (sourceBFSDepth > targetBFSDepth) {
            innerLoopPathPart = targetPathPart;
            outerLoopPathPart = sourcePathPart;
        } else {
            innerLoopPathPart = sourcePathPart;
            outerLoopPathPart = targetPathPart;
        }
    }

    @Override
    protected PathReference fetchNextOrNull() {
        while (viewNextPath()) {
            if (pathFilter.test(VirtualValues.pathReference(internalNodes, internalRels))) {
                return currentPath();
            }
        }
        return null;
    }

    private boolean viewNextPath() {
        if (finished) {
            return false;
        } else if (!consumedFirstPath) {
            consumedFirstPath = true;
            return true;
        } else if (innerLoopPathPart.viewNextPath()) {
            return true;
        } else if (outerLoopPathPart.viewNextPath()) {
            innerLoopPathPart.resetPathPartToIntersection();
            return true;
        } else if (setNextIntersectionNode()) {
            innerLoopPathPart.resetPathPartToIntersection();
            outerLoopPathPart.resetPathPartToIntersection();
            return true;
        }
        finished = true;
        return false;
    }

    private boolean setNextIntersectionNode() {
        if (!intersectionIterator.hasNext()) {
            return false;
        }
        internalNodes[intersectionNodeIndex] = intersectionIterator.next();
        return true;
    }

    private PathReference currentPath() {
        return VirtualValues.pathReference(internalNodes.clone(), internalRels.clone());
    }

    private class PathIteratorPart {
        private final int pathPartLength;
        private final HeapTrackingLongObjectHashMap<HeapTrackingArrayList<BiDirectionalBFS.PathTraceStep>>
                pathTraceData;
        private final boolean reversed;
        private final int[] pathsToHereActiveIndices;

        public PathIteratorPart(
                HeapTrackingLongObjectHashMap<HeapTrackingArrayList<BiDirectionalBFS.PathTraceStep>> pathTraceData,
                int pathPartLength,
                boolean reversed) {

            this.pathTraceData = pathTraceData;
            this.reversed = reversed;

            this.pathPartLength = pathPartLength;
            this.pathsToHereActiveIndices = new int[pathPartLength];
        }

        // Lots of confusing indexing going on when we are in the reversed pattern part. We compartmentalize
        // it all in the following 3 methods.
        private void updateInternalNodes(long nodeId, int pathPartIndexOfNode) {
            int internalNodesIndex = reversed ? pathLength - pathPartIndexOfNode : pathPartIndexOfNode;
            internalNodes[internalNodesIndex] = nodeId;
        }

        private long getInternalNode(int pathPartIndexOfNode) {
            int internalNodesIndex = reversed ? pathLength - pathPartIndexOfNode : pathPartIndexOfNode;
            return internalNodes[internalNodesIndex];
        }

        private void updateInternalRelsToNode(long relId, int pathPartIndexOfNode) {
            int internalRelsIndex = reversed ? (pathLength - 1) - (pathPartIndexOfNode - 1) : pathPartIndexOfNode - 1;
            internalRels[internalRelsIndex] = relId;
        }

        private BiDirectionalBFS.PathTraceStep getActivePathToNode(int pathPartIndexOfNode) {
            long node = getInternalNode(pathPartIndexOfNode);
            return pathTraceData.get(node).get(pathsToHereActiveIndices[pathPartIndexOfNode - 1]);
        }

        private boolean activateNextPathPartToNode(int pathPartIndexOfNode) {
            long node = getInternalNode(pathPartIndexOfNode);
            HeapTrackingArrayList<BiDirectionalBFS.PathTraceStep> pathsToNode = pathTraceData.get(node);
            if (pathsToHereActiveIndices[pathPartIndexOfNode - 1] == pathsToNode.size() - 1) {
                return false;
            }
            pathsToHereActiveIndices[pathPartIndexOfNode - 1]++;
            BiDirectionalBFS.PathTraceStep pathToHere = getActivePathToNode(pathPartIndexOfNode);
            updateInternalRelsToNode(pathToHere.relId, pathPartIndexOfNode);
            updateInternalNodes(pathToHere.prevNodeId, pathPartIndexOfNode - 1);
            return true;
        }

        private void activateFirstPathStepToNode(int pathPartIndexOfNode) {
            pathsToHereActiveIndices[pathPartIndexOfNode - 1] = 0;
            BiDirectionalBFS.PathTraceStep pathToHere = getActivePathToNode(pathPartIndexOfNode);
            updateInternalRelsToNode(pathToHere.relId, pathPartIndexOfNode);
            updateInternalNodes(pathToHere.prevNodeId, pathPartIndexOfNode - 1);
        }

        public void resetPathPartToIntersection() {
            resetPathPartToNodeAtIndex(pathPartLength);
        }

        public void resetPathPartToNodeAtIndex(int nodeIndex) {
            assert (nodeIndex <= pathPartLength);

            while (nodeIndex > 0) {
                activateFirstPathStepToNode(nodeIndex);
                nodeIndex--;
            }
        }

        public boolean viewNextPath() {
            if (pathPartLength == 0) {
                return false;
            }

            // We never iterate the start node as this is fixed (and it has no pathTraceSteps going into it).
            // That's why we begin by setting indexToIterate to 1 and not 0
            int indexToIterate = 1;

            while (!activateNextPathPartToNode(indexToIterate)) {
                indexToIterate++;
                if (indexToIterate > pathPartLength) {
                    return false;
                }
            }
            resetPathPartToNodeAtIndex(indexToIterate - 1);
            return true;
        }
    }
}
