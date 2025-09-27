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
package nycu.winlab.groupmeter;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// configurations
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;

// ProxyARP
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.Ip4Address;
import org.onosproject.net.packet.DefaultOutboundPacket;

// Group
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.DefaultGroupBucket;
import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.core.GroupId;
import org.onosproject.net.group.GroupKey;
import org.onosproject.net.group.DefaultGroupKey;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import static org.onosproject.net.group.GroupDescription.Type.FAILOVER;

// flowrule
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficSelector;

// Meters

import org.onosproject.net.meter.MeterService;
import org.onosproject.net.meter.MeterRequest;
import org.onosproject.net.meter.DefaultMeterRequest;
import org.onosproject.net.meter.Band;
import org.onosproject.net.meter.DefaultBand;
import org.onosproject.net.meter.Meter;

// Intent
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.packet.PacketPriority;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Collections;
/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final NameConfigListener cfgListener = new NameConfigListener();

    /** Some configurable property. */
    private final ConfigFactory<ApplicationId, NameConfig> factory = new ConfigFactory<ApplicationId, NameConfig>(
        APP_SUBJECT_FACTORY, NameConfig.class, "informations") {
        @Override
        public NameConfig createConfig() {
            return new NameConfig();
        }
    };

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MeterService meterService;

    private ProxyArpProcessor processor = new ProxyArpProcessor();
    private ApplicationId appId;
    private MacAddress broadcastMac = MacAddress.valueOf("FF:FF:FF:FF:FF:FF");

    private MacAddress h1Mac;
    private MacAddress h2Mac;
    private Ip4Address h1Ip;
    private Ip4Address h2Ip;
    private DeviceId h1deviceID;
    private DeviceId h2deviceID;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.winlab.groupmeter");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);
        packetService.addProcessor(processor, PacketProcessor.director(0));
        requestIPv4Packets();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped");
    }

    private class NameConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
            && event.configClass().equals(NameConfig.class)) {
                NameConfig config = cfgService.getConfig(appId, NameConfig.class);
                if (config != null) {
                    h1deviceID = DeviceId.deviceId(config.host1());
                    h2deviceID = DeviceId.deviceId(config.host2());
                    h1Mac = MacAddress.valueOf(config.mac1());
                    h2Mac = MacAddress.valueOf(config.mac2());
                    h1Ip = Ip4Address.valueOf(config.ip1());
                    h2Ip = Ip4Address.valueOf(config.ip2());

                    log.info("ConnectPoint_h1: {}, ConnectPoint_h2: {}", config.host1(), config.host2());
                    log.info("MacAddress_h1: {}, MacAddress _h2: {}", config.mac1(), config.mac2());
                    log.info("IpAddress_h1: {}, IpAddress_h2: {}", config.ip1(), config.ip2());

                    setupFailoverGroup();
                    setupMeterEntry(h1Mac);
                }
            }
        }
    }

    private void requestIPv4Packets() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4); // 匹配 IPv4 数据包

        // 请求处理 IPv4 数据包
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        log.info("Requested to intercept IPv4 packets.");
    }

    private class ProxyArpProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }
            //log.info("Packet received: EthType = {}", context.inPacket().parsed().getEtherType());
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                log.error("Received a null packet.");
            }

            if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
                if (ethPkt.getDestinationMAC().equals(broadcastMac)) {
                    ARP arpPacket = (ARP) ethPkt.getPayload();
                    if (arpPacket.getOpCode() == ARP.OP_REQUEST) {
                        sendArpReply(context, ethPkt, arpPacket);
                    }
                }
                return;
            }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                createAndSubmitIntent(pkt);
            }
        }
    }

    private void createAndSubmitIntent(InboundPacket pkt) {

        Ethernet ethPkt = pkt.parsed();
        PortNumber ingressPort = pkt.receivedFrom().port();
        DeviceId ingressDeviceId = pkt.receivedFrom().deviceId();
        DeviceId egressDeviceId;
        PortNumber egressPort;

        String[] host2Parts = h2deviceID.toString().split("/");
        DeviceId host2DeviceId = DeviceId.deviceId(host2Parts[0]);
        PortNumber host2Port = PortNumber.portNumber(Integer.parseInt(host2Parts[1]));
        String[] host1Parts = h1deviceID.toString().split("/");
        DeviceId host1DeviceId = DeviceId.deviceId(host1Parts[0]);
        PortNumber host1Port = PortNumber.portNumber(Integer.parseInt(host1Parts[1]));

        TrafficSelector.Builder selectorbuilder = DefaultTrafficSelector.builder();
        PointToPointIntent intent;
        if (ethPkt.getDestinationMAC().equals(h2Mac)) {
            selectorbuilder.matchEthDst(h2Mac);

            intent = PointToPointIntent.builder()
                .appId(appId)
                .selector(selectorbuilder.build())
                .filteredIngressPoint(new FilteredConnectPoint(new ConnectPoint(ingressDeviceId, ingressPort)))
                .filteredEgressPoint(new FilteredConnectPoint(new ConnectPoint(host2DeviceId, host2Port)))
                .build();

            //intentService.submit(intent);
            try {
                intentService.submit(intent);
                log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                    ingressDeviceId, ingressPort, host2DeviceId, host2Port);
            } catch (Exception e) {
                log.error("Failed to submit intent: {}", e.getMessage());
            }

            selectorbuilder.matchEthDst(h1Mac);

            intent = PointToPointIntent.builder()
                .appId(appId)
                .selector(selectorbuilder.build())
                .filteredIngressPoint(new FilteredConnectPoint(new ConnectPoint(host2DeviceId, host2Port)))
                .filteredEgressPoint(new FilteredConnectPoint(new ConnectPoint(host1DeviceId, host1Port)))
                .build();
            //intentService.submit(intent);
            try {
                intentService.submit(intent);
                log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                    host2DeviceId, host2Port, host1DeviceId, host1Port);
            } catch (Exception e) {
                log.error("Failed to submit intent: {}", e.getMessage());
            }
        }
    }

    private void sendArpReply(PacketContext context, Ethernet ethPkt, ARP arpPacket) {
        if (h2Mac == null || h2Ip == null) {
            return;
        }

        InboundPacket pkt = context.inPacket();
        DeviceId senderDevicedId = pkt.receivedFrom().deviceId();
        String[] host2Parts = h2deviceID.toString().split("/");
        DeviceId host2DeviceId = DeviceId.deviceId(host2Parts[0]);
        String[] host1Parts = h1deviceID.toString().split("/");
        DeviceId host1DeviceId = DeviceId.deviceId(host1Parts[0]);

        MacAddress targetMac = null;
        Ip4Address targetIP = null;

        if (senderDevicedId.equals(host2DeviceId)) {
            targetMac = h1Mac;
            targetIP = h1Ip;
        } else if (senderDevicedId.equals(host1DeviceId)) {
            targetMac = h2Mac;
            targetIP = h2Ip;
        }

        Ethernet arpReply = ARP.buildArpReply(targetIP, targetMac, ethPkt);
        try {
            packetService.emit(
                new DefaultOutboundPacket(
                    pkt.receivedFrom().deviceId(),
                    DefaultTrafficTreatment.builder().setOutput(pkt.receivedFrom().port()).build(),
                    ByteBuffer.wrap(arpReply.serialize())
                )
            );
            log.info("ARP reply to: {} => dstMac: {}", pkt.receivedFrom().deviceId(), targetMac);
        } catch (Exception e) {
            log.info("Failed to send ARP reply: {}", e.getMessage(), e);
        }
    }

    public void setupFailoverGroup() {
        DeviceId deviceId = DeviceId.deviceId("of:0000000000000001");

        GroupBucket bucket1 = DefaultGroupBucket.createFailoverGroupBucket(
                DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(2)).build(),
                PortNumber.portNumber(2), null);
        GroupBucket bucket2 = DefaultGroupBucket.createFailoverGroupBucket(
                DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(3)).build(),
                PortNumber.portNumber(3), null);
        GroupBuckets groupBuckets = new GroupBuckets(List.of(bucket1, bucket2));

        GroupKey groupkey = new DefaultGroupKey("group".getBytes());

        GroupDescription groupDescription = new DefaultGroupDescription(
                deviceId,
                FAILOVER,
                groupBuckets,
                groupkey,
                2,
                appId);

        //groupService.addGroup(groupDescription);
        try {
            groupService.addGroup(groupDescription);
            log.info("Successfully added failover group.");
        } catch (Exception e) {
            log.error("Failed to add failover group: {}", e.getMessage());
        }
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.portNumber(1))
                .matchEthType(Ethernet.TYPE_IPV4)
                .build();

        GroupId groupId = new GroupId(2);
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .group(groupId)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(40005)
                .fromApp(appId)
                .makePermanent()
                .build();

        //flowRuleService.applyFlowRules(flowRule);
        try {
            flowRuleService.applyFlowRules(flowRule);
            log.info("Successfully applied flow rule.");
        } catch (Exception e) {
            log.error("Failed to apply flow rule: {}", e.getMessage());
        }
        return;
    }

    public void setupMeterEntry(MacAddress srcMac) {
        DeviceId deviceId = DeviceId.deviceId("of:0000000000000004");
        Band dropBand = DefaultBand.builder()
                .ofType(Band.Type.DROP)
                .withRate(512)
                .burstSize(1024)
                .build();

        MeterRequest meterRequestbuilder = DefaultMeterRequest.builder()
                .forDevice(deviceId)
                .fromApp(appId)
                .withBands(Collections.singletonList(dropBand))
                .withUnit(Meter.Unit.KB_PER_SEC)
                .burst()
                .add();

        //Meter meter = meterService.submit(meterRequestbuilder);
        try {
            Meter meter = meterService.submit(meterRequestbuilder);
            log.info("Meter created with ID: {}", meter.id());

            Thread.sleep(200);

            TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthSrc(srcMac)
                    .build();

            // 設置 Treatment，應用 METER_ID 並指定轉發端口
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .meter(meter.id())                // 使用 METER_ID
                    .setOutput(PortNumber.portNumber(2)) // Output port: 2
                    .build();

            // 創建 Flow Rule
            FlowRule flowRule = DefaultFlowRule.builder()
                    .forDevice(deviceId)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(40005) // 設定優先級
                    .fromApp(appId)
                    .makePermanent()
                    .build();

            // 安裝 Flow Rule
            //flowRuleService.applyFlowRules(flowRule);
            try {
                flowRuleService.applyFlowRules(flowRule);
                log.info("Successfully applied flow rule for meter entry.");
            } catch (Exception e) {
                log.error("Failed to apply flow rule for meter entry: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to submit meter: {}", e.getMessage());
        }
    }
}