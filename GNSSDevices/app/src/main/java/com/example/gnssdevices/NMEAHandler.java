package com.example.gnssdevices;

import java.util.HashMap;
import java.util.Map;

public class NMEAHandler {
    //java interfaces
    interface SentenceParser {
        void parse(String[] tokens, GPSPosition position);
    }

    public static class GPSPosition {
        public float lat = 0.0f;
        public float lon = 0.0f;
        public float dir = 0.0f;
        public float velocity = 0.0f;

    }

    GPSPosition position = new GPSPosition();

    static float Latitude2Decimal(String lat, String NS) {
        float med = Float.parseFloat(lat.substring(2))/60.0f;
        med +=  Float.parseFloat(lat.substring(0, 2));
        if(NS.startsWith("S")) {
            med = -med;
        }
        return med;
    }

    static float Longitude2Decimal(String lon, String WE) {
        float med = Float.parseFloat(lon.substring(3))/60.0f;
        med +=  Float.parseFloat(lon.substring(0, 3));
        if(WE.startsWith("W")) {
            med = -med;
        }
        return med;
    }

    // parsers
    static class RMC implements SentenceParser {
        public void parse(String [] tokens, GPSPosition position) {
            position.lat = ((tokens[3] == "0") || (tokens[4] == "0")) ? 0.0f : Latitude2Decimal(tokens[3], tokens[4]);
            position.lon = ((tokens[5] == "0") || (tokens[6] == "0")) ? 0.0f : Longitude2Decimal(tokens[5], tokens[6]);
            position.velocity = (tokens[7] == "0") ? 0.0f : Float.parseFloat(tokens[7]);
            position.dir = (tokens[8] == "0") ? 0.0f : Float.parseFloat(tokens[8]);
        }
    }
    
    private static final Map<String, SentenceParser> sentenceParsers = new HashMap<String, SentenceParser>();

    public NMEAHandler() {
        sentenceParsers.put("RMC", new RMC());
    }

    public GPSPosition parse(String line) {

        if(line.startsWith("$G")) {
            String nmea = line.substring(1);
            String[] tokens = nmea.split(",");
            String type = tokens[0].substring(2);
            int i = 0;
            for(String x : tokens)
            {
                if(x.isEmpty())
                {
                    tokens[i] = "0";
                }
                i++;
            }
            //TODO check crc
            if(sentenceParsers.containsKey(type)) {
                sentenceParsers.get(type).parse(tokens, position);
            }
        }

        return position;
    }
}
