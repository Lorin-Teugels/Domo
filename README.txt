Dit is het project van groep 17.
Alles is getest in Eclipse Neon.0, Neon.1 en Neon.2 op zowel windows 10 en Ubuntu 16.04

Externe Libraries:
	Voor elke klasse worden de volgende libraries gebruikt:
		avro-1.7.7.jar
		avro-ipc-1.7.7.jar
		avro-ipc-1.7.7-source.jar
		jackson-core-asl-1.9.13.jar
		jackson-mapper-asl-1.9.13.jar
		slf4j-api-1.7.7.jar
		slf4j-simple-1.7.7.jar
	De UserClient maakt de UI met behulp van:
		asg.cliche-110413.jar
	Voor het compileren van protocols hebben we gebruik gemaakt van:
		avro-tools-1.7.7.jar

Runnables:
	DomoticsServer.java:
		Dit is de Server klasse in ons project. Functie main neemt 2 variabelen: 
			De poort waarop je wil luisteren
			Het IP address waarop je wil runnen.

	TemperatureSensor.java:
		Dit is de Temperatuur sensor in ons project. Functie main neemt 4 variabelen:
			De poort waarop de server luistert.
			Het IP address waarop de server luistert.
			De poort waarop je de temperatuursensor wil laten luisteren.
			Het IP address waarop je de temperatuursensor wil runnen.

	LightClient.java:
		Dit is het lichtje in ons project. Functie main neemt 4 variabelen:
			De poort waarop de server luistert.
			Het IP address waarop de server luistert.
			De poort waarop je het lichtje wil laten luisteren.
			Het IP address waarop je het lichtje wil runnen.

	SmartFridge.java:
		Dit is de ijskast in ons project. Functie main neemt 4 variabelen:
			De poort waarop de server luistert.
			Het IP address waarop de server luistert.
			De poort waarop je de ijskast wil laten luisteren.
			Het IP address waarop je de ijskast wil runnen.

	UserClient.java:
		Dit is de user in ons project. Functie main neemt 4 variabelen:
			De poort waarop de server luistert.
			Het IP address waarop de server luistert.
			De naam van de user.
			Het IP address waarop je de user wil runnen.

	De poorten zijn ook de ID's van alle klassen.
	Als een IP-address ontbreekt wordt het 127.0.0.1.
	De standaard poort is 6789. Indien het ID al genomen is, beslist de server een nieuw ID.

UserInterface User:
	Voor een lijst van alle commando's tijdens het runnen: type ?list.
	Hieronder staan ze allemaal onder elkaar:
		enter-house:
			verbindt met de server
		
		leave-house:
			verbreekt verbinding met de server

		get-clients:
			krijg een lijst met alle servers, users, lichten en ijskasten.

		get-servers:
			krijg een lijst met alle servers en of ze verbonden zijn of niet.

		get-users:
			krijg een lijst met alle users en of ze binnen zijn of niet.

		get-lights:
			krijg een lijst met alle lichten en of ze aan zijn of niet.

		get-fridges:
			krijg een lijst met alle ijskasten en hun inhoud.

		get-temperature:
			krijg de huidige temperatuur

		get-temperature-history:
			krijg de laatste 3 temperaturen

		open-fridge fridgeID:
			probeert de fridge met fridgeID te openen. Print iets als dit niet lukt.

		close-fridge:
			sluit de geopende ijskast

		add-item-to-fridge:
			voegt iets toe aan de geopende ijskast

		remove-item-from-fridge:
			verwijdert een item van de geopende ijskast

		switch-light lightID:
			probeert licht met lightID van staat te veranderen. Print iets als dit niet lukt.

		hello:
			print Hello World

		Alle commando's kunnen ook met hun afkortingen opgeroepen worden. 
		Deze afkortingen zijn telkens de eerst letters van elk woord van het commando.

FileSysteem:
	We hebben ons Systeem opgedeeld in 3 grote mappen:
		lib:
			Hierin staan alle gebruikte libraries

		src:
			Dit bevat al onze zelfgeschreven code. Deze is opgedeeld in verschillend packages:
				avro.domotics:
					abstracte superklassen
				avro.domotics.util:
					zelf geschreven klasse voor poort en IP adress bij te houden
				avro.domotics.server:
					bevat server klasse
				avro.domotics.smartFridge:
					bevat ijskast klasse
				avro.domotics.lights.client:
					bevat de lichten
				avro.domotics.domoticts.temperatureSensor:
					bevat de temperatuursensor
				avro.domotics.user
					bevat de gebruikers

		protocols:
			bevat 2 submappen:
				avpr:
					bevat de avpr bestanden voor onze protocols
				avro:
					bevat de gecompileerde protocols

Architectuur:
	Wij hebben gebruik gemaakt van een inheritance-structuur. De meest basische klasse is:
		Client.java

	Deze heeft 2 subklassen:
		SimpleClient.java
		ElectableClient.java

	Deze hebben elks ook nog subklassen:
		SimpleClient:
			LightClient.java
			TemperatureSensor.java
		ElectableClient:
			DomoticsServer.java
			SmartFridge.java
			UserClient.java

	Dit hebben we gedaan omdat er een heel groot verschil is in functionaliteit tussen de SimpleClients en ElectableClients.
	Toch wilden we 1 superklasses waarop alles is gebaseerd.
De ElectabelClients kunnen allemaal servers worden en hebben dus heel veel functionaliteit gemeen. 
