CREATE TABLE fleet_model
(
    id           INT PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255) NOT NULL,
    year         INT          NOT NULL
);

CREATE TABLE vehicle
(
    plate    VARCHAR(255) NOT NULL PRIMARY KEY,
    model_id INT          NOT NULL,
    color    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_model
        FOREIGN KEY (model_id)
            REFERENCES fleet_model (id)
);

CREATE TABLE vehicle_stats
(
    plate          VARCHAR(255) NOT NULL PRIMARY KEY,
    total_distance numeric      NOT NULL,
    avg_speed      numeric      NOT NULL,
    CONSTRAINT fk_plate
        FOREIGN KEY (plate)
            REFERENCES vehicle (plate)
);

CREATE TABLE vehicle_position
(
    plate        VARCHAR(255) NOT NULL PRIMARY KEY,
    city         VARCHAR(255),
    zone         VARCHAR(255),
    full_address VARCHAR(255),
    CONSTRAINT fk_plate
        FOREIGN KEY (plate)
            REFERENCES vehicle (plate)
);
