package uk.ac.ucl.iede;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.*;

public class EventPrice {
	
	// PRICE EVENT checked every 15min
	// sends priceEventTrigger as return of isPriceEvent method 
	// sends futurePrice as return of getfuturePrice() method 
	
	public static void main(String[] args) throws IOException, ParseException {
				 
		Reader priceReader = new FileReader("D:/datos-flaand/prices.csv");	
		EventPrice eventPrice = new EventPrice(); 
		eventPrice.searchPrice(priceReader);
		System.out.println("PriceEvent? " + eventPrice.isPriceEvent());

	}
	
    private boolean priceEvent = false;     // energy price event trigger
    double futurePrice = 0;					// future price

    // GET CURRENT TIME (epoch time: https://www.epochconverter.com/)
    public int getCurrentTime () {
        long epoch = (System.currentTimeMillis()) / 1000; // Returns epoch in seconds.
	    // Epoch date 	Converted epoch
        // 1640995200	Sat, 01 Jan 2022 00:00:00 +0000
        long correctedCurrentTime = epoch - 1640995200; // Corrected to  match with the time in the timeseries file which starts with 0 to represent January 1st 00:00:00)
        return (int) correctedCurrentTime;  
    	
    }

	public void searchPrice(Reader targetReader) throws IOException {
		long currentTime = getCurrentTime(); // check for current time
		long futureTime = currentTime + 900; // check for current time + 15min
        double currentPrice = 0;
        
	    // READ CSV FILE
        Iterable<CSVRecord> recordList = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(targetReader).getRecords();
        for(CSVRecord record : recordList) {

            Integer currentSlot =  Integer.parseInt(record.get("time"));
            Integer futureSlot =  (Integer.parseInt(record.get("time")) + 900); // slot of 15min
            //System.out.println("csv time " + currentSlot + " " + futureSlot);

            // CHECK CURRENT SCHEDULED PRICE FOR ALL ZONES
            if (currentTime > currentSlot &  currentTime < futureSlot )  {
            	currentPrice = Double.parseDouble(record.get("PriceElectricPowerDynamic")); 
	            System.out.println("currentTime " + currentTime + " price " + currentPrice);
            }
            // CHECK FUTURE SCHEDULED PRICE FOR ALL ZONES (CURRENT TIME + 15 MIN)
           if (futureTime > currentSlot &  futureTime < futureSlot ) {
        	   futurePrice = Double.parseDouble(record.get("PriceElectricPowerDynamic")); 
	            System.out.println("futureTime " + futureTime + " price " + futurePrice);
               break;
           }           
        }
        // CHECK IF THERE IS EVENT PRICE (FUTURE PRICE CHANGES FROM THE CURRENT PRICE)
        if (futurePrice != currentPrice) {
            setPriceEvent(true);
        }
        else { setPriceEvent(false);}
    }

    public void setPriceEvent(boolean priceEvent) {
        this.priceEvent = priceEvent;
    }

    public boolean isPriceEvent() {
        return priceEvent;
    }
    
    public double getfuturePrice() {
        return futurePrice;
    }  
    
    // SEND EVENT TYPE AND FUTURE PRICE
   /*
    public void sendMessage() {
	    if (isPriceEvent()){
	        OMBody omBodyOut = new OMBody();
	        omBodyOut.set("JSON");
	        omBodyOut.setBody(getfuturePrice());
	        tell(omBodyOut);
	    }
    }
    */
    
}
