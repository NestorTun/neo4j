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
package upgrade;

import java.io.File;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.impl.AbstractNeo4jTestCase;
import org.neo4j.kernel.impl.nioneo.store.NeoStore;
import org.neo4j.kernel.impl.nioneo.store.NodeRecord;
import org.neo4j.kernel.impl.nioneo.xa.NeoStoreProvider;
import org.neo4j.test.TargetDirectory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.neo4j.consistency.store.StoreAssertions.assertConsistentStore;
import static org.neo4j.graphdb.Direction.OUTGOING;
import static org.neo4j.graphdb.DynamicLabel.label;
import static org.neo4j.graphdb.factory.GraphDatabaseSettings.allow_store_upgrade;
import static org.neo4j.helpers.collection.IteratorUtil.asSet;
import static org.neo4j.helpers.collection.IteratorUtil.count;
import static org.neo4j.helpers.collection.IteratorUtil.single;
import static org.neo4j.tooling.GlobalGraphOperations.at;

public class TestMigrateToDenseNodeSupport
{
    private static final Label referenceNode = label( "ReferenceNode" );

    private static enum Types implements RelationshipType
    {
        DENSE,
        SPARSE,
        OTHER,
        FOURTH
    }

    private static enum Properties
    {
        BYTE( (byte)10 ),
        SHORT( (short)345 ),
        INT( 123456 ),
        LONG( 12345678901L ),
        CHAR( 'N' ),
        FLOAT( 123.456F ),
        DOUBLE( 123.456789D ),
        SHORT_STRING( "short" ),
        LONG_STRING( "super-duper long string that will have to spill over into a dynamic record !#¤%&/()=," ),
        INT_ARRAY( new int[] {12345, 67890, 123456789, 1, 2, 3, 4, 5} )
        {
            @Override
            public void assertValueEquals( Object otherValue )
            {
                assertArrayEquals( (int[]) value, (int[]) otherValue );
            }
        },
        STRING_ARRAY( new String[] { "First", "Second", "Third", "Fourth" } )
        {
            @Override
            public void assertValueEquals( Object otherValue )
            {
                assertArrayEquals( (Object[]) value, (Object[]) otherValue );
            }
        };

        protected final Object value;

        private Properties( Object value )
        {
            this.value = value;
        }

        public Object getValue()
        {
            return value;
        }

        public void assertValueEquals( Object otherValue )
        {
            assertEquals( value, otherValue );
        }
    }

    private File dir;

    @Before
    public void before() throws Exception
    {
        dir = AbstractNeo4jTestCase.unzip( getClass(), "0.A.1-db.zip" );
    }

    @Test
    @Ignore( "Used for creating the dataset, using the previous store version" )
    public void createDb()
    {
        GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase(
                TargetDirectory.forTest( getClass() ).makeGraphDbDir().getPath() );
        try
        {
            try ( Transaction tx = db.beginTx() )
            {
                Node refNode = db.createNode( referenceNode );
                // Create 10 dense nodes
                for ( int i = 0; i < 10; i++ )
                {
                    createDenseNode( db, refNode );
                }
                // And 10 sparse nodes
                for ( int i = 0; i < 10; i++ )
                {
                    createSparseNode( db, refNode );
                }
                tx.success();
            }
        }
        finally
        {
            db.shutdown();
        }
    }

    @Test
    public void migrateDbWithDenseNodes() throws Exception
    {
        GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( dir.getAbsolutePath() )
                .setConfig( allow_store_upgrade, "true" ).newGraphDatabase();

        try ( Transaction tx = db.beginTx() )
        {
            ResourceIterable<Node> allNodesWithLabel = at( db ).getAllNodesWithLabel( referenceNode );
            Node refNode = single( allNodesWithLabel );
            int sparseCount = 0;
            for ( Relationship relationship : refNode.getRelationships( Types.SPARSE, OUTGOING ) )
            {
                verifySparseNode( db, relationship.getEndNode() );
                sparseCount++;
            }

            int denseCount = 0;
            for ( Relationship relationship : refNode.getRelationships( Types.DENSE, OUTGOING ) )
            {
                verifyDenseNode( db, relationship.getEndNode() );
                denseCount++;
            }

            assertEquals( 10, sparseCount );
            assertEquals( 10, denseCount );

            tx.success();
        }
        db.shutdown();

        assertConsistentStore( dir );
    }

    private void verifyDenseNode( GraphDatabaseService db, Node node )
    {
        assertEquals( 102, count( node.getRelationships( OUTGOING ) ) );
        assertEquals( 100, count( node.getRelationships( Types.OTHER, OUTGOING ) ) );
        assertEquals( 2, count( node.getRelationships( Types.FOURTH, OUTGOING ) ) );
        verifyProperties( node );
        verifyDenseRepresentation( db, node, true );
    }

    private void verifySparseNode( GraphDatabaseService db, Node node )
    {
        assertEquals( 3, count( node.getRelationships( OUTGOING ) ) );
        verifyProperties( node );
        verifyDenseRepresentation( db, node, false );
    }

    private void verifyProperties( Node node )
    {
        Set<String> keys = asSet( node.getPropertyKeys() );
        for ( Properties property : Properties.values() )
        {
            assertTrue( keys.remove( property.name() ) );
            property.assertValueEquals( node.getProperty( property.name() ) );
        }
        assertTrue( keys.isEmpty() );
    }

    private void verifyDenseRepresentation( GraphDatabaseService db, Node node, boolean dense )
    {
        NeoStore neoStore = ((GraphDatabaseAPI) db).getDependencyResolver().resolveDependency(
                NeoStoreProvider.class ).evaluate();
        NodeRecord record = neoStore.getNodeStore().getRecord( node.getId() );
        assertEquals( dense, record.isDense() );
    }

    private void createSparseNode( GraphDatabaseService db, Node refNode )
    {
        Node node = db.createNode();
        refNode.createRelationshipTo( node, Types.SPARSE );
        createRelationships( db, node, 3, Types.OTHER );
        setProperties( node );
    }

    private void createDenseNode( GraphDatabaseService db, Node refNode )
    {
        Node node = db.createNode();
        refNode.createRelationshipTo( node, Types.DENSE );
        createRelationships( db, node, 100, Types.OTHER );
        createRelationships( db, node, 2, Types.FOURTH );
        setProperties( node );
    }

    private void createRelationships( GraphDatabaseService db, Node node, int count, RelationshipType type )
    {
        for ( int i = 0; i < count; i++ )
        {
            node.createRelationshipTo( db.createNode(), type );
        }
    }

    private void setProperties( Node node )
    {
        for ( Properties properties : Properties.values() )
        {
            node.setProperty( properties.name(), properties.getValue() );
        }
    }
}
