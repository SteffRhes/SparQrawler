import org.apache.jena.rdf.model.RDFNode;

import java.util.HashMap;


public class Neighbourhood {

    public HashMap<RDFNode, Integer> countRelationsIncoming;
    public HashMap<RDFNode, Integer> countRelationsOutgoing;

    public Neighbourhood(HashMap<RDFNode, Integer> countRelationsIncoming, HashMap<RDFNode, Integer> countRelationsOutgoing) {
        this.countRelationsIncoming = countRelationsIncoming;
        this.countRelationsOutgoing = countRelationsOutgoing;
    }

    public Neighbourhood() {
        this.countRelationsIncoming = new HashMap<RDFNode, Integer>();
        this.countRelationsOutgoing = new HashMap<RDFNode, Integer>();
    }

    @Override
    public int hashCode() {
        return countRelationsIncoming.hashCode() + countRelationsOutgoing.hashCode() * 7;
    }

    @Override
    public boolean equals(Object obj) {
        return countRelationsIncoming.equals(((Neighbourhood) obj).countRelationsIncoming) &&
                countRelationsOutgoing.equals(((Neighbourhood) obj).countRelationsOutgoing);
    }

    @Override
    public String toString() {

        String result = "Relations incoming: [ ";
        for ( RDFNode relation : countRelationsIncoming.keySet() ) {
            int count = countRelationsIncoming.get(relation);
            result += relation + " (" + count + "), ";
        }

        result += "], Relations outgoing: [";

        for ( RDFNode relation : countRelationsOutgoing.keySet()) {
            int count = countRelationsOutgoing.get(relation);
            result += relation + " (" + count + "), ";
        }

        result += "]";
        return  result;
    }
}
