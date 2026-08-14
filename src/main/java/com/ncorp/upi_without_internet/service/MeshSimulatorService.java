package com.ncorp.upi_without_internet.service;

import com.ncorp.upi_without_internet.modal.MeshPacket;
import com.ncorp.upi_without_internet.modal.VirtualDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Slf4j
public class MeshSimulatorService {
    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();
    public MeshSimulatorService(){
        seedDefaultDevices();
    }
    private void seedDefaultDevices(){
        devices.put("phone-naman",   new VirtualDevice("phone-alice",   false));
        devices.put("phone-stranger1", new VirtualDevice("phone-stranger1", false));
        devices.put("phone-stranger2", new VirtualDevice("phone-stranger2", false));
        devices.put("phone-stranger3", new VirtualDevice("phone-stranger3", false));
        devices.put("phone-bridge",  new VirtualDevice("phone-bridge",  true));
    }
    public Collection<VirtualDevice> getDevices(){
        return devices.values();
    }
    public VirtualDevice getDevice(String id){
        return devices.get(id);
    }

    public void inject(String senderDeviceId, MeshPacket packet){
        VirtualDevice sender = devices.get(senderDeviceId);
        if(sender == null) throw new IllegalArgumentException("Unknown device: " + senderDeviceId);
        sender.hold(packet);
        log.info("Packet {} injected at {} (TTL={})",
                packet.getPacketId().substring(0,8), senderDeviceId, packet.getTtl());
    }

    public  GossipResult gossipOnce(){
        int transfers = 0;
        List<VirtualDevice> deviceList = new ArrayList<>(devices.values());
        Map<String, List<MeshPacket>> snapshot = new HashMap<>();
        for(VirtualDevice d: deviceList){
            snapshot.put(d.getDeviceId(), new ArrayList<>(d.getHeldPackets()));
        }
        for(VirtualDevice src : deviceList){
            for(MeshPacket pkt : snapshot.get(src.getDeviceId())){
                if(pkt.getTtl() <= 0) continue;
                for(VirtualDevice dst : deviceList){
                    if(src == dst) continue;
                    if(dst.holds(pkt.getPacketId())) continue;
                    MeshPacket copy = new MeshPacket();
                    copy.setPacketId(pkt.getPacketId());
                    copy.setTtl(pkt.getTtl() - 1);
                    copy.setCipherText((pkt.getCipherText()));
                    copy.setCreatedAt(pkt.getCreatedAt());
                    dst.hold(copy);
                    transfers++;
                }
            }
        }
        log.info("Gossip round completed : {} packet transferred", transfers);
        return new GossipResult(transfers, snapshotMap());
    }

    public Map<String, Integer> snapshotMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (VirtualDevice d : devices.values()) {
            m.put(d.getDeviceId(), d.packetCount());
        }
        return m;
    }

    public List<BridgeUpload> collectBridgeUploads() {
        List<BridgeUpload> out = new ArrayList<>();
        for (VirtualDevice d : devices.values()) {
            if (!d.hasInternet()) continue;
            for (MeshPacket pkt : d.getHeldPackets()) {
                out.add(new BridgeUpload(d.getDeviceId(), pkt));
            }
        }
        return out;
    }

    public void resetMesh() {
        devices.values().forEach(VirtualDevice::clear);
    }


    public record GossipResult(int transfers, Map<String, Integer> deviceCounts) {}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet) {}
}
