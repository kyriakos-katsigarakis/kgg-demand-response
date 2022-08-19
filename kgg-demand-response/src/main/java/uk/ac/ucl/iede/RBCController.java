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

public class RBCController {

	// PRICE EVENT checked every 15min
	// receives priceEventTrigger as return of isPriceEvent method 
	// receives futurePrice as return of searchPrice method 
	
	// OCC SCHEDULE EVENT checked every 1h
	// receives occScheduleEvent as return of isOccScheduleEvent method 
	// receives futureOccScheduledZones as return of searchOccSchedulePerZone method 
		
	
	// OCC STATUS EVENT checked every 1h
	// receives occStatusEvent as return of isOccStatusEvent method 
	// receives currentOccDetectedZones as return of searchOccSchedulePerZone method (eventOccStatus.searchOccSchedulePerZone)
	
	public static void main(String[] args) throws IOException, ParseException {
		
		Model DRgraph = RDFDataMgr.loadModel("D:/datos-flaand/DRgraph.ttl");
		Reader priceReader = new FileReader("D:/datos-flaand/prices.csv");	
		Reader scheduleReader = new FileReader("D:/datos-flaand/occSchedule.csv");	
		Reader statusReader = new FileReader("D:/datos-flaand/occStatus.csv");	
		
		EventPrice eventPrice = new EventPrice(); 
		eventPrice.searchPrice(priceReader);
		System.out.println("PriceEvent? " + eventPrice.isPriceEvent());
		
		EventOccSchedule eventOccSchedule = new EventOccSchedule(); 
		eventOccSchedule.searchOccIdPerZone(DRgraph);
		eventOccSchedule.searchOccSchedulePerZone(scheduleReader);
		System.out.println("OccScheduleEvent? " + eventOccSchedule.isOccScheduleEvent());	

		EventOccStatus eventOccStatus = new EventOccStatus();
		eventOccStatus.searchOccIdPerZone(DRgraph);
		eventOccStatus.searchOccStatusPerZone(statusReader);
		System.out.println("OccStatusEvent? " + eventOccStatus.isOccStatusEvent());	


		RBCController rbcController = new RBCController();
		rbcController.searchZones(DRgraph);
		rbcController.interpretRequiredFunction (eventOccStatus, eventPrice, eventOccSchedule);
		
	}
	
    ArrayList<String> listZones = new ArrayList<>();    			 // List of zones 
    ArrayList<String> currentOccDetectedZones = new ArrayList<>();    			 // List of zones where presence is detected
    ArrayList<String> futureOccScheduledZones = new ArrayList<>();    			 // List of zones where presence is detected
    double futurePrice = 0;					// future price
    public static enum functionTypesEnum { offMode, tempSetMax, tempSetMin, tempSetNormal }
    public double tempSetMaxValue; //add datapoint per zone?
    public double tempSetMinValue; //add datapoint per zone?
    public double tempSetNormalValue; //add datapoint per zone?
    public double priceThreshold = 0.0841;
    public long startSlot;                     // timeperiod to initiate the commands

    Map<Object, Object> offModeZones = new HashMap<>();
    Map<Object, Object> tempSetMaxZones = new HashMap<>();
    Map<Object, Object> tempSetNormalZones = new HashMap<>();
    Map<Object, Object> tempSetMinZones = new HashMap<>();

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
        		"SELECT DISTINCT ?building ?zone " + 
        		"WHERE { " +
        		"?building a brick:Building ;" +
        		"brick:hasPart ?storey ." +
        		
        		"?storey a brick:Storey; " +
        		"brick:hasPart ?space . " +
        		
        		"?zone a brick:Zone ;" +
        		"brick:hasPart ?space ." +
        		
