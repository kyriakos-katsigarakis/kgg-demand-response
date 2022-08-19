package uk.ac.ucl.iede.dt.as.actor;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import uk.ac.ucl.iede.dt.as.message.OMBody;
import uk.ac.ucl.iede.dt.as.model.Entity;
import uk.ac.ucl.iede.dt.dao.DataCollectionRepository;
import uk.ac.ucl.iede.dt.dom.DataCollection;
import uk.ac.ucl.iede.dt.dom.ProjectProperty;
import uk.ac.ucl.iede.dt.kgg.ResourceConverter;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OMActorResourceGraph extends OMActorBase {

	private static final Logger log = LoggerFactory.getLogger(OMActorResourceGraph.class);
	
	@Autowired
	private DataCollectionRepository dataCollectionRepository;
	
	public OMActorResourceGraph(Entity entity) {
		super(entity);
		log.info("resource ontology actor, init=" + getSelf().path().toString());
	}

	@Override
	public void onReceive(Object message) throws Throwable {		
		if(message instanceof OMBody) {
			OMBody omBodyIn = (OMBody) message;
			Path temp = Files.createTempFile("BAStags",".csv");
			File tempFile = temp.toFile();
			FileUtils.writeStringToFile(tempFile, omBodyIn.getBody(), Charset.forName("utf-8"));
			Reader targetReader = new FileReader(tempFile);
			Optional<DataCollection> dataCollectionOpt = dataCollectionRepository.findById(omBodyIn.getCollectionId());
			if(dataCollectionOpt.isPresent()) {
				DataCollection dataCollection = dataCollectionOpt.get();
				ResourceConverter resourceConverter = new ResourceConverter();
				//
				Optional<ProjectProperty> propertyOpt = dataCollection.getProject().getProperties().stream().filter(p -> p.getPropertyName().equalsIgnoreCase("project.name")).findFirst();
				if(propertyOpt.isPresent()) {
					ProjectProperty property = propertyOpt.get();
					Model rdfModel = resourceConverter.convert(targetReader, property.getPropertyValue());
					StringWriter stringWriter = new StringWriter();
					rdfModel.write(stringWriter,"ttl");
					//
					OMBody omBodyOut = new OMBody();
					omBodyOut.setCollectionId(omBodyIn.getCollectionId());
					omBodyOut.setId(null);
					omBodyOut.setType(null);
					omBodyOut.setExtension("ttl");
					omBodyOut.setBody(stringWriter.toString());
					//
					tell(omBodyOut);
				}
			}
		}
	}
}