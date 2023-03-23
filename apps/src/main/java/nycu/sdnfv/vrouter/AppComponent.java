/*
 * Copyright 2023-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nycu.sdnfv.vrouter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ElementId;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.RouteService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

        private final Logger log = LoggerFactory.getLogger(getClass());
        private final QuaggaConfigListener cfgListener = new QuaggaConfigListener();
        private final ConfigFactory<ApplicationId, QuaggaConfig> factory = new ConfigFactory<ApplicationId, QuaggaConfig>(
                        APP_SUBJECT_FACTORY, QuaggaConfig.class, "router") {
                @Override
                public QuaggaConfig createConfig() {
                        return new QuaggaConfig();
                }
        };
        /** Some configurable property. */

        @Reference(cardinality = ReferenceCardinality.MANDATORY)
        protected ComponentConfigService cfgService;

        @Reference(cardinality = ReferenceCardinality.MANDATORY)
        protected NetworkConfigRegistry netcfgService;

        @Reference(cardinality = ReferenceCardinality.MANDATORY)
        protected CoreService coreService;

        @Reference(cardinality = ReferenceCardinality.MANDATORY)
        protected PacketService packetService;

        @Reference(cardinality = ReferenceCardinality.MANDATORY)
        protected RouteService routeService;

        @Reference(cardinality = ReferenceCardinality.MANDATORY)
        protected HostService hostService;

        @Reference(cardinality = ReferenceCardinality.MANDATORY)
        protected InterfaceService interfaceService;

        @Reference(cardinality = ReferenceCardinality.MANDATORY)
        protected IntentService intentService;

        @Reference(cardinality = ReferenceCardinality.MANDATORY)
        protected FlowRuleService flowRuleService;

        private ApplicationId appId;
        private vrouterProcessor processor;
        private ConnectPoint quaggaCP;
        private Ip4Address quaggaIP;
        private MacAddress quaggaMAC;
        private MacAddress quaggaVMAC;
        private List<String> quaggaPeers;

        @Activate
        protected void activate() {
                appId = coreService.registerApplication("nycu.sdnfv.vrouter");
                netcfgService.addListener(cfgListener);
                netcfgService.registerConfigFactory(factory);

                processor = new vrouterProcessor();
                packetService.requestPackets(
                                DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                                PacketPriority.REACTIVE, appId);
                packetService.addProcessor(processor, PacketProcessor.director(6));

                log.info("Started");
        }

        @Deactivate
        protected void deactivate() {
                flowRuleService.removeFlowRulesById(appId);
                netcfgService.removeListener(cfgListener);
                netcfgService.unregisterConfigFactory(factory);
                packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                                PacketPriority.REACTIVE, appId);
                packetService.removeProcessor(processor);
                processor = null;
                log.info("Stopped");
        }

        private void createIntent(ConnectPoint ingressCP, ConnectPoint egressCP,
                        TrafficSelector selector, TrafficTreatment treatment, int priority) {
                FilteredConnectPoint ingress = new FilteredConnectPoint(ingressCP);
                FilteredConnectPoint egress = new FilteredConnectPoint(egressCP);

                if (treatment == null) {
                        PointToPointIntent intent = PointToPointIntent.builder()
                                        .appId(appId)
                                        .filteredIngressPoint(ingress)
                                        .filteredEgressPoint(egress)
                                        .priority(priority)
                                        .selector(selector)
                                        .build();

                        intentService.submit(intent);
                } else {
                        PointToPointIntent intent = PointToPointIntent.builder()
                                        .appId(appId)
                                        .filteredIngressPoint(ingress)
                                        .filteredEgressPoint(egress)
                                        .priority(priority)
                                        .selector(selector)
                                        .treatment(treatment)
                                        .build();

                        intentService.submit(intent);
                }

                log.info("Intent {}, port {} => {}, port {} is submitted.",
                                ingress.connectPoint().deviceId(),
                                ingress.connectPoint().port(),
                                egress.connectPoint().deviceId(),
                                egress.connectPoint().port());
        }

        private void createMto1Intent(Set<FilteredConnectPoint> ingressSet, FilteredConnectPoint egress,
                        TrafficSelector selector, TrafficTreatment treatment, int priority) {
                MultiPointToSinglePointIntent intent = MultiPointToSinglePointIntent.builder()
                                .appId(appId)
                                .filteredIngressPoints(ingressSet)
                                .filteredEgressPoint(egress)
                                .priority(priority)
                                .selector(selector)
                                .treatment(treatment)
                                .build();

                intentService.submit(intent);

                log.info("Intent {} => {}, port {} is submitted.",
                                ingressSet,
                                egress.connectPoint().deviceId(),
                                egress.connectPoint().port());
        }

        private void PeerBGP(Ip4Address er) {
                log.info(er.toString());
                Interface outInterface = interfaceService.getMatchingInterface(er);
                IpAddress outInterfaceIP = outInterface.ipAddressesList().get(0).ipAddress();

                ConnectPoint ingress = quaggaCP;
                ConnectPoint egress = outInterface.connectPoint();
                TrafficSelector goSelector = DefaultTrafficSelector.builder()
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .matchIPDst(IpPrefix.valueOf(er, 24))
                                .build();
                TrafficSelector backSelector = DefaultTrafficSelector.builder()
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .matchIPDst(IpPrefix.valueOf(outInterfaceIP, 24))
                                .build();

                createIntent(ingress, egress, goSelector, null, 41000);
                createIntent(egress, ingress, backSelector, null, 42000);
        }

        private void TransitTraffic(IpAddress dstIP) {
                IpPrefix dstPrefix = IpPrefix.valueOf(dstIP, 24);

                Set<FilteredConnectPoint> ingressSet = new HashSet<FilteredConnectPoint>();
                ingressSet.add(new FilteredConnectPoint(
                                interfaceService.getMatchingInterface(IpAddress.valueOf(quaggaPeers.get(0)))
                                                .connectPoint()));
                ingressSet.add(new FilteredConnectPoint(
                                interfaceService.getMatchingInterface(IpAddress.valueOf(quaggaPeers.get(1)))
                                                .connectPoint()));
                ingressSet.add(new FilteredConnectPoint(
                                interfaceService.getMatchingInterface(IpAddress.valueOf(quaggaPeers.get(2)))
                                                .connectPoint()));

                log.info("routes = {}", interfaceService.getMatchingInterface(dstPrefix.address()));
                FilteredConnectPoint egress = new FilteredConnectPoint(
                                interfaceService.getMatchingInterface(dstPrefix.address()).connectPoint());
                log.info("egress = {}", egress.connectPoint());

                if (ingressSet.contains(egress)) {
                        ingressSet.remove(egress);
                }
                log.info("ingressSet = {}", ingressSet);

                TrafficSelector selector = DefaultTrafficSelector.builder()
                                .matchEthType(Ethernet.TYPE_IPV4)
                                .matchIPDst(dstPrefix)
                                .build();

                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                .setEthSrc(quaggaMAC)
                                .setEthDst(hostService.getHostsByIp(dstIP).iterator().next().mac())
                                .build();

                createMto1Intent(ingressSet, egress, selector, treatment, 40001);
        }

        private class vrouterProcessor implements PacketProcessor {
                @Override
                public void process(PacketContext context) {
                        if (context.isHandled() || context.inPacket().parsed().getEtherType() != Ethernet.TYPE_IPV4) {
                                return;
                        }

                        IPv4 ipv4Packet = (IPv4) context.inPacket().parsed().getPayload();
                        var dstIP = IpAddress.valueOf(ipv4Packet.getDestinationAddress());

                        IpPrefix dstPrefix = IpPrefix.valueOf(dstIP, 24);

                        ConnectPoint ingress = context.inPacket().receivedFrom();
                        TrafficSelector selector = DefaultTrafficSelector.builder()
                                        .matchEthType(Ethernet.TYPE_IPV4)
                                        .matchIPDst(dstPrefix)
                                        .build();

                        if (!routeService.getAllResolvedRoutes(dstPrefix).isEmpty()
                                        && hostService.getHostsByIp(dstIP).isEmpty()
                                        && IpPrefix.valueOf(dstIP, 24) != IpPrefix.valueOf(quaggaIP, 24)) {
                                log.info("SDN to External");
                                List<ResolvedRoute> routes = (List<ResolvedRoute>) routeService
                                                .getAllResolvedRoutes(dstPrefix);

                                IpAddress egressIP = routes.get(0).nextHop();
                                ConnectPoint egress = interfaceService.getMatchingInterface(egressIP).connectPoint();
                                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                                .setEthSrc(quaggaMAC)
                                                .setEthDst(routes.get(0).nextHopMac())
                                                .build();

                                createIntent(ingress, egress, selector, treatment, 40100);
                                context.block();
                        } else if ((routeService.getAllResolvedRoutes(dstPrefix).isEmpty()
                                        && !hostService.getHostsByIp(dstIP).isEmpty())) {
                                log.info("External to SDN");
                                Host dstHost = hostService.getHostsByIp(dstIP).iterator().next();
                                ConnectPoint egress = new ConnectPoint(dstHost.location().deviceId(),
                                                dstHost.location().port());

                                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                                .setEthSrc(quaggaVMAC)
                                                .setEthDst(dstHost.mac())
                                                .build();

                                createIntent(ingress, egress, selector, treatment, 40200);
                                context.block();
                        }
                }
        }

        private class QuaggaConfigListener implements NetworkConfigListener {
                @Override
                public void event(NetworkConfigEvent event) {
                        if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                                        && event.configClass().equals(QuaggaConfig.class)) {
                                QuaggaConfig config = netcfgService.getConfig(appId, QuaggaConfig.class);
                                if (config != null) {
                                        ElementId device = DeviceId
                                                        .deviceId(URI.create(
                                                                        config.connectionPoint().substring(0, config
                                                                                        .connectionPoint()
                                                                                        .indexOf('/'))));
                                        PortNumber port = PortNumber.portNumber(config.connectionPoint()
                                                        .substring(config.connectionPoint().indexOf('/') + 1,
                                                                        config.connectionPoint().length()));

                                        quaggaCP = new ConnectPoint(device, port);
                                        quaggaIP = Ip4Address.valueOf(config.vip());
                                        quaggaMAC = MacAddress.valueOf(config.mac());
                                        quaggaVMAC = MacAddress.valueOf(config.vmac());
                                        quaggaPeers = config.peers();
                                        log.info("Quagga is connected to {}, port {}", quaggaCP.deviceId(),
                                                        quaggaCP.port());
                                        log.info("MAC: {}, VIP:{}, VMAC:{}, PEERS:{}", quaggaMAC, quaggaIP, quaggaVMAC,
                                                        quaggaPeers);

                                        PeerBGP(Ip4Address.valueOf(config.peers().get(0)));
                                        PeerBGP(Ip4Address.valueOf(config.peers().get(1)));
                                        PeerBGP(Ip4Address.valueOf(config.peers().get(2)));
                                        PeerBGP(Ip4Address.valueOf(config.peers().get(3)));

                                        if (!routeService
                                                        .getRouteTables().isEmpty()) {
                                                TransitTraffic(IpAddress.valueOf(config.peers().get(0)));
                                                TransitTraffic(IpAddress.valueOf(config.peers().get(1)));
                                                TransitTraffic(IpAddress.valueOf(config.peers().get(2)));
                                                TransitTraffic(IpAddress.valueOf(config.peers().get(3)));
                                        }
                                }
                        }
                }
        }
}
