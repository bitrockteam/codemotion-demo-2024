base:
		helmfile apply -f infra/helmfile.yaml -l component=base

confluent-operator:
		helmfile apply -f infra/helmfile.yaml -l component=confluent

kafka:
		helmfile apply -f infra/helmfile.yaml -l component=kafka

waterstream:
		helmfile apply -f infra/helmfile.yaml -l component=waterstream

flink:
		helmfile apply -f infra/helmfile.yaml -l component=flink

kafka-ui:
		helmfile apply -f infra/helmfile.yaml -l component=kafka-ui

openrouteservice:
		helmfile apply -f infra/helmfile.yaml -l component=openrouteservice

apps:
		helmfile apply -f infra/helmfile.yaml -l component=fleet-demo

setupDockerBuildx:
		docker buildx ls | grep codemotion-2024 &> /dev/null || docker buildx create --use --name codemotion-2024

packageVehicleSimulator:
		cd vehicle-simulator/; ./gradlew clean shadowJar

dockerVehicleSimulator: packageVehicleSimulator setupDockerBuildx
		docker buildx build \
			--builder codemotion-2024 \
			--push \
			--platform linux/amd64,linux/arm64 \
			-t simoexpo/codemotion-2024-vehicle-simulator:$(version) ./vehicle-simulator

packageVehicleSimulatorUI:
		cd vehicle-simulator-ui/; ./gradlew clean shadowJar

dockerVehicleSimulatorUI: packageVehicleSimulatorUI setupDockerBuildx
		docker buildx build \
			--builder codemotion-2024 \
			--push \
			--platform linux/amd64,linux/arm64 \
			-t simoexpo/codemotion-2024-vehicle-simulator-ui:$(version) ./vehicle-simulator-ui

packageFlinkJobDirection:
		cd flink-jobs; ./mvnw -pl fleet-direction-counter package

packageFlinkJobVehicle:
		cd flink-jobs; ./mvnw -pl fleet-vehicle-sink package

packageFlinkJobStats:
		cd flink-jobs; ./mvnw -pl fleet-statistics-sink package

packageFlinkJobLocation:
		cd flink-jobs; ./mvnw -pl fleet-location-sink package

packageFlinkJobVisible:
		cd flink-jobs; ./mvnw -pl fleet-visible-vehicle package

dockerFlinkJobDirection: packageFlinkJobDirection
		docker buildx build \
			--builder codemotion-2024 \
			--push \
			--platform linux/amd64,linux/arm64 \
			-t simoexpo/codemotion-2024-fleet-direction-counter:$(version) ./flink-jobs/fleet-direction-counter

dockerFlinkJobVehicle: packageFlinkJobVehicle
		docker buildx build \
			--builder codemotion-2024 \
			--push \
			--platform linux/amd64,linux/arm64 \
			-t simoexpo/codemotion-2024-fleet-vehicle-sink:$(version) ./flink-jobs/fleet-vehicle-sink

dockerFlinkJobStats: packageFlinkJobStats
		docker buildx build \
			--builder codemotion-2024 \
			--push \
			--platform linux/amd64,linux/arm64 \
			-t simoexpo/codemotion-2024-fleet-statistics-sink:$(version) ./flink-jobs/fleet-statistics-sink

dockerFlinkJobLocation: packageFlinkJobLocation
		docker buildx build \
			--builder codemotion-2024 \
			--push \
			--platform linux/amd64,linux/arm64 \
			-t simoexpo/codemotion-2024-fleet-location-sink:$(version) ./flink-jobs/fleet-location-sink

dockerFlinkJobVisible: packageFlinkJobVisible
		docker buildx build \
			--builder codemotion-2024 \
			--push \
			--platform linux/amd64,linux/arm64 \
			-t simoexpo/codemotion-2024-fleet-visible-vehicle:$(version) ./flink-jobs/fleet-visible-vehicle

packageLlmQuery:
	python3 -m venv llm-query/.venv
	. llm-query/.venv/bin/activate; pip install -r llm-query/requirements.txt

dockerLlmQuery: packageLlmQuery setupDockerBuildx
	docker buildx build \
		--builder codemotion-2024 \
		--push \
		--platform linux/amd64,linux/arm64 \
		-t simoexpo/llm-query:$(version) ./llm-query