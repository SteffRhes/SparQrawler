import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;

import java.util.*;


public class Main {

    public static Driver neo4jDriver;
    public static String virtuosoURL;

    public static void main(String[] args){

        Scanner scanner = new Scanner(System.in);

//        System.out.println("Options: " +
//                "\n run [sparql query] (runs the typed in sparql query or in the case of no argument being passed, run the pre-defined query)" +
//                "\n list [group identifier] (lists all the nodes contained in group with given identifier" +
//                "\n show [group identifier] (shows all relations from group with given identifier)" +
//                "\n expand [group identifier] (expands all relations and the groups they are relating to from the group with given identifier)");



        System.out.print("URL of virtuoso instance: ");
        virtuosoURL = scanner.nextLine();

        System.out.print("User of local neo4 instance: ");
        String userNeo4j = scanner.nextLine();

        System.out.print("Password for local neo4 instance: ");
        String passwordNeo4j = scanner.nextLine();

        // testing values
//        virtuosoURL = "http://localhost:8890/sparql";
//        String userNeo4j = "neo4j"; //ToDo
//        String passwordNeo4j = "password"; //ToDo

        neo4jDriver = GraphDatabase.driver(
                "bolt://127.0.0.1:7687", AuthTokens.basic(userNeo4j, passwordNeo4j));


        System.out.println("\nType in sparql-query (end query with ';' in a single new line, ';;' to exit the program) :");
        String query = "";
        while (scanner.hasNext()) {

            String input = scanner.nextLine();

            if (input.equals(";")) {
                try {
                    execute(query);

                    // testing value
//                    execute("select * where { ?s ?p ?o } limit 10");

                    System.out.println("\nType in sparql-query (end query with ';' in a single new line, ';;' to exit the program) :");

                } catch (Exception e) {
                    neo4jDriver.close();
                    System.out.println(e);
                    System.exit(1);
                }
                query = "";
            } else if (input.equals(";;")) {
                neo4jDriver.close();
                System.exit(0);
            }
            else {
                query += input + "\n";
            }
        }
    }

    private static void execute(String queryString) throws Exception {

        // phase 1, get all individual RDFNodes from original query
        System.out.println("##############################\nget all individual RDFNodes from original query.");
        long startTime = System.currentTimeMillis();
        HashSet<RDFNode> startNodes = getNodeHashSetFromQuery(queryString);
        System.out.println("DONE, number of nodes: " + startNodes.size() + ", time elapsed: " + (System.currentTimeMillis() - startTime) +"\n");

        // phase 2, calculate neighbourhoods of each otherNode
        System.out.println("##############################\ncalculate neighbourhood of each node, put the node into the neighbourhood-respective group, wire the groups");
        startTime = System.currentTimeMillis();
        HashSet<Group> groups = getNeighbourhoods(startNodes);
        System.out.println("DONE, number of groups: " + groups.size() + ", time elapsed: " + (System.currentTimeMillis() - startTime) +"\n");


        // phase 3, persist groups in neo4j
        System.out.println("##############################\nDelete data in neo4j, persist new groups in neo4j");
        startTime = System.currentTimeMillis();
        persistGroupsToNeo4j(groups);
        System.out.println("DONE, time elapsed: " + (System.currentTimeMillis() - startTime) +"\n");


        System.out.println("##############################\nPrintout of all groups with all relations\n");
        // print result to console
        for (Group g : groups) {
            if (g.hasRelations())
                System.out.println(g);
        }
        System.out.println("\n\n\n");
    }


    private static HashSet<RDFNode> getNodeHashSetFromQuery(String queryString) {

        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.sparqlService(virtuosoURL, query);
        ResultSet rs = qexec.execSelect();

        HashSet<RDFNode> results = new HashSet<RDFNode>();
        while(rs.hasNext()) {
            QuerySolution qs = rs.next();
            Iterator<String> iter = qs.varNames();

            while (iter.hasNext()) {
                String varName = iter.next();
                RDFNode n = qs.get(varName);
                results.add(n);
            }
        }

        return results;
    }


