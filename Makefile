.PHONY: default all clean

default: all

all:
	cd final_project_A111062 && mvn clean install -DskipTests && onos-app localhost install! target/vrouter-1.0-SNAPSHOT.oar
	
clean:
	onos-app localhost deactivate nycu.sdnfv.vrouter
	onos-app localhost uninstall nycu.sdnfv.vrouter