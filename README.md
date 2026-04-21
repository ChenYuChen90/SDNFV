# NYCU-SDN-NFV

**Instructor:** 曾建超 (Chien-Chao Tseng)  
**Semester:** 113-1  

---

## Environment

- Ubuntu 22.04  
- ONOS  
- Mininet  
- Open vSwitch  
- Docker  
- FRRouting  

---

## Lab1 – Environment Setup & Topology

- 建立 SDN 實驗環境（Mininet / OVS / ONOS）  
- 使用 Python 撰寫自訂 topology  
- 建立 multi-host / multi-switch 網路  
- 設定 host IP（Layer 3）  

---

## Lab2 – Static Flow Rules

- 使用 ONOS / REST API 下達 flow rules  
- ARP broadcast（ETH_TYPE = 0x0806）  
- IPv4 forwarding（依 source / destination IP）  
- multi-switch 環境下的封包轉送  

---

## Lab3 – Learning Bridge & Proxy ARP

- ONOS app 開發（Java）  

### Learning Bridge

- MAC learning（MAC → port）  
- unknown → flood  
- known → install flow rule  

### Proxy ARP

- controller 維護 ARP table  
- 直接回覆 ARP request  
- 未命中 → flood  

---

## Lab4 – ONOS Advanced Features

- failover group（primary / backup path）  
- meter（rate limiting）  
- intent-based forwarding  
- network config 載入 host 資訊  

---

## Lab5 – NFV Routing (Docker + BGP)

- Docker 建立 multi-router topology  
- FRRouting 設定 BGP（multi-AS）  
- FPM → ONOS routing integration  
- OVS + ONOS 控制資料平面  
