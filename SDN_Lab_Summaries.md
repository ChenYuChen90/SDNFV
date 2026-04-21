# SDN/NFV Labs Summary

本文件根據工作區中的 spec、程式碼、設定檔、拓樸檔與備份內容整理而成，目的是重建各次 lab 的實作重點。若原始紀錄缺漏，本文會以現有檔案內容進行合理還原。

---

## Lab 1: SDN Environment Setup and Mininet Topology Practice

### 實驗目標
Lab 1 的核心目標是建立後續 SDN/NFV 實驗所需的基本環境，並熟悉 Mininet 拓樸撰寫方式。從資料夾中的 `SDN_Environment_Setup.pdf`、`env_setup.sh` 與兩份 Python 拓樸檔可以看出，這次 lab 不只是安裝工具，更要求學生了解 Mininet 中 host、switch、link 的宣告方式，以及如何在自訂拓樸中配置主機參數。

### 環境與工具
本 lab 使用 Mininet 作為網路模擬平台，並透過 shell script 協助完成 SDN 實驗環境安裝。`env_setup.sh` 應負責安裝或設定後續會用到的工具，例如 Mininet、Open vSwitch、Maven 或 ONOS 開發所需套件。這個部分雖然不是資料夾裡最醒目的成果，但它其實是後面所有 lab 能順利進行的基礎。

### 實作內容
實作紀錄主要體現在兩個拓樸檔。第一個是 [lab1_part2_313551124.py](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab1\lab1_part2_313551124.py:3>)，定義了一個包含 5 台 hosts 與 4 台 switches 的自訂網路。拓樸結構為 `h1-s1`、`h2-s2`、`h3-s3`、`h4-s4`、`h5-s4`，交換器之間再以 `s1-s2`、`s2-s3`、`s2-s4` 互連。這代表學生已經掌握如何在 Mininet 中用 `addHost()`、`addSwitch()`、`addLink()` 建立中小型拓樸。

第二個是 [lab1_part3_313551124.py](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab1\lab1_part3_313551124.py:3>)，其拓樸結構與 part2 相同，但進一步替 h1 到 h5 指定了固定 IP 位址，分別為 `192.168.0.1/27` 到 `192.168.0.5/27`。這表示 part3 的重點在於從「只有連線結構」進一步提升到「具備 Layer 3 參數設定」的網路環境，使主機能在後續 controller 或 flow rule 配合下完成測試。

### 實驗成果
這次 lab 的實際成果可以整理為三點。第一，完成 SDN 實驗主機環境建置。第二，成功撰寫並匯出自訂 Mininet topology。第三，能在 topology 中加入主機 IP 設定，讓模擬網路更貼近真實網路環境。雖然 Lab 1 的邏輯相對單純，但它建立了後續每次實驗都會重複使用的工作流程，也讓學生熟悉以程式描述網路拓樸的方式。

### 總結
Lab 1 屬於基礎建設型實驗，重點不在複雜控制邏輯，而在於把 SDN 實驗的環境、工具與抽象思維建立起來。從目前留下的檔案來看，這次實驗已完成自訂拓樸設計與 IP 配置，為後續的 static flow、ONOS app 與 NFV routing 實驗打下基礎。

---

## Lab 2: Static Flow Rule Configuration with ONOS

### 實驗目標
Lab 2 的目標是讓學生熟悉 OpenFlow flow rule 的撰寫與安裝方式，並透過 ONOS 或 REST API 對交換器下達靜態規則，使主機之間能依照指定路徑或指定封包條件完成通訊。從 `part2` 與 `part3` 的 JSON flow 檔可推斷，本 lab 著重在 selector 與 treatment 的組合，以及 ARP 與 IPv4 規則分開處理的觀念。

### Part 2: 單一交換器上的靜態轉送
在 `part2` 中，共留下三份 flow rule JSON。第一份 [flows_s1-1_313551124.json](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab2\part2\flows_s1-1_313551124.json:1>) 用來匹配 `ETH_TYPE = 0x0806`，也就是 ARP 封包，並將封包輸出到 `ALL`，達成 ARP broadcast 的效果。第二份與第三份 [flows_s1-2_313551124.json](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab2\part2\flows_s1-2_313551124.json:1>)、[flows_s1-3_313551124.json](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab2\part2\flows_s1-3_313551124.json:1>) 則分別比對 `IPV4_SRC`、`IPV4_DST` 與 `ETH_TYPE = 0x0800`，讓 `10.0.0.1 -> 10.0.0.2` 與 `10.0.0.2 -> 10.0.0.1` 可以被導向正確埠口。

