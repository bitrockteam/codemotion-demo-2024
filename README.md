Codemotion 2024 Fleet Demo
======================

Demo project for the Codemotion 2024 talk, forked and inspired by [Waterstream Demo](https://fleetdemo.waterstream.io/). See https://codemotion2024.bitrock.it to access the demo.

Tools used:
- [Kubernetes](https://kubernetes.io/) - open source system for automating deployment, scaling, and management of containerized applications.
- [Waterstream](https://waterstream.io) - Kafka-native MQTT broker
- [Radicalbit](https://radicalbit.ai) - Your ready-to-use MLOps platform for Machine Learning.
- [Flink](https://flink.apache.org/) - framework and distributed processing engine for stateful computations over unbounded and bounded data streams.
- [Confluent Kafka](https://www.confluent.io/) - The Data Streaming Platform, deployed with Kubernetes operator.
- [PostgreSQL](https://www.postgresql.org/) - the World's Most Advanced Open Source Relational Database.
- [Kafbat UI](https://github.com/kafbat/kafka-ui) - Versatile, fast and lightweight web UI for managing Apache Kafka clusters.
- [Openrouteservice](https://github.com/GIScience/openrouteservice) - A highly customizable, performant routing service written in Java.


Trucks are moving along the randomly assigned routes, reporting their location and nearest waypoint
to the MQTT broker.

Small subset of the fleet is displayed on the map. As the data ends up in Kafka Flink is used to build some aggregates.

Some of the results of aggregations (like the direction statistics) are retrieved back through MQTT
(remember - Waterstream is a Kafka-native MQTT broker) directly into UI using MQTT WebSocket transport.
Other aggregation result are persisted on PostgreSQL and used to provide RAG feature to an langchain agent.
