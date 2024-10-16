package it.bitrock.codemotion2024.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehiclePosition {
    String plate;
    String city;
    String zone;
    String fullAddress;
}