這代表本 part 的關鍵觀念是，若只有 IPv4 單播規則而沒有 ARP 封包的處理，主機即使有 IP，也無法先完成 MAC 位址解析，因此實驗中必須先讓 ARP 正常運作，再讓資料封包能依規則轉送。

### Part 3: 多交換器拓樸與規則配置
在 `part3` 中，學生建立了一個由兩台 host 與三台 switch 組成的三角形拓樸，[topo_313551124.py](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab2\part3\topo_313551124.py:3>)。拓樸為 `h1-s1`、`h2-s2`，且 `s1-s2`、`s2-s3`、`s1-s3` 互連。此結構可用來觀察多條路徑、回圈以及分散式 rule 配置時的轉送行為。

目前留下的三份規則 [flows_s1-1_313551124.json](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab2\part3\flows_s1-1_313551124.json:1>)、[flows_s2-1_313551124.json](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab2\part3\flows_s2-1_313551124.json:1>)、[flows_s3-1_313551124.json](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab2\part3\flows_s3-1_313551124.json:1>) 都是 ARP 廣播規則，顯示你至少有把 ARP 封包的泛洪需求配置到各交換器上。至於更完整的 IPv4 單播轉送規則，可能在當時只存在於 controller 上或未完整保存到工作區，因此現有紀錄比較偏向「ARP 與拓樸配置」的保存。

### 實驗成果
Lab 2 的成果在於你已經從 Lab 1 的「建立拓樸」進一步進入「控制封包怎麼走」。你實際操作了 flow selector、priority、output port 與 packet type 的概念，也理解 ARP 與 IPv4 規則必須分開考量。這對後續 reactive forwarding 與 ONOS app 開發很重要，因為那些高階功能本質上仍是建立在 flow rule 的安裝與匹配之上。

### 總結
Lab 2 是從網路模擬走向 SDN 控制邏輯的第一步。現有檔案顯示你已實作靜態 ARP 規則與 IPv4 單播規則，並在多交換器拓樸中進行 flow 配置練習。雖然部分紀錄缺漏，但核心技能與實作方向相當清楚。

---

## Lab 3: ONOS Application Development - Learning Bridge and Proxy ARP

### 實驗目標
Lab 3 的主題是 ONOS application development。根據投影片 `2024_SDNFV_LAB3 .pptx`，此 lab 要求完成兩個獨立 app：Learning Bridge 與 Proxy ARP，並學會使用 ONOS 的 packet processor、flow installation、edge port flooding 與 log 訊息格式。這是整門課的重要轉折點，因為學生從手動下 flow rule 進入控制器程式開發。

### Learning Bridge 實作
Learning Bridge 的主要程式位於 [bridge-app/AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab3\bridge-app\src\main\java\nycu\winlab\bridge\AppComponent.java:59>)。程式中註冊 app `nycu.winlab.bridge`，並在啟動時向 ONOS 請求攔截 IPv4 packet-in。核心資料結構是 `Map<DeviceId, Map<MacAddress, PortNumber>> bridgeTable`，也就是每台交換器各自維護一張 MAC-to-port table。當 controller 收到 packet-in 時，會先學習來源 MAC 與 ingress port，若目的 MAC 尚未學到，就 flood 封包；若已學到，就送到對應埠口並安裝一條 temporary flow rule。

從備份版本可進一步確認，實作中有使用 `DefaultForwardingObjective` 與 `flowObjectiveService.forward()` 來安裝規則，並在封包第一次命中時先 `packetOut`，之後再由交換器依 flow rule 自動處理。log 訊息也對應 spec 中要求的三種格式，包括新增 table entry、destination MAC miss，以及 destination MAC match 後安裝 flow rule。這表示 Learning Bridge 功能應已完成基本要求。

