package uk.ac.ucl.iede;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.*;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;

public class BASActuator extends RBCController {


    // RECEIVE FROM CONTROLLER: DICTIONARY WITH LIST OF ZONES PER FUNCTION TYPES WITH SETPOINT AND SLOT VALUES
    /*
    Map<Object, Object> offModeZones = new HashMap<>();         // With "Zones", "Function" and "Slot time" keys 
    Map<Object, Object> tempSetMaxZones = new HashMap<>();      // With "Zones", "Function", "Setpoint value" and "Slot time" keys 
    Map<Object, Object> tempSetMinZones = new HashMap<>();      // With "Zones", "Function", "Setpoint value" and "Slot time" keys 
    Map<Object, Object> tempSetNormalZones = new HashMap<>();   // With "Zones", "Function", "Setpoint value" and "Slot time" keys 
     */
	
	public static void main(String[] args) throws IOException, ParseException {		
		
		Model DRgraph = RDFDataMgr.loadModel("D:/datos-flaand/DRgraph.ttl");
		BASActuator basActuator = new BASActuator(); 
		basActuator.searchZones(DRgraph);
		basActuator.matchRequiredAndAvailableFunctions();

	}
	   
	   Map<String, String> onOffDatapointsPerZone = new HashMap<>();    // Available onOff function datapoints per zone
	   Map<String, String> levelDatapointsPerZone = new HashMap<>();    // Available level function datapoints per zone

	// CONFIGURE KNOWLEDGE GRAPH INITIALISATION
	    /*
	    private static final Logger log = LoggerFactory.getLogger(OMActorKGE.class);
	    private String graphUrl;

	    public OMActorKGE(Entity entity) {
	        super(entity);
	        log.info("Knowledge Graph, init=" + getSelf().path().toString());
	    }

	    @Override
	    public void onReceive(Object message) throws Throwable { 

	         // ACCESS & LOAD MODEL 
	        DatasetAccessor accessor = DatasetAccessorFactory.createHTTP(graphUrl);
	        Model model = accessor.getModel();
	*/
		private void searchZones(Model DRgraph) throws IOException {

	        // CREATE GRAPH QUERY TO GET A LIST OF ZONES PER BUILDING
	        Query query = QueryFactory.create(
	        		"PREFIX schema: <http://schema.org#> " +
	        		"PREFIX  owl:   <http://www.w3.org/2002/07/owl#> " +
	        		"PREFIX  rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
	        		"PREFIX  xsd:   <http://www.w3.org/2001/XMLSchema#> " +
	        		"PREFIX  rdfs:  <http://www.w3.org/2000/01/rdf-schema#> " +
	        		"PREFIX  brick: <https://brickschema.org/schema/Brick#> " +
	        		"PREFIX  om:    <http://openmetrics.eu/openmetrics#> " +
	        		"PREFIX  saref: <https://saref.etsi.org/core/>  " +
	        		"PREFIX  props: <https://w3id.org/props#> " + 
	        		"SELECT DISTINCT ?zone ?actuatingFunctions ?datapoint " + 
	        		"WHERE { " +        		
					"?controller a brick:Equipment ; " +
              		"brick:hasLocation ?space; " +
        			"saref:hasFunction ?actuatingFunctions. " +
           
        			"?actuatingFunctions a saref:ActuatingFunction ; " +
        			"saref:hasCommand ?command . " +

        			"?zone a brick:Zone ;" +
        			"brick:hasPart  ?space ." +
  
        			"?command brick:timeseries ?datapoint ." +  		
	        		" } "); 

	        // EXECUTE QUERY TO GET A LIST OF ZONES AND THEIR OCCUPANCY SENSOR ID
	        QueryExecution qe = QueryExecutionFactory.create(query, DRgraph);
	        ResultSet results = qe.execSelect();
	        //ResultSetFormatter.out(System.out, results, query);         // Print query results (table with building, zone, datapoint)

	        // CREATE A DICTIONARY WITH ZONE URI AND OCC ID & AND ARRAYLIST WITH ZONE URIs
	        while (results.hasNext()) {
	            QuerySolution qSolution = results.nextSolution();
	            String zoneURI = qSolution.get("zone").toString();
	            String availableFunction = qSolution.get("actuatingFunctions").toString();
	            String datapointFunction = qSolution.get("datapoint").toString();
	            if (availableFunction.contains("OnOffFunction")) {
	                onOffDatapointsPerZone.put(zoneURI, datapointFunction);
	                continue;
	            }
	            else if (availableFunction.contains("LevelControlFunction")) {
	                levelDatapointsPerZone.put(zoneURI, datapointFunction);
	                break;
	            }
	        }
	        qe.close();
            System.out.println(onOffDatapointsPerZone);
            System.out.println(levelDatapointsPerZone);
	    }
		

        // CHECK IF THE INPUT LIST OF ZONES WITH REQUIRED FUNCTIONS MATCH THE QUERIED FUNCTIONS AVAILABLE PER ZONE, IF SO ADD THE DATAPOINTS TO THE FUNCTION DICTIONARIES
    public void matchRequiredAndAvailableFunctions() {

    	//test
        // FOR offMode FUNCTION
        for (String currentZoneOffMode : (ArrayList<String>) offModeZones.get("Zones")) {
            if (onOffDatapointsPerZone.containsKey(currentZoneOffMode)) {
                offModeZones.put("Datapoints", Arrays.asList(onOffDatapointsPerZone.get(currentZoneOffMode)));
	            System.out.println("offModeZonesMatch " + offModeZones);
                break;
            }
        }

        // FOR tempSetMax FUNCTION
        for (String currentZoneTempSetMax : (ArrayList<String>) tempSetMaxZones.get("Zones")) {
            if (levelDatapointsPerZone.containsKey(currentZoneTempSetMax)) {
                tempSetMaxZones.put("Datapoints", Arrays.asList(levelDatapointsPerZone.get(currentZoneTempSetMax)));
	            System.out.println("tempSetMaxZonesMatch " + tempSetMaxZones);
                break;
            }
        }

        // FOR tempSetMin FUNCTION
        for (String currentZoneTempSetMin : (ArrayList<String>) tempSetMinZones.get("Zones")) {
            if (levelDatapointsPerZone.containsKey(currentZoneTempSetMin)) {
                tempSetMinZones.put("Datapoints", Arrays.asList(levelDatapointsPerZone.get(currentZoneTempSetMin)));
	            System.out.println("tempSetMinZonesMatch " + tempSetMinZones);
                break;
            }
        }

        // FOR tempSetNormal FUNCTION
        for (String currentZoneTempSetNormal : (ArrayList<String>) tempSetNormalZones.get("Zones")) {
            if (levelDatapointsPerZone.containsKey(currentZoneTempSetNormal)) {
                tempSetNormalZones.put("Datapoints", Arrays.asList(levelDatapointsPerZone.get(currentZoneTempSetNormal)));
	            System.out.println("tempSetNormalZonesMatch " + tempSetNormalZones);
                break;
            }
        }
    }
    
    // WRITE COMMAND TO THE DATAPOINTS IDS THAT MATCH THE REQUIRED FUNCTION PER ZONE

    // using the updated dictionary per function which has: List of zones, Function type, Setpoint value (if applicable), Slot time, and List of Datapoints

		
}