        		" } "); 

        // EXECUTE QUERY TO GET A LIST OF ZONES AND THEIR OCCUPANCY SENSOR ID
        QueryExecution qe = QueryExecutionFactory.create(query, DRgraph);
        ResultSet results = qe.execSelect();
        //ResultSetFormatter.out(System.out, results, query);         // Print query results (table with building, zone, datapoint)

        // CREATE A DICTIONARY WITH ZONE URI AND OCC ID & AND ARRAYLIST WITH ZONE URIs
        while (results.hasNext()) {
            QuerySolution qSolution = results.nextSolution();
            String zoneURI = qSolution.get("zone").toString();
            listZones.add(zoneURI);
        }
        qe.close();
    }
	
	// NESTED RULES TO INTERPRET REQUIRED FUNCTION BASED ON THE COMBINATION OF EVENTS
    public void interpretRequiredFunction (EventOccStatus eventOccStatus, EventPrice eventPrice, EventOccSchedule eventOccSchedule) {
		
    	long currentTime = getCurrentTime(); // check for current time
    	currentOccDetectedZones = eventOccStatus.getCurrentOccDetectedZones();
        futureOccScheduledZones = eventOccSchedule.getFutureOccScheduledZones();
        futurePrice = eventPrice.getfuturePrice();
    	System.out.println("\nINPUTS:\n" + "currentOccDetectedZones " + currentOccDetectedZones + "\nfuturePrice " + futurePrice + "\npriceThreshold " + priceThreshold +  "\nfutureOccScheduledZones " + futureOccScheduledZones);	

    	//for (Iterator<String> zoneURI = listZones.iterator(); zoneURI.hasNext() ;){
        for (String zoneURI : listZones)	{
            if (currentOccDetectedZones.contains(zoneURI)) {
                if (futurePrice < priceThreshold) {
                    ArrayList<String> tempSetNormalZones = new ArrayList<>();
                    tempSetNormalZones.add(zoneURI);
                    startSlot = currentTime + 900; // To activate control in the future time, when there is the change of price
                    configFunction(functionTypesEnum.tempSetNormal, tempSetNormalZones, startSlot); // to send function to ACTUATOR (Tset = Tset normal), list of zones currently occupied
                }
                else {
                	ArrayList<String> tempSetMinZones = new ArrayList<>();
                    tempSetMinZones.add(zoneURI);
                    startSlot = currentTime + 900; // To activate control in the future time, when there is the change of price
                    configFunction(functionTypesEnum.tempSetMin, tempSetMinZones, startSlot); // to send function to ACTUATOR (Tset = Tset min) and list of zones currently occupied
                }
            } else if (futureOccScheduledZones.contains(zoneURI)) {
                if (0 < futurePrice && futurePrice < 0.2 * priceThreshold) {
                	ArrayList<String> tempSetMaxZones = new ArrayList<>();
                    tempSetMaxZones.add(zoneURI);
                    startSlot = currentTime + 3600; // To activate control in the future time, to activate pre-cooling
                    configFunction(functionTypesEnum.tempSetMax, tempSetMaxZones, startSlot); // to send function to ACTUATOR (Tset = Tset max) and list of zones not currently occupied but expected to be occupied
                }
                else if (0.2 * priceThreshold < futurePrice  && futurePrice < 0.7 * priceThreshold) {
                	ArrayList<String> tempSetNormalZones = new ArrayList<>();
                    tempSetNormalZones.add(zoneURI);
                    startSlot = currentTime + 3600; // To activate control in the future time, to activate pre-cooling
                    configFunction(functionTypesEnum.tempSetNormal, tempSetNormalZones, startSlot); // to send function to ACTUATOR (Tset = Tset normarl) and list of zones not currently occupied but expected to be occupied
                }
                else {
                	ArrayList<String> tempSetMinZones = new ArrayList<>();
                    tempSetMinZones.add(zoneURI);
                    startSlot = currentTime + 3600; // To activate control in the future time, to activate pre-cooling
                    configFunction(functionTypesEnum.tempSetMin, tempSetMinZones, startSlot); // to send function to ACTUATOR (Tset = Tset min) and list of zones not currently occupied but expected to be occupied
                }
            } else {
            	ArrayList<String> offModeZones = new ArrayList<>();
                offModeZones.add(zoneURI);
                startSlot = currentTime; // To activate control in the current time to turn off with no occupancy
                configFunction(functionTypesEnum.offMode, offModeZones, startSlot); //  to send to ACTUATOR (Off command) and list of zones not currently occupied and not expected to be occupied
            }
        }
    }

    // SEND FUNCTION, SETPOINTS, SLOT AND ZONES TO BE ACTIVATE IN THE CONTROL
    public void configFunction(functionTypesEnum functionTypes, ArrayList<String> zoneURIs, long startSlot) {

        switch (functionTypes) {
            case offMode:
                // write request for OnOff function for the defined zoneURIs with off command
                String onOffFunction = "onOffCmd";
                offModeZones.put("Zones", Arrays.asList(zoneURIs));
                offModeZones.put("Function", onOffFunction);
                offModeZones.put("Start time", startSlot);
        		System.out.println("offMode " + offModeZones);	
               // OMBody omBodyOut = new OMBody();
               // omBodyOut.set("JSON");
               // omBodyOut.setBody(offModeZones);
               // tell(omBodyOut);
                break;
            case tempSetMax:
                // write request for level function for the defined zoneURIs with tempSetMaxValue
                String tempSetMaxFunction = "levelCmd";
                tempSetMaxZones.put("Zones", Arrays.asList(zoneURIs));
                tempSetMaxZones.put("Function", tempSetMaxFunction);
                tempSetMaxZones.put("Setpoint value", tempSetMaxValue);
                tempSetMaxZones.put("Start time", startSlot);
        		System.out.println("tempSetMax " + tempSetMaxZones);	
               // OMBody omBodyOut = new OMBody();
               // omBodyOut.set("JSON");
               // omBodyOut.setBody(tempSetMaxZones);
               // tell(omBodyOut);
                break;
            case tempSetMin:
                // write request for level function for the defined zoneURIs with tempSetMinValue
                String tempSetMinFunction = "levelCmd";
                tempSetMinZones.put("Zones", Arrays.asList(zoneURIs));
                tempSetMinZones.put("Function", tempSetMinFunction);
                tempSetMinZones.put("Setpoint value", tempSetMinValue);
                tempSetMinZones.put("Start time", startSlot);
        		System.out.println("tempSetMin " + tempSetMinZones);	
               // OMBody omBodyOut = new OMBody();
               // omBodyOut.set("JSON");
               // omBodyOut.setBody(tempSetMinZones);
               // tell(omBodyOut);
                break;
            case tempSetNormal:
                // write request for level function for the defined zoneURIs with tempSetNormalValue
                String tempSetNormalFunction = "levelCmd";
                tempSetNormalZones.put("Zones", Arrays.asList(zoneURIs));
                tempSetNormalZones.put("Function", tempSetNormalFunction);
                tempSetNormalZones.put("Setpoint value", tempSetNormalValue);
                tempSetNormalZones.put("Start time", startSlot);
        		System.out.println("tempSetNormal " + tempSetNormalZones);	
               // OMBody omBodyOut = new OMBody();
               // omBodyOut.set("JSON");
               // omBodyOut.setBody(tempSetNormalZones);
               // tell(omBodyOut);
                break;
        }
    }
    
   public Map<Object, Object> getoffModeZones() {
        return offModeZones;
    }  
    
    public Map<Object, Object> gettempSetMaxZones() {
        return tempSetMaxZones;
    }  
    
    public Map<Object, Object> gettempSetMinZones() {
        return tempSetMinZones;
    }  
    
    public Map<Object, Object> getTempSetNormalZones() {
        return tempSetNormalZones;
    }  
	
}
