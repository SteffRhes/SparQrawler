import org.apache.jena.rdf.model.RDFNode;


/**
 * A data object which is inserted into a subject group. Such a GroupRelation object only encodes an outgoing relation and
 * the objectGroup this relations goes to.
 */
public class GroupRelation {

    public RDFNode relation;
    public Group groupObject;

    public GroupRelation(RDFNode relation, Group groupObject) {
        this.relation = relation;
        this.groupObject = groupObject;
    }

    /**
     * custom hashCode method becuase in a Group object, the GroupRelation objects are used as keys for a hashmap
     *
     * @return
     */
    @Override
    public int hashCode() {
        return relation.hashCode() * 3 + groupObject.hashCode() * 7;
    }

    /**
     * custom equals method becuase in a Group object, the GroupRelation objects are used as keys for a hashmap
     *
     * @return
     */
    @Override
    public boolean equals(Object obj) {

        GroupRelation otherGroupRelation = (GroupRelation) obj;
        return
                this.relation.equals(otherGroupRelation.relation) &&
                        this.groupObject.equals(otherGroupRelation.groupObject);
    }

    @Override
    public String toString() {
        return " - " + relation + " -> " + groupObject;
    }
}
