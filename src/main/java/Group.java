import org.apache.jena.rdf.model.RDFNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;


/**
 * The data object class which represents a group of rdf nodes and which saves a sume of all the outgoing relations of
 * its containing rdf nodes.
 */
public class Group {

    // The containing rdf nodes
    public HashSet<RDFNode> nodes;

    // The neighbourhood this group represents
    public Neighbourhood neighbourhood;

    // A hashmap which has as key a GroupRelation and as value the count of how often this relation points to the respective group
    private HashMap<GroupRelation, Integer> relationsAndCounts;


    public Group() {
        this.nodes = new HashSet<RDFNode>();
        this.relationsAndCounts = new HashMap<GroupRelation, Integer>();
        this.neighbourhood = new Neighbourhood();
    }


    /**
     * Takes as input a list of relations, checks all of its items if this groups already contains the same relation
     * to the same objectGroup, and if it does, increases the counter, if not instantiates it to 1.
     *
     *
     * @param newGroupRelations : A list of GroupRelation objects, see that class for more information on it
     * @throws Exception
     */
    public void addRelation( ArrayList<GroupRelation> newGroupRelations) throws Exception {

        for (GroupRelation newGroupRelation : newGroupRelations) {

            Integer existingRelationCount = relationsAndCounts.get(newGroupRelation);
            if (existingRelationCount == null) {
                relationsAndCounts.put(newGroupRelation, 1);
            } else {
                relationsAndCounts.put(newGroupRelation, ++existingRelationCount);
            }
        }
    }

    public boolean hasRelations() {
        return relationsAndCounts.size() > 0;
    }

    public HashMap<GroupRelation, Integer> getRelations() {
        return relationsAndCounts;
    }


    @Override
    public String toString() {

        String result = "Group with " + Integer.toString(nodes.size()) + " nodes: " + nodes.toString();

        for ( GroupRelation groupRelation : relationsAndCounts.keySet()) {
            int count = relationsAndCounts.get(groupRelation);

            result += "\n- " + groupRelation.relation + " (" + count + ") -> " + "Group with " + Integer.toString(groupRelation.groupObject.nodes.size()) + " nodes: " + groupRelation.groupObject.nodes;

        }
        result += "\n";

        return result;
    }
}
