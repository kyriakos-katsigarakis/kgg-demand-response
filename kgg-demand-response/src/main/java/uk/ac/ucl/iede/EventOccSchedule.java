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

public class EventOccSchedule {
	
	// OCC SCHEDULE EVENT checked every 1h
	// sends occScheduleEvent as return of isOccScheduleEvent method 
	// sends futureOccScheduledZones as return of getFutureOccScheduledZones() method 
	
	public static void main(String[] args) throws IOException, ParseException {		
		
		Reader scheduleReader = new FileReader("D:/datos-flaand/occSchedule.csv");	
		Model DRgraph = RDFDataMgr.loadModel("D:/datos-flaand/DRgraph.ttl");
		EventOccSchedule eventOccSchedule = new EventOccSchedule(); 
		eventOccSchedule.searchOccIdPerZone(DRgraph);
		eventOccSchedule.searchOccSchedulePerZone(scheduleReader);
		System.out.println("OccScheduleEvent? " + eventOccSchedule.isOccScheduleEvent());	
	}

    private boolean occScheduleEvent = false;     						 // Event trigger
    Map<String, String> occIdPerZone = new HashMap<>();                  // Dictionary for ZoneURI: OccID
    ArrayList<String> futureOccScheduledZones = new ArrayList<>();    			 // List of zones where presence is detected
    
    // GET CURRENT TIME (epoch time: https://www.epochconverter.com/)
    public int getCurrentTime () {
        long epoch = (System.currentTimeMillis()) / 1000; // Returns epoch in seconds.
	    // Epoch date 	Converted epoch
        // 1640995200	Sat, 01 Jan 2022 00:00:00 +0000
        long correctedCurrentTime = epoch - 1640995200; // Corrected to  match with the time in the timeseries file which starts with 0 to represent January 1st 00:00:00)
        return (int) correctedCurrentTime;  
    	
    }
    
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
	public void searchOccIdPerZone(Model DRgraph) throws IOException {

        // CREATE GRAPH QUERY TO GET A LIST OF ZONES AND THEIR OCCUPANCY SENSOR ID PER BUILDING
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
        		"SELECT DISTINCT ?building ?zone ?datapoint " + 
        		"WHERE { " +
        		"?building a brick:Building ;" +
        		"brick:hasPart ?storey ." +
        		
        		"?storey a brick:Storey; " +
        		"brick:hasPart ?space . " +
        		
        		"?zone a brick:Zone ;" +
        		"brick:hasPart ?space ." +
        		
        		"?occSensor a brick:Occupancy_Sensor ;" +
                "brick:hasLocation ?space;" +
        		"brick:timeseries ?datapoint." +
        		" } "); 

        // EXECUTE QUERY TO GET A LIST OF ZONES AND THEIR OCCUPANCY SENSOR ID
        QueryExecution qe = QueryExecutionFactory.create(query, DRgraph);
        ResultSet results = qe.execSelect();
        //ResultSetFormatter.out(System.out, results, query);         // Print query results (table with building, zone, datapoint)

        // CREATE A DICTIONARY WITH ZONE URI AND OCC ID & AND ARRAYLIST WITH ZONE URIs
        while (results.hasNext()) {
            QuerySolution qSolution = results.nextSolution();
            String zoneURI = qSolution.get("zone").toString();
            String occID = qSolution.getLiteral("datapoint").getString();
            occIdPerZone.put(zoneURI, occID);
        }
        qe.close();
    }
	

    // LOAD TIMESERIES DATABASE BASED ON THE OCCID PER ZONE TO GET OCCUPANCY SCHEDULE VALUE
	public void searchOccSchedulePerZone(Reader targetReader) throws IOException {
		long currentTime = getCurrentTime(); // check for current time
		long futureTime = currentTime + 3600; // check for current time + 1h
        double currentOccSchedule = 0;
        double futureOccSchedule = 0;
        String occScheduledZone = "";
        
        // GET SCHEDULE PER ZONE
        for (Map.Entry<String, String> queryList : occIdPerZone.entrySet()) {
        	System.out.println("zone " + queryList.getKey() + " datapoint " + queryList.getValue());
        	String datapoint = queryList.getValue();
            
	    // READ CSV FILE
	        Iterable<CSVRecord> recordList = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(targetReader).getRecords();
	        for(CSVRecord record : recordList) {
	
	            Integer currentSlot =  Integer.parseInt(record.get("time"));
	            Integer futureSlot =  (Integer.parseInt(record.get("time")) + 3600); // slot of 1h
	            //System.out.println("csv time " + currentSlot + " " + futureSlot);
	
	            // CHECK CURRENT OCC SENSING FOR EACH ZONE 
	            if (currentTime > currentSlot &  currentTime < futureSlot )  {
	            	currentOccSchedule = Integer.parseInt(record.get(datapoint)); 
		            System.out.println("currentTime " + currentTime + " schedule " + currentOccSchedule);
	            }
	            
	          // CHECK FUTURE SENSING FOR EACH ZONE (CURRENT TIME - 1 HOUR) 
		           if (futureTime > currentSlot &  futureTime < futureSlot ) {
		        	   futureOccSchedule = Integer.parseInt(record.get(datapoint)); 
		               System.out.println("futureTime " + futureTime + " schedule " + futureOccSchedule);
		                break;
		           }
		               
	        }
	        // CHECK IF THERE IS EVENT (CURRENT SCHEDULE CHANGES FROM THE FUTURE SCHEDULE)
	        if (futureOccSchedule != currentOccSchedule) {
	        	setOccScheduleEvent(true);
	        }
	        else { setOccScheduleEvent(false);}
        
	        if (futureOccSchedule == 1) {
	        	occScheduledZone = queryList.getKey();
	        	//System.out.println("Zones that will be occupied " + occScheduledZone);
	        }
        }
        futureOccScheduledZones.add(occScheduledZone);
    }	

    public void setOccScheduleEvent(boolean occSchedule) {
        this.occScheduleEvent = occSchedule;
    }

    public boolean isOccScheduleEvent() {
        return occScheduleEvent;
    }  
    
    public ArrayList<String> getFutureOccScheduledZones() {
        return futureOccScheduledZones;
    }  
    
    // SEND EVENT TYPE AND LIST OF ZONES WITH FUTURE OCCUPANCY SCHEDULE
   /*
    public void sendMessage() {
	    if (isOccScheduleEvent()){
	        OMBody omBodyOut = new OMBody();
	        omBodyOut.set("JSON");
	        omBodyOut.setBody(getFutureOccScheduledZones());
	        tell(omBodyOut);
	    }
    }
    */

}