    private static HashSet<Group> getNeighbourhoods(HashSet<RDFNode> nodes) throws Exception {

        ParameterizedSparqlString pssSubject = new ParameterizedSparqlString();
        pssSubject.setCommandText("SELECT * WHERE { " +
                "{ ?n ?nRelObject ?nObject } UNION" +
                "{ ?nSubject ?nRelSubject ?n }" +
                " }");

        ParameterizedSparqlString pssObject = new ParameterizedSparqlString();
        pssObject.setCommandText("SELECT * WHERE { " +
                "{ ?nObject ?nObjectRelObject ?nObjectObject } UNION" +
                "{ ?nObjectSubject ?nObjectRelSubject ?nObject }" +
                " }");

        HashMap<RDFNode, Neighbourhood> nodeToNeighbourhood = new HashMap<RDFNode, Neighbourhood>();
        HashMap<Neighbourhood, Group> neighbourToGroup = new HashMap<Neighbourhood, Group>();
        HashSet<Group> groupsSet = new HashSet<Group>();
        ArrayList<Group> groupsList = new ArrayList<Group>();

        int i = 0;
        int j = 0;
        int totalSize = nodes.size();
        for (RDFNode n : nodes) {

            if (totalSize >= 20) {
                i++;
                if (i % (totalSize / 10) == 0) {
                    j += 10;
                    System.out.println(j + "%");
                }
            }

            pssSubject.setParam("?n", n);
            Query querySubject = QueryFactory.create(pssSubject.asQuery());
            QueryExecution qexecSubject = QueryExecutionFactory.sparqlService(virtuosoURL, querySubject);
            ResultSet rsSubject = qexecSubject.execSelect();

            ArrayList<GroupRelation> currentGroupRelations = new ArrayList<GroupRelation>();

            // go through all connections of subject n
            Neighbourhood neighbourhoodSubject = nodeToNeighbourhood.get(n);
            if (neighbourhoodSubject == null)
                neighbourhoodSubject = new Neighbourhood();

            RDFNode nRelObject = null;
            RDFNode nRelSubject = null;
            RDFNode nObject = null;
            RDFNode nSubject = null;

            while (rsSubject.hasNext()) {

                QuerySolution qsSubject = rsSubject.next();


                // subject neighbourhood

                nRelObject = qsSubject.get("?nRelObject");
                nRelSubject = qsSubject.get("?nRelSubject");
                // if subject has no neighbourhood, create it
                if (!nodeToNeighbourhood.containsKey(n)) {

                    nObject = qsSubject.get("?nObject");
                    nSubject = qsSubject.get("?nSubject");


                    if (nRelObject != null && nodes.contains(nObject)) {

                        Integer count = neighbourhoodSubject.countRelationsOutgoing.get(nRelObject);
                        if (count != null) {
                            neighbourhoodSubject.countRelationsOutgoing.put(nRelObject, ++count);
                        } else {
                            neighbourhoodSubject.countRelationsOutgoing.put(nRelObject, 1);
                        }

                    } else if (nRelSubject != null && nodes.contains(nSubject)) {

                        Integer count = neighbourhoodSubject.countRelationsIncoming.get(nRelSubject);
                        if (count != null) {
                            neighbourhoodSubject.countRelationsIncoming.put(nRelSubject, ++count);
                        } else {
                            neighbourhoodSubject.countRelationsIncoming.put(nRelSubject, 1);
                        }

                    }
                } else if (n.equals(nObject)) {
                    neighbourhoodSubject = nodeToNeighbourhood.get(nObject);
                }



                // object group assignment
                nObject = qsSubject.get("?nObject");
                if (nObject != null && nodes.contains(nObject)) {

                    // if object has no neighbourhood, create it
                    Group groupObject = null;
                    Neighbourhood neighbourhoodObject = nodeToNeighbourhood.get(nObject);
                    if (neighbourhoodObject == null) {

                        neighbourhoodObject = new Neighbourhood();

                        pssObject.setParam("?nObject", nObject);
                        Query queryObject = QueryFactory.create(pssObject.asQuery());
                        QueryExecution qexecObject = QueryExecutionFactory.sparqlService(virtuosoURL, queryObject);
                        ResultSet rsObject = qexecObject.execSelect();

                        // calculate neighbourhood
                        while (rsObject.hasNext()) {

                            QuerySolution qsObject = rsObject.next();

                            RDFNode nObjectRelObject = qsObject.get("?nObjectRelObject");
                            RDFNode nObjectRelSubject = qsObject.get("?nObjectRelSubject");
                            RDFNode nObjectObject = qsObject.get("?nObjectObject");
                            RDFNode nObjectSubject = qsObject.get("?nObjectSubject");

                            if (nObjectRelObject != null && nodes.contains(nObjectObject)) {

                                Integer count = neighbourhoodObject.countRelationsOutgoing.get(nObjectRelObject);
                                if (count != null) {
                                    neighbourhoodObject.countRelationsOutgoing.put(nObjectRelObject, ++count);
                                } else {
                                    neighbourhoodObject.countRelationsOutgoing.put(nObjectRelObject, 1);
                                }

                            } else if (nObjectRelSubject != null && nodes.contains(nObjectSubject) ) {

                                Integer count = neighbourhoodObject.countRelationsIncoming.get(nObjectRelSubject);
                                if (count != null) {
                                    neighbourhoodObject.countRelationsIncoming.put(nObjectRelSubject, ++count);
                                } else {
                                    neighbourhoodObject.countRelationsIncoming.put(nObjectRelSubject, 1);
                                }

                            }
                        }

                        if (!nodeToNeighbourhood.containsKey(nObject))
                            nodeToNeighbourhood.put(nObject, neighbourhoodObject);
                        groupObject = neighbourToGroup.get(neighbourhoodObject);
                        if (groupObject == null) {
                            groupObject = new Group();
                            groupObject.nodes.add(nObject);
                            groupObject.neighbourhood = neighbourhoodObject;
                            neighbourToGroup.put(neighbourhoodObject, groupObject);
                        } else {
                            groupObject.nodes.add(nObject);
                            groupObject.neighbourhood = neighbourhoodObject;
                        }

                    } else {
                        groupObject = neighbourToGroup.get(neighbourhoodObject);
                        if ( groupObject == null )
                            throw new Exception("This should never happen!");
                    }

                    // subject - object relation
                    if ( ( nRelObject == null && groupObject != null) || ( nRelObject != null && groupObject == null)  ) {
                        throw new Exception("This should never happen!"); //

                    }


                    currentGroupRelations.add(new GroupRelation(nRelObject, groupObject));
                }
            }


            // get group of subject, if doesn't exist yet, create and add subject to it
            if (!nodeToNeighbourhood.containsKey(n))
                nodeToNeighbourhood.put(n, neighbourhoodSubject);
            Group groupSubject = neighbourToGroup.get(neighbourhoodSubject);
            if (groupSubject == null) {
                groupSubject = new Group();
                groupSubject.nodes.add(n);
                groupSubject.neighbourhood = neighbourhoodSubject;
                neighbourToGroup.put(neighbourhoodSubject, groupSubject);
            } else {
                groupSubject.nodes.add(n);
                groupSubject.neighbourhood = neighbourhoodSubject;
            }

            // save relations into group of subject
            groupSubject.addRelation(currentGroupRelations);
            groupsSet.add(groupSubject);
        }

        return groupsSet;
    }

