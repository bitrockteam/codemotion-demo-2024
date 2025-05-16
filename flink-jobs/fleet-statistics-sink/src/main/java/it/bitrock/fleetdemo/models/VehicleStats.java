package it.bitrock.fleetdemo.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleStats {
    String plate;
    double totalDistance;
    double averageSpeed;
}
