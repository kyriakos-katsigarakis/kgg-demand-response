package uk.ac.ucl.iede;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.reasoner.rulesys.RDFSRuleReasonerFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.springframework.stereotype.Service;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.graph.Graph;

@Service
public class ResourceConverter {

	private static Logger log = LoggerFactory.getLogger(ResourceConverter.class);
		
	private Model rdfModel;
	private String projectName;
	
	public ResourceConverter() {
		log.info("CSV Resource KGG");
	}
	
	
	public Model convert(Reader in, String projectName) throws IOException, ParseException {
		this.projectName = projectName;
		rdfModel = ModelFactory.createDefaultModel();
		rdfModel.setNsPrefix("owl", OWL.getURI());
		rdfModel.setNsPrefix("rdf", RDF.getURI());
		rdfModel.setNsPrefix("xsd", XSD.getURI());
		rdfModel.setNsPrefix("rdfs", RDFS.getURI());
		rdfModel.setNsPrefix("schema", "http://schema.org#");
		rdfModel.setNsPrefix("brick", "https://brickschema.org/schema/Brick#");
		rdfModel.setNsPrefix("om", "http://openmetrics.eu/openmetrics#");
		rdfModel.setNsPrefix("saref", "https://saref.etsi.org/core/");
		rdfModel.setNsPrefix("props", "https://w3id.org/props#");
		Iterable<CSVRecord> recordList = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in).getRecords();
		parseResources(recordList);
		
		//Trying Reasoner engine
				/*
				Model brick = RDFDataMgr.loadModel("D:/datos-flaand/Brick.ttl");
				Model saref = RDFDataMgr.loadModel("D:/datos-flaand/Saref.ttl");
				Reasoner reasoner = ReasonerRegistry.getRDFSSimpleReasoner();
				Reasoner boundReasonerBrick = reasoner.bindSchema(brick);
				Reasoner boundReasonerSaref = reasoner.bindSchema(saref);
				InfModel infModelBrick = ModelFactory.createInfModel(boundReasonerBrick, rdfModel);
				InfModel infModelSaref = ModelFactory.createInfModel(boundReasonerSaref, infModelBrick);

				return (infModelBrick);
				//return (infModelSaref);
		*/
		