    static void persistGroupsToNeo4j(HashSet<Group> groups) {

        Session session = null;
        try {
            session = neo4jDriver.session();

            session.run("MATCH (n) DETACH DELETE (n)");

            for (Group g : groups) {

                String subjectGroupSizeString = Integer.toString(g.nodes.size());
                String subjectGroupNodesString = g.nodes.toString();
                String subjectGroupNeighbourhood = g.neighbourhood.toString();

                HashMap<GroupRelation, Integer> groupRelations = g.getRelations();
                for (GroupRelation groupRelation : groupRelations.keySet()) {
                    String relationCountString = Integer.toString(groupRelations.get(groupRelation));
                    String relationString = groupRelation.relation.toString();
                    String objectGroupSizeString = Integer.toString(groupRelation.groupObject.nodes.size());
                    String objectGroupNodesString = groupRelation.groupObject.nodes.toString();
                    String objectGroupNeighbourhood = groupRelation.groupObject.neighbourhood.toString();

                    String n1 = null;
                    String n2 = null;

                    if (g.nodes.size() > 1) {
                        n1 = "(n1:Group { label: '" + subjectGroupSizeString + "', neighbourhood: '" + subjectGroupNeighbourhood + "'})";
                    } else {
                        for (RDFNode individualNode : g.nodes)
                            n1 = "(n1:Group { label: '" + subjectGroupSizeString + "', node: '" + individualNode.toString() + "', neighbourhood: '" + subjectGroupNeighbourhood + "'})";
                    }

                    if (groupRelation.groupObject.nodes.size() > 1 ) {
                        n2 = "(n2:Group { label: '" + objectGroupSizeString + "', neighbourhood: '" + objectGroupNeighbourhood + "'})";
                    } else {
                        for (RDFNode individualNode : groupRelation.groupObject.nodes )
                            n2 = "(n2:Group { label: '" + objectGroupSizeString + "', node: '" + individualNode.toString() + "',  neighbourhood: '" + objectGroupNeighbourhood + "'})";
                    }

                    String neo4jCommand =
                            "MERGE " + n1 + "\n" + "MERGE " + n2 + "\n" +
                                    "MERGE (n1)-[:`"+relationString+"` { count: '" + relationCountString + "'}]-> (n2) \n";

                    session.run(neo4jCommand);
                }
            }
        } finally {
            session.close();
        }
    }
}