### Proxy ARP 實作
Proxy ARP 的程式位於 [proxyarp/AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab3\proxyarp\src\main\java\nycu\winlab\proxyarp\AppComponent.java:61>)。app 內部維護一張 `Map<Ip4Address, MacAddress>` 作為 ARP table。當接收到 ARP packet-in 時，controller 先學習 sender 的 IP-MAC 對應。若 ARP request 的 target IP 已存在於 table 中，就直接建立 ARP reply 並回送給 sender；若 table 中找不到目標，就將 ARP request flood 到所有 edge ports，等待真正的 host 回覆 ARP reply，並再由 controller 學習對應關係。

這個設計符合投影片中的 Proxy ARP workflow，也反映出你對 edge ports、ARP request/reply、controller 代答與 flooding 邏輯有實作經驗。程式中還保留了 `TABLE MISS`、`TABLE HIT` 與 `RECV_REPLY` 三種 log 訊息，對應 spec 的評分項目。

### 實驗成果
Lab 3 的成果不只是完成兩個 app，更重要的是建立了完整的 ONOS 開發流程，包括 `pom.xml` 命名規範、Maven build、app 安裝與啟用、packet processor 撰寫，以及控制器主動安裝 flow rule 的模式。這代表你已經從使用 SDN 控制器，進一步走到開發控制器應用程式的層次。

### 總結
Lab 3 是整個課程最具代表性的 controller-side 開發實驗。你完成了 Learning Bridge 與 Proxy ARP 兩個經典題目，展現了 packet-in 處理、學習式轉送、ARP 代理回覆與 ONOS app 開發的整合能力。值得注意的是，工作區中的 bridge 原始資料夾看起來像中途版本，但結合備份內容後，可以合理判斷完整交作業版本已存在且功能較完整。

---

## Lab 4: ONOS Group, Meter, Intent, and Network Configuration

### 實驗目標
Lab 4 進一步延伸 ONOS 開發能力，主題從單純 packet processing 擴展到 network configuration、intent framework、group table 與 meter table。從 `project4_313551124` 的內容可看出，本次 lab 不是單一功能，而是將多種 ONOS 機制整合到同一個 app 中，並在指定拓樸上驗證。

### 拓樸與設定資料
本 lab 使用 [ring_topo.py](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab4\ring_topo.py:12>) 建立一個 5-switch、2-host 的拓樸。h1 與 h2 分別連接在 `s1` 與 `s5`，交換器間形成兩條可替代路徑，因此很適合測試 failover group 的效果。主機資訊則存放於 [hostconfig.json](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab4\hostconfig.json:1>)，其中包含 host 所在 connect point、MAC 與 IP，app 啟動後可透過 network config service 載入這些資訊。

### App 設計與核心功能
主程式位於 [project4_313551124/src/main/java/nycu/winlab/groupmeter/AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab4\project4_313551124\src\main\java\nycu\winlab\groupmeter\AppComponent.java:95>)。當設定檔被載入時，app 會先記錄 h1/h2 的 device ID、MAC 與 IP，之後呼叫 `setupFailoverGroup()` 與 `setupMeterEntry()`。`setupFailoverGroup()` 在 `of:0000000000000001` 上建立 failover group，將 port 2 設為 primary、port 3 設為 backup，再安裝一條會導向 group 的 IPv4 flow rule。這說明你已經實際操作 ONOS group bucket 與 group-based forwarding。

接著，`setupMeterEntry()` 在 `of:0000000000000004` 上建立 drop type meter，速率限制設為 `512 KB/s`，並將 meter 綁到匹配特定 source MAC 的 flow rule 上。這代表本 lab 不只要求流量能到達，也要求你理解如何在資料平面加上 traffic policing。

此外，程式中的 `createAndSubmitIntent()` 會根據封包目的 MAC 建立 `PointToPointIntent`，讓 ONOS 自動選擇從 ingress 到 egress 的路徑。`sendArpReply()` 則負責在 ARP request 發生時直接回覆對端主機的 MAC/IP。綜合來看，這個 lab 同時整合了 ARP handling、intent、group failover 與 meter 限速。

### 實驗成果
Lab 4 展現出你對 ONOS 高階抽象的掌握程度。相較於 Lab 3 自己處理 packet flooding 與 flow rule，這次你開始利用 ONOS 內建的 intent framework 與 group abstraction，讓控制邏輯更具結構性。同時，透過 network config 將 host 參數外部化，也讓 app 更接近可維護、可配置的真實系統設計。

