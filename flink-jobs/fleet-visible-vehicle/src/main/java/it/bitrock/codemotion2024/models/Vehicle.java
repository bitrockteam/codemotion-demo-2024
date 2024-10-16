package it.bitrock.codemotion2024.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {
    String plate;
    int modelId;
    String color;
    Location current;
    Location waypoint;
    double speed;
    double distance;
    boolean visible;
    long updateTimestamp;
    String id;
}