		return rdfModel;
	}
	
	
	private void parseResources(Iterable<CSVRecord> recordIterator) throws ParseException {
		for(CSVRecord record : recordIterator) {
			HashSet<String> uniqueValues = new HashSet<>();
			uniqueValues.add(record.get("deviceSerialNumber"));
			// test
			Iterator<String> iterate_value = uniqueValues.iterator();
			while (iterate_value.hasNext()) {
				if (record.get("deviceSerialNumber").equals(iterate_value.next())) {
					String deviceSerialNumber = record.get("deviceSerialNumber");
					Resource resController =  rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_Controller");
					resController.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "Equipment"));																				
					resController.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("saref") + "Device"));																					
					resController.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasSerialNumber"), ResourceFactory.createStringLiteral(deviceSerialNumber));
					String endpoint = record.get("endpoint");
					
					if(record.get("tag").contains("sensor")) {
						if(record.get("tag").contains("occ")) {
							Resource resOccupancySensor =  rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_OccupancySensor");
							resOccupancySensor.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "Occupancy_Sensor"));																		
							resOccupancySensor.addProperty( ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "timeseries"), endpoint);
							resOccupancySensor.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPointOf"), resController);
							//upd parent
							resController.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPoint"), resOccupancySensor);
						}else if(record.get("tag").contains("temp")) {
							Resource resTemperatureSensor =  rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_TemperatureSensor");
							resTemperatureSensor.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "Zone_Air_Temperature_Sensor"));																		
							resTemperatureSensor.addProperty( ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "timeseries"), endpoint);
							resTemperatureSensor.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPointOf"), resController);
							//upd parent
							resController.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPoint"), resTemperatureSensor);
						}else if(record.get("tag").contains("power")) {
							Resource resPowerSensor =  rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_PowerSensor");
							resPowerSensor.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "Active_Power_Sensor"));																		
							resPowerSensor.addProperty( ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "timeseries"), endpoint);
							resPowerSensor.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPointOf"), resController);
							//upd parent
							resController.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPoint"), resPowerSensor);
						} else {}
						
					}else if(record.get("tag").contains("cmd") || record.get("tag").contains("sp") || record.get("tag").contains("writeble")){					
						if(record.get("tag").contains("sp") && record.get("tag").contains("max") == false && record.get("tag").contains("min") == false){
							Resource resZoneSetPoint = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_ZoneSetPointCommand");
							resZoneSetPoint.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "Zone_Air_Temperature_Setpoint"));
							resZoneSetPoint.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("saref") + "setLevelCommand"));
							// trying link b/w terminal unit and points (maybe if IFC & CSV are parsed in the same code this could work; If running inside the space/zone loop - TBD)
							//tba resZoneSetStatus  = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_ZoneSetStatus");
							//tba resZoneSetStatus.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("saref") + "MultiLevelState"));
							//tba resZoneSetPoint.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "actsUpon"), resZoneSetStatus));
							//tba resHVAC.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "hasState"), resZoneSetStatus); 
							
							resZoneSetPoint.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "timeseries"), endpoint);
							resZoneSetPoint.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPointOf"), resController);
							//upd parent
							resController.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPoint"), resZoneSetPoint);
							
							Resource resSetPointFunction = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_SetPointFunction");
							resSetPointFunction.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("saref") + "LevelControlFunction"));
							resZoneSetPoint.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "isCommandOf"), resSetPointFunction);
							//upd parent
							resSetPointFunction.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "hasCommand"), resZoneSetPoint);
							//upd parent
							resController.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "hasFunction"), resSetPointFunction);
						}else if(record.get("tag").contains("sp") && record.get("tag").contains("max") == true && record.get("tag").contains("min") == false){
							Resource resSetpointMax = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_SetpointMax");
							resSetpointMax.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "Max_Air_Temperature_Setpoint"));
							resSetpointMax.addProperty( ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "timeseries"), endpoint);
							resSetpointMax.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPointOf"), resController);
							//upd parent
							resController.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPoint"), resSetpointMax);
						}else if(record.get("tag").contains("sp") && record.get("tag").contains("max") == false && record.get("tag").contains("min") == true){
							Resource resSetpointMin = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_SetpointMin");
							resSetpointMin.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "Min_Air_Temperature_Setpoint"));
							resSetpointMin.addProperty( ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "timeseries"),  endpoint);
							resSetpointMin.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPointOf"), resController);
							//upd parent
							resController.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPoint"), resSetpointMin);	
						}else if(record.get("tag").contains("onoff")){
							Resource resOnCommand = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_OnCommand");
							Resource resOffCommand = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_OffCommand");
							// trying link b/w terminal unit and points (maybe if IFC & CSV are parsed in the same code this could work; If running inside the space/zone loop - TBD)
							//tba resZoneOnOffStatus  = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_ZoneOnOffStatus");
							//tba resZoneOnOffStatus.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("saref") + "OnOffState"));
							//tba resOnOffCommand.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "actsUpon"), resZoneOnOffStatus));
							//tba resHVAC.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "hasState"), resZoneOnOffStatus); 
							
							resOnCommand.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "On_Command"));
							resOnCommand.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("saref") + "OnCommand"));
							resOnCommand.addProperty( ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "timeseries"),endpoint);
							resOffCommand.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "Off_Command"));
							resOffCommand.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("saref") + "OffCommand"));
							resOffCommand.addProperty( ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "timeseries"),endpoint);
							
							Resource resOnOffFunction = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_OnOffFunction");
							resOnOffFunction.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("saref") + "OnOffFunction"));
							//upd parent
							resController.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "hasFunction"), resOnOffFunction);
							resOnCommand.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "isCommandOf"), resOnOffFunction);
							resOffCommand.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "isCommandOf"), resOnOffFunction);
							//upd parent
							resOnOffFunction.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "hasCommand"), resOnCommand);	
							resOnOffFunction.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("saref") + "hasCommand"), resOffCommand);	
						}else if(record.get("tag").contains("sendsettings")){
							Resource resSendSettings = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + deviceSerialNumber + "_Run");
							resSendSettings.addProperty(RDF.type, ResourceFactory.createResource(rdfModel.getNsPrefixURI("brick") + "Enable_Command"));
							resSendSettings.addProperty( ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "timeseries"),  endpoint);
							resSendSettings.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "isPointOf"), resController);
							//upd parent
							resController.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPoint"), resSendSettings);	
						}else{}
					}
					
				}
				
			}
			
		}	
				
	}			
		
}
