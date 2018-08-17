import org.apache.jena.rdf.model.RDFNode;

public class GroupRelation {

    //    public Group groupSubject;
    public RDFNode relation;
    public Group groupObject;

    public GroupRelation(RDFNode relation, Group groupObject) {
//        this.groupSubject = groupSubject;
        this.relation = relation;
        this.groupObject = groupObject;
    }

    @Override
    public int hashCode() {
//        return groupSubject.hashCode() + relation.hashCode() * 3 + groupObject.hashCode() * 7;
        return relation.hashCode() * 3 + groupObject.hashCode() * 7;
    }

    @Override
    public boolean equals(Object obj) {

        GroupRelation otherGroupRelation = (GroupRelation) obj;
//        return this.groupSubject.equals(otherGroupRelation.groupSubject) &&
//                this.relation.equals(otherGroupRelation.relation) &&
//                this.groupObject.equals(otherGroupRelation.groupObject);
        return
                this.relation.equals(otherGroupRelation.relation) &&
                        this.groupObject.equals(otherGroupRelation.groupObject);
    }

    @Override
    public String toString() {
        return " - " + relation + " -> " + groupObject;
    }
}
