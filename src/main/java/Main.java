import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;

import java.util.*;


public class Main {


    /**
     * neo4jDriver : the driver for the neo4j database
     */
    public static Driver neo4jDriver;

    /**
     * virtuosoURL : URL of the endpoint. Only virtuoso tested so far, could work with any other in principle
     */
    public static String virtuosoURL;


    /**
     * main method
     *
     * responsible for reading in user input regarding the two external databases:
     * - rdf triplestore for the input data,
     * - neo4j database for the grouped output data
     *
     * @param args : sofar no command line arguments are handled
     */
    public static void main(String[] args){

        Scanner scanner = new Scanner(System.in);


        /**
         * Read in configuration data
         *
         * experimentable : For the ease of use, you could either hardcore these configurations directly here in the code
         * or insert a way to read in the configuration from a file.
         */

        System.out.print("URL of virtuoso instance: ");
        virtuosoURL = scanner.nextLine();

        System.out.print("User of local neo4 instance: ");
        String userNeo4j = scanner.nextLine();

        System.out.print("Password for local neo4 instance: ");
        String passwordNeo4j = scanner.nextLine();

        neo4jDriver = GraphDatabase.driver(
                "bolt://127.0.0.1:7687", AuthTokens.basic(userNeo4j, passwordNeo4j));



        // Go into loop for reading sparql query after sparql query

        System.out.println("\nType in sparql-query (end query with ';' in a single new line, ';;' to exit the program) :");
        String query = "";
        while (scanner.hasNext()) {

            String input = scanner.nextLine();

            if (input.equals(";")) {
                try {

                    // call of main execution method
                    execute(query);
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

    /**
     * the top level method which invokes several distinct phases of the whole graph compression logic and its output
     *
     * @param queryString : the sparql query as string to be executed
     * @throws Exception
     */
    private static void execute(String queryString) throws Exception {


        // phase 1, get all individual RDFNodes from original query
        System.out.println("##############################\nget all individual RDFNodes from original query.");
        long startTime = System.currentTimeMillis();
        HashSet<RDFNode> startNodes = getNodeHashSetFromQuery(queryString);
        System.out.println("DONE, number of nodes: " + startNodes.size() + ", time elapsed: " + (System.currentTimeMillis() - startTime) +"\n");


        // phase 2, calculate neighbourhoods of each otherNode
        System.out.println("##############################\ncalculate neighbourhood of each node, put the node into the neighbourhood-respective group, wire the groups");
        startTime = System.currentTimeMillis();
        HashSet<Group> groups = generateGroups(startNodes);
        System.out.println("DONE, number of groups: " + groups.size() + ", time elapsed: " + (System.currentTimeMillis() - startTime) +"\n");


        // phase 3, persist groups in neo4j
        //
        // experimentable: Since the main logic has been done by now, all the generated groups could be written into any
        // kind of storage with any kind of format (which would need to be done manually of course, as a general guideline
        // on how to iterate over the result groups, have a look at the 'toString' method of the Group Class, where all
        // the information encapsulated in a single groups is iterated over. This you then would need to do for each group
        System.out.println("##############################\nDelete data in neo4j, persist new groups in neo4j");
        startTime = System.currentTimeMillis();
        persistGroupsToNeo4j(groups);
        System.out.println("DONE, time elapsed: " + (System.currentTimeMillis() - startTime) +"\n");


        // phase 4, print all groups to user console
        System.out.println("##############################\nPrintout of all groups with all relations\n");
        for (Group g : groups) {
            if (g.hasRelations())
                System.out.println(g);
        }
        System.out.println("\n\n\n");
    }


    /**
     * This method executes the sparql query and extracts all the individual rdf nodes from the result set which it then
     * saves  into a hashset, that is needed later for the grouping algorithm.
     *
     * motivation:
     *
     * Extracting the nodes in such way is necessary since the grouping algorithm needs to query the relations of each
     * given node again since the result set of the user sparql query doesn't provide any information on which variable
     * is a node, which is a relation and the like.
     *
     * Also saving the nodes into a hashset eliminates redundancy on a few occasions where the grouping algorithm need
     * to check what nodes are relevant in the context of the user sparql qurey.
     *
     * @param queryString : the sparql query as string to be executed
     * @return HashSet<RDFNode> : the hashset of RDFNodes
     */
    private static HashSet<RDFNode> getNodeHashSetFromQuery(String queryString) {


        // apache jena query preparation and execution
        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.sparqlService(virtuosoURL, query);
        ResultSet rs = qexec.execSelect();


        // saving results into a hashset
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


    /**
     * the main grouping algorithm
     *
     * important algorithmic steps (which are explained in detail within the method):
     * 1.) queries the relations for an individual rdf node
     * 2.) calculates the neighbourhood of the individual rdf nodes (both subject and object)
     * 3.) saves the rdf nodes into a neighbourhood-specific group
     * 4.) copies the relations of the nodes to their respective groups
     *
     * @param nodes : a hashset consisting of rdf nodes whose relations are examined to categorize them into groups
     * @return HashSet<Group> : The resulting set of groups
     * @throws Exception
     */
    private static HashSet<Group> generateGroups(HashSet<RDFNode> nodes) throws Exception {


        /**
         * 1.) queries the relations for an individual rdf node
         *
         * Since the resulting variables of a sparql query do not intrinsically encode relations or nodes (since they
         * are just variables defined by the user-query), for reach individual rdf node from the result set the algorithms
         * needs to query again for its relations, so that the node's neighbourhood can be examined.
         *
         * Another possibility to avoid this second querying would be to force the user to only use variables in a
         * way which would tell the programm which of them are nodes and which are relations. But since this excludes many
         * custom sparql queries, I decided to make this algorithm result-set agnostic on the expense of a bit of performance
         * loss
         */

        /** preparations of sparql queries for subject and object nodes, the individual URIs are being injected later on
         *
         * experimentable :  I think by adapting these queries with prefixes and namespaces,
         * the incoming result set values can be shortened to more user-friendly abbrevations.
         */
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


        // nodeToNeighbourhood : A hashmap which takes a rdf node as key and a Neighbourhood object as value
        // This map is used for saving to each node its respective neighboourhood
        HashMap<RDFNode, Neighbourhood> nodeToNeighbourhood = new HashMap<RDFNode, Neighbourhood>();

        // neighbourToGroup : A hashmap which takes a Neighbourhood object as key and a Group object as value
        // This map is used for saving to each neighbourhood its respective group
        HashMap<Neighbourhood, Group> neighbourToGroup = new HashMap<Neighbourhood, Group>();

        // groupsSet : the main result set of groups which is returned from the method
        HashSet<Group> groupsSet = new HashSet<Group>();


        // some counting variables for displaying progress
        int i = 0;
        int j = 0;
        int totalSize = nodes.size();




        /**
         * start of main loop, where each individual rdf node from the input hashset is being queried and its
         * neighbourhood examined.
         *
         * Important note: For each node, only its relations as subject to its objects are being saved into groups which
         * are generated on the fly as well (if needed). By only encoding group-relations in the format of
         * subjectGroup -GroupRelations-> objectGroup
         * redundancy is avoided when later the node of such a generated objectGroup is being queried where it would
         * otherwise needed to be checked if it were already put into a group.
         *
         * From my understanding and testing, encoding the group relations onyl one-directional avoids a lot of double
         * checks while not losing any relation.
         */
        for (RDFNode n : nodes) {

            // counting stuff for displaying progress
            if (totalSize >= 20) {
                i++;
                if (i % (totalSize / 10) == 0) {
                    j += 10;
                    System.out.println(j + "%");
                }
            }

            // injection of rdf node into prepared sparql query from before, execution of query
            pssSubject.setParam("?n", n);
            Query querySubject = QueryFactory.create(pssSubject.asQuery());
            QueryExecution qexecSubject = QueryExecutionFactory.sparqlService(virtuosoURL, querySubject);
            ResultSet rsSubject = qexecSubject.execSelect();

            // preparing list of all the relations the group of the subject node will encounter, reuse this list later
            // and save all of it into the group of the subject node
            ArrayList<GroupRelation> currentGroupRelations = new ArrayList<GroupRelation>();

            // check if for given node there is already a neighbourhood generated, if not create a new one.
            Neighbourhood neighbourhoodSubject = nodeToNeighbourhood.get(n);
            if (neighbourhoodSubject == null)
                neighbourhoodSubject = new Neighbourhood();

            // variables for the individual rdf subjects, objects, their relations
            RDFNode nRelObject = null;
            RDFNode nRelSubject = null;
            RDFNode nObject = null;
            RDFNode nSubject = null;



            /**
             * This loop now goes throught each line of the sparql result set, which means it iterates over
             * each individual relation the node n has.
             */
            while (rsSubject.hasNext()) {

                QuerySolution qsSubject = rsSubject.next();
                nRelObject = qsSubject.get("?nRelObject");
                nRelSubject = qsSubject.get("?nRelSubject");

                /**
                 *2.) calculates the neighbourhood of the individual rdf nodes (both subject and object)
                 * if subject n has no neighbourhood yet, create it
                 */
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

                    Group groupObject = null;


                    /**
                     * 2.) calculates the neighbourhood of the individual rdf nodes (both subject and object)
                     *
                     * Within the following if branch the neighbourhood of the object node is created (since it
                     * does not exist yet). This duplicates a lot of logic from the neighbourhood creation of the
                     * subject node, but since there are a few dependencies between object and subject and other variables,
                     * I didn't see a quick way of outsourcing this logic into a smaller dedicated method
                     */
                    Neighbourhood neighbourhoodObject = nodeToNeighbourhood.get(nObject);
                    if (neighbourhoodObject == null) {

                        neighbourhoodObject = new Neighbourhood();

                        // query preparation, injecting the object node into it, execute query
                        pssObject.setParam("?nObject", nObject);
                        Query queryObject = QueryFactory.create(pssObject.asQuery());
                        QueryExecution qexecObject = QueryExecutionFactory.sparqlService(virtuosoURL, queryObject);
                        ResultSet rsObject = qexecObject.execSelect();

                        /**
                         * This loop now goes throught each line of the sparql result set, which means it iterates over
                         * each individual relation the node nObject has.
                         */
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




                        /**
                         * 3.) saves the rdf nodes into a neighbourhood-specific group
                         *
                         * The following block assigns to the object node nObject its respective neighbourhodd,
                         * and also to the neighbourhood its respective group
                         */
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

                        // test for a case which should never happen
                        if ( groupObject == null )
                            throw new Exception("This should never happen!");
                    }

                    // test for a case which should never happen
                    if ( ( nRelObject == null && groupObject != null) || ( nRelObject != null && groupObject == null)  ) {
                        throw new Exception("This should never happen!"); //
                    }


                    // Saves the current relation from the subject n to the group of the object with a given relation
                    // into a list which is used later to be inserted into the subjectGroup
                    currentGroupRelations.add(new GroupRelation(nRelObject, groupObject));
                }
            }


            /**
             * 3.) saves the rdf nodes into a neighbourhood-specific group
             *
             * The following block assigns to the subject node n its respective neighbourhodd,
             * and also to the neighbourhood its respective group
             */
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

            /**
             * 4.) copies the relations of the nodes to their respective groups
             */
            groupSubject.addRelation(currentGroupRelations);
            groupsSet.add(groupSubject);
        }

        return groupsSet;
    }


    /**
     * This method persists all the groups into neo4j database
     *
     * experimentable : A lot in this method can be easily tweaked since it would just fetch group relevant data from the groups
     * and uses their values as string for neo4j. Thus the desired result neo4j graph can be very easily tweaked here.
     *
     * @param groups
     */
    static void persistGroupsToNeo4j(HashSet<Group> groups) {

        Session session = null;
        try {
            session = neo4jDriver.session();


            /**
             * experimentable : you could try commenting the following line and see how neo4j could handle inserting data after data
             * Though I think there would appear inconsistencies since different queries and their different result sets
             * would cause different neighbourhoods for some nodes and thus then different groups. In such a case, a single
             * node could be inserted into different groups which goes against the basic idea of the graph compression.
             */
            session.run("MATCH (n) DETACH DELETE (n)");


            /**
             * Iterate over each group, get their values and fiels, turn them into strings so that they can be saved
             * in neo4j
             */
            for (Group g : groups) {


                /**
                 * experimentable : All of the following String variables are used for the neo4j insert command.
                 * If the graph structure in neo4j should be changed you can do this easily here, by selecting the
                 * wished-for values from the groups and use them as strings to be inserted as values again into neo4j
                 *
                 * E.g. The neighbourhood of a group could be inserted as some neo4j arrays instead of this simple string
                 * as it is now (so that in neo4j you could search for certain neighbourhoods).
                 */

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


                    /**
                     * n1 and n2 are string variables for representing groups.
                     */
                    String n1 = null;
                    String n2 = null;

                    /**
                     * Checking the size of the group, if it only contains one node, then include its URI into neo4j properties
                     */
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

                    /**
                     * main neo4j 'insert' command, which consists of three merge commands which all mean that they
                     * check if this variable already exists in neo4j. If it does exists then it reuses it, if not, it creates it
                     *
                     * experimentable : you can play around with this neo4j command
                     */
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
