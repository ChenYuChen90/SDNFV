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
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }
            if (context.inPacket().parsed().getEtherType() != Ethernet.TYPE_ARP) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            ARP arpPkt = (ARP) ethPkt.getPayload();
            Ip4Address srcIP = Ip4Address.valueOf(arpPkt.getSenderProtocolAddress());
            MacAddress srcMac = MacAddress.valueOf(arpPkt.getSenderHardwareAddress());
            Ip4Address dstIp = Ip4Address.valueOf(arpPkt.getTargetProtocolAddress());
            ConnectPoint inport = pkt.receivedFrom();

// VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV      TODO      VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
            // Controller learns mapping of IP to MAC
            arpTable.putIfAbsent(srcIP, srcMac);

            // Proxy ARP looks up ARP table (For target IP-MAC mapping)
            if (arpPkt.getOpCode() == ARP.OP_REQUEST) {
                if (arpTable.get(dstIp) == null) {
                    log.info("TABLE MISS. Send request to edge ports");
                    flood(pkt, inport);
                } else {
                    log.info("TABLE HIT. Request Mac = {}", arpTable.get(dstIp));
                    Ethernet arpReply = ARP.buildArpReply(dstIp, arpTable.get(dstIp), ethPkt);
                    packetService.emit(
                        new DefaultOutboundPacket(
                            pkt.receivedFrom().deviceId(),
                            DefaultTrafficTreatment.builder().setOutput(pkt.receivedFrom().port()).build(),
                            ByteBuffer.wrap(arpReply.serialize())
                        )
                    );
                }
            } else if (arpPkt.getOpCode() == ARP.OP_REPLY) {
                log.info("RECV_REPLY. Requected MAC = {}", srcMac);
            }
        }
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

// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

}