### 總結
Lab 4 的價值在於把多種 ONOS 元件整合在一起，從 reactive forwarding 提升到 policy-based forwarding 與 resilience 機制。就目前保存下來的檔案來看，你已完成 ring 拓樸、設定檔載入、failover group、meter rule、ARP 回覆與 point-to-point intent 的整合實作，內容相當完整。

---

## Lab 5: Docker-based NFV Routing with FRR, BGP, and ONOS FPM

### 實驗目標
Lab 5 的主題已經從純 SDN controller app 擴展到 SDN 與 NFV 的整合。從 [docker-compose.yml](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab5\docker-compose.yml:1>)、FRR 設定檔與 `makefile` 可以看出，這次實驗主要目標是用 Docker 建立多台 router 與 host 的虛擬網路，讓 FRR 執行 BGP，並透過 ONOS 的 FPM app 與 Open vSwitch 交換路由資訊。

### 系統架構
本 lab 的 compose 會啟動一個 ONOS container、4 個 host containers 與 5 個 FRR routers。不同 router 與 host 之間各自透過獨立 bridge network 連接，例如 `R1h1br`、`R1R2br`、`R4R5br` 等。`makefile` 額外建立一座 `ovsbr`，設定 OpenFlow 1.4 並指向 ONOS controller，再用 `ovs-docker add-port` 將 `R1`、`R3`、`R4` 接到同一個 OVS bridge 上，形成受 controller 管理的交換區段。[makefile](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab5\makefile:1>)

### BGP 與 FPM 設定
FRR daemon 設定檔 [config/daemons](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab5\config\daemons:1>) 顯示 `bgpd=yes`，代表實驗主軸是 BGP。以 [R1/frr.conf](<c:\Users\nss-yuchen\Desktop\113-1\SDN\SDNFV_Lab5\config\R1\frr.conf:1>) 為例，R1 設定了 `fpm connection ip 172.17.0.1 port 2620`，表示會將路由資訊送往 ONOS。R1 以 AS 65000 身分與 R2、R3、R4 建立 eBGP 鄰居；R2、R3、R4、R5 則分屬不同 AS，並各自公告對應子網，如 `172.21.0.0/16`、`172.22.0.0/16`、`172.23.0.0/16`、`172.24.0.0/16`。這樣的配置使 ONOS 能從 FRR 學習路由，再轉化為底層網路的轉送狀態。

### 實作內容
從容器命名與網段配置可推斷，你已建立一個多站點、多 AS 的 NFV 路由實驗環境。每個 host 預設 gateway 都指向相鄰 router，因此資料流應由 FRR 控制的 BGP 路由決定；同時 OVS 與 ONOS 的存在，使得路由學習結果能影響 OpenFlow 網路中的實際轉送。這已不只是單純 container networking，而是 SDN controller、virtual router 與 routing protocol 的整合實驗。

### 實驗成果
Lab 5 的成果在於你成功把前幾次 lab 的 controller 視角擴展到 NFV 與 routing 視角。你不只操作 ONOS，也建立了包含 FRR、BGP、FPM 與 OVS 的複合式架構。這類架構在概念上接近真實資料中心或邊緣網路中常見的 software router 與 controller 協同模式，因此是很有代表性的 SDN/NFV 實驗。

### 總結
Lab 5 是整門課最接近系統整合的一次實作。從現有檔案可清楚看出，你已完成多 router、多 host 的 Docker 拓樸、FRR BGP 鄰居關係、FPM 對 ONOS 匯出，以及 OVS bridge 與 controller 之間的整合。和前幾次 lab 相比，這次更強調服務編排、虛擬化與控制平面整合，是 SDN 與 NFV 結合的代表作。
---

## Final Project: SDN-enabled Virtual Router

### 專題目標
Final project 的主題是把前面各次 lab 累積的能力整合成一個可運作的 SDN 虛擬路由器。根據 [SDNNFVProject.pptx](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\SDNNFVProject.pptx:1>)，專題要求不只是完成單一 ONOS app，而是要在自己的 SDN 網域中同時處理 `intra-domain traffic`、`inter-domain traffic` 與 `transit traffic`，並且同時支援 IPv4 與 IPv6。整體精神是用 ONOS 控制的 OpenFlow 網路與虛擬化元件，模擬傳統 router、gateway 與 ISP edge 的行為。

