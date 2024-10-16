package it.bitrock.codemotion2024.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location {
    public static final double EARTH_RADIUS = 6371;
    double lat;
    double lng;

    double distance(Location location) {
        double lat1Rad = Math.toRadians(lat);
        double lat2Rad = Math.toRadians(location.lat);
        double lon1Rad = Math.toRadians(lng);
        double lon2Rad = Math.toRadians(location.lng);

        double x = (lon2Rad - lon1Rad) * Math.cos((lat1Rad + lat2Rad) / 2);
        double y = (lat2Rad - lat1Rad);

        return Math.sqrt(x * x + y * y) * EARTH_RADIUS;
    }
}
