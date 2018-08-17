import org.apache.jena.rdf.model.RDFNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Group {

    public HashSet<RDFNode> nodes;
    public Neighbourhood neighbourhood;
    private HashMap<GroupRelation, Integer> relationsAndCounts;


    public Group() {
        this.nodes = new HashSet<RDFNode>();
        this.relationsAndCounts = new HashMap<GroupRelation, Integer>();
        this.neighbourhood = new Neighbourhood();
    }


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



//    @Override
//    public int hashCode() {
//        return nodes.hashCode();
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        Group otherGroup = (Group) obj;
//        return nodes.equals(otherGroup.nodes);
//    }
}