### 專題核心概念
這個專題最重要的概念是 `virtual router`。在一般網路中，邊界 router 需要直接和外部 router 建立 BGP session、維護 routing table，並在資料平面上進行轉送與 MAC 改寫；而在這個專題中，BGP 與 route exchange 交給 FRRouting container，真正的資料平面控制則由 ONOS 和 OVS 來完成。換句話說，FRR 負責 control plane，ONOS 依據學到的 route 安裝 intent 或 flow rule，讓整個 SDN 網路看起來像一台虛擬路由器。

另一個關鍵概念是 `virtual gateway`。對內部主機來說，它們看到的是一個邏輯上的 gateway IP 與 gateway MAC，而不是一台真正的實體 router。當主機將封包送給 gateway MAC 後，controller 再根據封包目的 IP、route lookup 與 next hop 決定封包應該送往何處，並在必要時重寫 source/destination MAC。這個概念正是本專題和前面 lab 最大的差異之一。

### 拓樸與部署架構
從 [final_project/code/docker-compose.yml](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\docker-compose.yml:1>) 可看出，專題目前的部署骨架至少包含：

- 一個 ONOS controller
- 兩台 host containers：`h1`、`h2`
- 兩台 FRRouting containers：`r1`、`r2`

其中 `h2` 與 `r2` 已配置在 `172.17.55.0/24` 與 `2a0b:4e07:c4:155::/64` 子網，對應簡報中 AS65xx1 那一側的網域。`r1` 則扮演本組的 BGP speaker，預期還要接到 OVS、VXLAN 與 TA/IXP 網段。這種架構說明 final project 並不只是本地單一容器測試，而是要把本組 SDN 網域接到外部世界。

此外，`final_project` 根目錄已經有 [makefile](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\makefile:1>)，顯示專案有朝著簡報要求的 `make deploy` 與 `make clean` 方向整理。輔助腳本方面，目前 [topo_utils.sh](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\supplement\topo_utils.sh:1>) 已經提供建立 container、veth、bridge、OVS 連線等工具函式，但最後仍保留 `# TODO Write your own code`，代表實際拓樸搭建仍屬於半成品或待補區塊。另外 [wg0.conf](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\supplement\wg0.conf:1>) 也說明這個專題原本打算先透過 WireGuard 接到 TA server，再在其上建立 VXLAN。

### FRRouting、BGP 與 FPM 設定
在 routing control plane 方面，現有設定已經對應簡報中的要求。[config/daemons](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\config\daemons:1>) 同時啟用 `zebra` 與 `bgpd`，並在 `zebra_options` 中加入 `-M fpm`，表示 Zebra 會將 FIB 推送給 ONOS 的 FPM app。這正好對應簡報中「Use ONOS built-in FPM to collect FIB from zebra」的說明。

R1 的 [frr.conf](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\config\R1\frr.conf:1>) 顯示它使用 AS 65550，並和 `192.168.63.2`、`192.168.64.2`、`192.168.70.253` 建立 IPv4 eBGP neighbors，同時也和 `fd63::2`、`fd70::fe` 建立 IPv6 neighbors。它宣告本組 prefix `172.16.55.0/24` 與 `2a0b:4e07:c4:55::/64`，而且和 IXP 連線時使用簡報指定的 BGP password `winlab.nycu`。這表示本組的 FRR 設定已經把對外 route exchange 需要的基本條件都放進去了。

R2 的 [frr.conf](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\config\R2\frr.conf:1>) 則代表另一個 customer/transit 側的 AS，宣告 `172.17.55.0/24` 與 `2a0b:4e07:c4:155::/64`。R1 與 R2 的關係對應簡報中的 AS65xx0 與 AS65xx1 架構，也代表 final project 繼承了 Lab 5 中 `FRR + BGP + FPM` 的能力。

### ONOS App 設計
專題的核心 ONOS app 是 [final_project/code/vrouter](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter:1>)。從 [pom.xml](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter\pom.xml:1>) 可以看出這個 app 名稱是 `nycu.winlab.vrouter`，並額外引入 `onos-apps-route-service-api`，說明它會直接依賴 `RouteService` 來讀取 ONOS 收到的 routing information。

