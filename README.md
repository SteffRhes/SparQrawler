
# SparQrawler ( v0.1 Early prototype! )

SparQrawler is a rdf graph compression tool, which takes as input a sparql query and produces a more condensed output graph which is for now stored in a neo4j database for quick visualization means.

## Compression algorithm of RDF data

The compression algorithm works by grouping together nodes which have identical 'relational neighbourhoods' -  which is the collection of their outgoing and incoming relations in addition to the number of occurrences of each relation. So when two or more nodes have the same neighbourhood (e.g. two outgoing 'p' relations and one incoming 'q' relation) then they are grouped together and the individual relations of each node are transfered to their respective group. 

As an example assume an rdf graph of the following triples:
```
a --p-> x
b --p-> x
y --q-> a
z --q-> b
x --r-> x
```

This would result in three groups:
```
[a,b], [y,z], [x]
```

[a,b] because both a and b have each one outgoing 'p' and one incomig 'q'

[y,z] because both y and z have each one outgoing 'q' 

[x] because x has one outgoing r and two incoming 'p'

And these groups would have the following relations with each other:

```
[y,z]--q(2)->[a,b]--p(2)->[x]--r(1)->[x]
```

Note: SparQrawler only runs through rdf nodes which come from a result set of a give sparql-query. That means that the neighbourhood of a given rdf node can be smaller than in the triplestore because only relations coming from the sparql-result are analysed.

## How to run

### Dependencies

* Java 8 ( succesfully tested with 1.8.0_161 and 1.8.0_181 )
* neo4j ( succesfully tested with 3.4.5 on both Linux and Windows )
Download the community edition here: https://neo4j.com/download-center/#releases

### Running the pre-combiled executable

After having installed / downloaded these tools, go to the neo4j folder and therein to the bin folder. There start neo4j with

for Linux: 

```
./neo4j console
```

for Windows:

```
neo4j.bat console 
```

If it's your first time starting neo4j, you must change the default password. Do this via the web-UI on localhost:7474

Then in the target folder of SparQrawler you find an executable jar file, run this with:

```
java -jar SparQrawler-0.1-jar-with-dependencies.jar 
```

After launching you would type in the necesseray values (triplestore URL, neo4j user and password (assuming localhost!), after which the sparql query would be typed in.

You can use multiple lines by just inserting enters, once done you can execute the sparql query then by entering a new line where you would only type in a single semicolon ';'. 

To quit SparQrawler enter two semicolons ';;' in a single new line.


### Interpretation of result in neo4j and in terminal

Log into neo4j with your browser by going to localhost:7474

Return all data with
```
match (n) return n
```

In the neo4j web browser for reasons of compactness, the individual nodes are not saved. What you see in neo4j then is:

* Groups. Each node in neo4j represents a group of rdf nodes
* Number of nodes in groups. This is used as label to be displayed in neo4j
* Hovering with the mouse over nodes in neo4j: the defining neighbourhood of a group is displayed and if the group only contains one rdf node this single node is displayed.
* Hovering with the mouse over relations in neo4j: the count of this relation between the respective groups

In the console after the grouping and persisting data to neo4j is done, a summary of all groups is printed (and their contained nodes).

## How to compile

If you want to compile it yourself then use maven to do so, in the root directory of SparQrawler where there is the pom.xml file, run

```
mvn clean package assembly:single
```

After which in the target directory an executable jar file is created.

( Compiling only tested on Linux )
