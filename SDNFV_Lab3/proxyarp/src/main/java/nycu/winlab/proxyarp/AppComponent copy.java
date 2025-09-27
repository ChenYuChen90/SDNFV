/*
 * Copyright 2024-present Open Networking Foundation
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
package nycu.winlab.proxyarp;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.nio.ByteBuffer;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;

import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.DefaultOutboundPacket;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ARP;
import org.onlab.packet.Ip4Address;

import org.onosproject.net.flow.FlowRuleService;

import org.onosproject.net.flowobjective.FlowObjectiveService;

import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.ConnectPoint;

// Ipv6
import java.util.Optional;
import java.util.Objects;
import java.util.stream.Stream;
import org.onlab.packet.IPv6;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.ndp.NeighborSolicitation;
import org.onlab.packet.ndp.NeighborAdvertisement;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;

    private ProxyArpProcessor processor = new ProxyArpProcessor();
    private ApplicationId appId;
    private Map<Ip4Address, MacAddress> arpTable = new HashMap<>();
    private Map<Ip6Address, MacAddress> ipv6Table = new HashMap<>();

    @Activate
    protected void activate() {

        // register your app
        appId = coreService.registerApplication("nycu.winlab.bridge");

        // add a packet processor to packetService
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // install a flowrule for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);


        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {

        // remove flowrule installed by your app
        flowRuleService.removeFlowRulesById(appId);

        // remove your packet processor
        packetService.removeProcessor(processor);
        processor = null;

        // remove flowrule you installed for packet-in
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

        log.info("Stopped");
    }

    private class ProxyArpProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled
            if (context.isHandled()) {
                return;
            }

            // 解析 Ethernet 封包
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            // 判斷是否為 ARP 封包 (IPv4)
            if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
                handleArpPacket(context, ethPkt);
            }

            // 判斷是否為 IPv6 封包
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                handleIpv6Packet(context, ethPkt);
            }
        }

        private void handleArpPacket(PacketContext context, Ethernet ethPkt) {
            ARP arpPkt = (ARP) ethPkt.getPayload();
            Ip4Address srcIP = Ip4Address.valueOf(arpPkt.getSenderProtocolAddress());
            MacAddress srcMac = MacAddress.valueOf(arpPkt.getSenderHardwareAddress());
            Ip4Address dstIp = Ip4Address.valueOf(arpPkt.getTargetProtocolAddress());
            ConnectPoint inport = context.inPacket().receivedFrom();

            // Controller learns mapping of IPv4 to MAC
            arpTable.putIfAbsent(srcIP, srcMac);

            // Handle ARP Request
            if (arpPkt.getOpCode() == ARP.OP_REQUEST) {
                if (arpTable.get(dstIp) == null) {
                    log.info("ARP TABLE MISS. Send request to edge ports");
                    flood(context.inPacket(), inport);
                } else {
                    log.info("ARP TABLE HIT. Request MAC = {}", arpTable.get(dstIp));
                    Ethernet arpReply = ARP.buildArpReply(dstIp, arpTable.get(dstIp), ethPkt);
                    packetService.emit(
                        new DefaultOutboundPacket(
                            context.inPacket().receivedFrom().deviceId(),
                            DefaultTrafficTreatment.builder().setOutput(
                                context.inPacket().receivedFrom().port()).build(),
                            ByteBuffer.wrap(arpReply.serialize())
                        )
                    );
                }
            }

            // Handle ARP Reply
            if (arpPkt.getOpCode() == ARP.OP_REPLY) {
                log.info("RECV_REPLY. Requected MAC = {}", srcMac);
            }
        }

        private void handleIpv6Packet(PacketContext context, Ethernet ethPkt) {
            // Attempt to parse Neighbor Solicitation
            findndp(ethPkt).ifPresentOrElse(
                ndp -> handleNeighborSolicitation(context, ethPkt, ndp),
                () -> {
                    if (ethPkt.getPayload() instanceof NeighborAdvertisement) {
                        handleNeighborAdvertisement(context, ethPkt);
                    }
                }
            );
        }

        private void handleNeighborSolicitation(PacketContext context, Ethernet ethPkt, NeighborSolicitation ndp) {
            Ip6Address targetIp = Ip6Address.valueOf(ndp.getTargetAddress());
            MacAddress macAddress = ipv6Table.get(targetIp); // 查找目標 IPv6 地址的 MAC 映射
            ConnectPoint inport = context.inPacket().receivedFrom();

            if (macAddress == null) {
                log.info("NDP TABLE MISS. Flooding Neighbor Solicitation.");
                flood(context.inPacket(), inport);
            } else {
                log.info("NDP TABLE HIT. Responding with Neighbor Advertisement.");
                Ethernet naResponse = NeighborAdvertisement.buildNdpAdv(targetIp, macAddress, ethPkt);
                packetService.emit(new DefaultOutboundPacket(
                    context.inPacket().receivedFrom().deviceId(),
                    DefaultTrafficTreatment.builder().setOutput(context.inPacket().receivedFrom().port()).build(),
                    ByteBuffer.wrap(naResponse.serialize())
                ));
            }
        }

        private void handleNeighborAdvertisement(PacketContext context, Ethernet ethPkt) {
            NeighborAdvertisement naPkt = (NeighborAdvertisement) ethPkt.getPayload();
            Ip6Address senderIp = Ip6Address.valueOf(naPkt.getTargetAddress());
            MacAddress senderMac = ethPkt.getSourceMAC();

            // 更新 IPv6-MAC 表
            ipv6Table.putIfAbsent(senderIp, senderMac);
            log.info("Learned NDP entry: {} -> {}", senderIp, senderMac);
        }

        private Optional<NeighborSolicitation> findndp(Ethernet packet) {
            return Stream.of(packet)
                .filter(Objects::nonNull)                           // Ensure packet is not null
                .map(Ethernet::getPayload)                          // Extract Ethernet payload
                .filter(p -> p instanceof IPv6)                    // Filter for IPv6 packets
                .filter(Objects::nonNull)
                .map(p -> (p != null ? p.getPayload() : null))                          // Extract IPv6 payload
                .filter(p -> p instanceof NeighborSolicitation)    // Filter for Neighbor Solicitation packets
                .map(p -> (NeighborSolicitation) p)                // Cast to Neighbor Solicitation
                .findFirst();                                      // Return the first found packet
        }

        private void flood(InboundPacket pkt, ConnectPoint inport) {
            for (ConnectPoint cp : edgePortService.getEdgePoints()) {
                if (cp.equals(inport)) {
                    continue;
                }
                packetOut(pkt, cp);
            }
        }

        private void packetOut(InboundPacket pkt, ConnectPoint cp) {
            packetService.emit(
                new DefaultOutboundPacket(
                    cp.deviceId(),
                    DefaultTrafficTreatment.builder().setOutput(cp.port()).build(),
                    pkt.unparsed()
                )
            );
        }
    }
}