主程式 [AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter\src\main\java\nycu\winlab\vrouter\AppComponent.java:159>) 啟動後會：

- 註冊自己的 app 與 network config factory
- 向 ONOS 請求 IPv4 與 IPv6 packet-in
- 註冊 `RouteListener` 監聽 route event

程式中同時使用了 `PacketService`、`IntentService`、`RouteService`、`InterfaceService`、`HostService`、`EdgePortService` 與 `FlowRuleService`，這代表 final project 不是單純用一種 ONOS abstraction，而是把多種 service 組合在一起，完成虛擬 router 的多層邏輯。

### 設定檔與 router 參數
`vrouter` 的設定格式定義在 [VConfig.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter\src\main\java\nycu\winlab\vrouter\VConfig.java:1>)。其中可讀出的欄位包括：

- `frr`：FRR connect point
- `frr-mac`
- `gateway-ip4` / `gateway-ip6`
- `gateway-mac`
- `v4-peers` / `v6-peers`
- `ta-gateway-ip4` / `ta-gateway-ip6`
- `ta-domain-ip4` / `ta-domain-ip6`

而 [cfg.json](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\cfg.json:1>) 則是這份設定的實際輸入，裡面除了 interface 配置外，也同時提供了本組 virtual gateway、FRR connect point、對 TA 的 gateway、對 peer AS 的連線資訊，以及 `nycu.winlab.ProxyArp` 所需的虛擬 ARP 參數。這表示專案原始設計上是打算讓 `vrouter` 與 `ProxyArp` 兩個 app 協同運作，而不是所有功能都塞進單一 app 中。

### BGP speaker 與 peer 之間的封包轉送
收到 config 後，`AppComponent` 會呼叫 `BGPConnection()`，這段程式位於 [AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter\src\main\java\nycu\winlab\vrouter\AppComponent.java:246>)。做法是把 `v4-peers` 與 `v6-peers` 中的 IP 成對拆開，再透過 `InterfaceService` 找出對應的 WAN connect point，接著安裝兩個方向的 `PointToPointIntent`，讓 FRR 的 connect point 和 WAN connect point 之間能正確交換 BGP 封包。

這段邏輯直接對應簡報中「Delegate BGP Speaker IP to WAN Connect Point」與「Route packet between BGP speaker connect point and WAN connect point」的需求。換句話說，即便 FRR container 沒有物理上直接接到所有 peer，仍能透過 SDN fabric 建立 BGP L3 連通性。

### Intra-domain 流量處理
對於同一個 SDN domain 內的主機互通，`vrouter` app 沿用了 Lab 3 的 learning bridge 思想。`processIntraDomain()` 位於 [AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter\src\main\java\nycu\winlab\vrouter\AppComponent.java:473>)，它會維護一張 `bridgeTable`，記錄各交換器上的 `MAC -> port` 對應。當目的 MAC 尚未知時就 flood；若已知目的 MAC 所在 port，就建立對應的 `PointToPointIntent`。

這表示專題中的 intra-domain forwarding 並不是重新發明一套新東西，而是把 Lab 3 的 L2 forwarding 能力內化到 final app 中，作為內部流量的基礎。

### Inter-domain 與 Virtual Gateway 邏輯
較重要的 router 行為出現在 `processExternalIn()` 與 `processExternalOut()`，[AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter\src\main\java\nycu\winlab\vrouter\AppComponent.java:336>)、[AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter\src\main\java\nycu\winlab\vrouter\AppComponent.java:414>)。

在 `processExternalOut()` 中，程式會根據目的 IP 使用 `RouteService.longestPrefixLookup()` 找到最佳 route，再取出 next hop IP，透過 `InterfaceService` 找出 next hop 所在的出口 connect point，並安裝 intent，同時把 destination MAC 改成 next hop 的 MAC、source MAC 改成 virtual gateway 的 MAC。這是典型的 gateway forwarding 行為，也就是主機只知道把封包交給 gateway，但真正要送給哪個 next hop、要換成什麼 MAC，是由 controller 來決定。

在 `processExternalIn()` 中，程式則是針對從 FRR 或外部進入本網域的封包，嘗試找到對應的內部 host，然後把封包導向該主機所在的 connect point，並重新設定 MAC。這一段是 inter-domain traffic 能正常進入 SDN domain 的核心。

