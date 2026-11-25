
package com.example.khan_hamza_s2468448;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.StringReader;
import java.util.ArrayList;

public class TrafficParser {

    // Stores each traffic situation
    public static class Situation {
        String id = "";
        String type = "";
        String category = "";
        String description = "";
        String startTime = "";
        String endTime = "";
        String delay = "";
        String direction = "";
        String restrictedLanes = "";
        String operationalLanes = "";
        String eventType = "";

        ArrayList<Location> locations = new ArrayList<>();
    }

    // Stores location information
    public static class Location {
        String position = "";
        String latitude = "";
        String longitude = "";

        ArrayList<String> names = new ArrayList<>();
    }

    public ArrayList<Situation> parse(
            String xml, String category) throws Exception {

        ArrayList<Situation> situations = new ArrayList<>();
        ArrayList<String> tags = new ArrayList<>();

        XmlPullParserFactory factory =
                XmlPullParserFactory.newInstance();

        factory.setNamespaceAware(true);

        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xml));

        Situation current = null;
        Location location = null;

        int event = parser.getEventType();

        while (event != XmlPullParser.END_DOCUMENT) {

            if (event == XmlPullParser.START_TAG) {

                String tag = parser.getName();
                tags.add(tag);

                if (tag.equals("situationRecord")) {

                    current = new Situation();
                    current.category = category;

                    current.id = parser.getAttributeValue(
                            null, "id");

                    current.type = parser.getAttributeValue(
                            "http://www.w3.org/2001/XMLSchema-instance",
                            "type");

                } else if (current != null) {

                    if (tag.equals("from") || tag.equals("to")) {

                        location = new Location();
                        location.position = tag;
                        current.locations.add(location);

                    } else {

                        boolean consumed = true;

                        switch (tag) {

                            case "value":
                                if (tags.contains("generalPublicComment")) {
                                    current.description = parser.nextText();
                                } else if (location != null) {
                                    location.names.add(parser.nextText());
                                } else {
                                    consumed = false;
                                }
                                break;

                            case "overallStartTime":
                                current.startTime = parser.nextText();
                                break;

                            case "overallEndTime":
                                current.endTime = parser.nextText();
                                break;

                            case "delaysType":
                                current.delay = parser.nextText();
                                break;

                            case "tpegDirection":
                                current.direction = parser.nextText();
                                break;

                            case "numberOfLanesRestricted":
                                current.restrictedLanes = parser.nextText();
                                break;

                            case "numberOfOperationalLanes":
                                current.operationalLanes = parser.nextText();
                                break;

                            case "roadOrCarriagewayOrLaneManagementType":
                            case "abnormalTrafficType":
                            case "roadMaintenanceType":
                                current.eventType = parser.nextText();
                                break;

                            case "latitude":
                                if (location != null) {
                                    location.latitude = parser.nextText();
                                } else {
                                    consumed = false;
                                }
                                break;

                            case "longitude":
                                if (location != null) {
                                    location.longitude = parser.nextText();
                                } else {
                                    consumed = false;
                                }
                                break;

                            default:
                                consumed = false;
                        }

                        if (consumed) {
                            tags.remove(tags.size() - 1);
                        }
                    }
                }

            } else if (event == XmlPullParser.END_TAG) {

                String tag = parser.getName();

                if (tag.equals("situationRecord") && current != null) {

                    situations.add(current);
                    current = null;
                    location = null;

                } else if (tag.equals("from") || tag.equals("to")) {

                    location = null;
                }

                tags.remove(tags.size() - 1);
            }

            event = parser.next();
        }

        return situations;
    }
}