### Transit 流量處理
Transit forwarding 由 `TransitProcessor` 負責，它實作 `RouteListener`，[AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter\src\main\java\nycu\winlab\vrouter\AppComponent.java:510>)。當 route 新增或更新時，程式會呼叫 `installTransitIntent()`，從 `RouteService` 取出所有 route tables 與最佳 route，再用 `InterfaceService` 檢查每個 interface 的 subnet，排除 next hop 所在區段後，把其餘入口整理成 ingress set，最後建立 `MultiPointToSinglePointIntent` 匯聚到對應的出口。

從概念上來看，這一段是在模擬 transit ISP 的角色：只要 ONOS 知道某個 prefix 應該走哪個 next hop，就讓其他入口的流量全部往那個出口送。這和簡報中 transit traffic 的說明完全一致，也延續了 Lab 4 對 intent 的使用經驗。

### IPv6 與 NDP 能力
Final project 明確要求 IPv4/IPv6 dual stack，因此這份 code 也實際加入了 IPv6 邏輯，而不只是停在欄位宣告。除了在 packet processor 中攔截 IPv6 封包、做 IPv6 route lookup 之外，還有 `floodNdp()` 與 `floodArp()` 兩個工具函式，[AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\vrouter\src\main\java\nycu\winlab\vrouter\AppComponent.java:720>)，會主動送出 Neighbor Solicitation 與 ARP Request，以發現 peer 或 next hop 的 MAC。

這表示開發者已經意識到在 dual-stack router 中，不能只處理 IPv4 ARP，還必須處理 IPv6 的鄰居發現。這部分與簡報中介紹的 NDP extension 方向一致。

### ProxyArp 的角色
雖然 `vrouter` 裡已經有部分 ARP/NDP 輔助邏輯，但從 [cfg.json](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\code\cfg.json:1>) 中存在 `nycu.winlab.ProxyArp` 設定，以及 `final_project/lab3_313551124/proxyarp` 仍保留完整程式碼這件事來看，專題原本應該是預期把 Lab 3 的 Proxy ARP 也整合進 final project。參考程式在 [final_project/lab3_313551124/proxyarp/AppComponent.java](<c:\Users\nss-yuchen\Desktop\113-1\SDN\final_project\lab3_313551124\proxyarp\src\main\java\nycu\winlab\proxyarp\AppComponent.java:61>)，其邏輯包括：

- controller 學習 `IP -> MAC` 對應
- ARP miss 時 flood 到 edge ports
- ARP hit 時直接代答 ARP reply

因此，若從專題完整設計來看，較合理的架構是由 `ProxyArp` 處理 gateway/peer 的 ARP 與 NDP 問題，再由 `vrouter` 專心負責 routing 與 forwarding。

### 專題成果與現況評估
從現有檔案整體來看，這個 final project 並不是空白，而是已經具有一份頗完整的原型：

- 有完整的專題簡報與規格說明
- 有 `makefile`、compose、FRR 設定、WireGuard/VXLAN 補充資訊
- 有 `vrouter` app，而且內含 BGP peer forwarding、intra-domain learning、inter-domain forwarding、transit intent 與 IPv6/NDP 邏輯
- 有來自 `lab3_313551124` 的 `ProxyArp` 可作為 final project 內 gateway 鄰居解析的直接參考

不過，它看起來更像是一份「整理後很有內容的半成品」或「可說明架構的 prototype」，而不是保證能立即一鍵 demo 的完成品。主要原因在於完整拓樸腳本仍待補、`code` 目錄下尚未正式整合 `ProxyArp` app、以及 `vrouter` 內部若要真正穩定運作，仍需要實機測試與細節修正。

### 總結
Final project 可以視為整門 SDN/NFV 課程的綜合應用。它把 Lab 3 的 `Learning Bridge` 與 `Proxy ARP`、Lab 4 的 `Intent-based forwarding`、Lab 5 的 `FRR/BGP/FPM` 整合起來，最終目標是形成一個可以在 SDN fabric 中模擬傳統 router 行為的虛擬路由器。就你目前保留下來的 `final_project` 檔案而言，雖然不一定完全可執行，但在架構與實作方向上已經相當完整，也足以清楚說明這個 final project 實際上做了什麼。
