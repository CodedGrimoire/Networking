// File: Network.java
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class Network {
    public Map<String, Router> routers = new HashMap<>();
    public String topologyFile;
    public int messageCount = 0;
    public List<String> log = new ArrayList<>();

    public void readTopology(String filename) throws IOException {
        this.topologyFile = filename;
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(" ");
            String r1 = parts[0], r2 = parts[1];
            int cost = Integer.parseInt(parts[2]);

            routers.putIfAbsent(r1, new Router(r1));
            routers.putIfAbsent(r2, new Router(r2));

            routers.get(r1).neighbors.put(r2, cost);
            routers.get(r2).neighbors.put(r1, cost);

            logChange("Link: " + r1 + " <-> " + r2 + " = " + cost);
        }
        reader.close();

        Set<String> allRouters = routers.keySet();
        for (Router router : routers.values()) {
            router.initializeRoutingTable(allRouters);
        }
    }

    public void startSimulation() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(this::sendDistanceVectors, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::updateCostRandomly, 30, 30, TimeUnit.SECONDS);
    }

    public void sendDistanceVectors() {
        for (Router sender : routers.values()) {
            for (String neighborId : sender.neighbors.keySet()) {
                Router neighbor = routers.get(neighborId);
                Map<String, Integer> vector = sender.getDistanceVectorFor(neighborId);
                boolean changed = neighbor.receiveVector(sender.id, vector);
                messageCount++;
                logChange(sender.id + " sent vector to " + neighborId);
                if (changed) {
                    neighbor.printRoutingTable((int)(System.currentTimeMillis() / 1000));
                    logChange("Routing table updated at " + neighbor.id);
                }
            }
        }
    }

    public void updateCostRandomly() {
        List<String> keys = new ArrayList<>(routers.keySet());
        Random rand = new Random();
        String r1 = keys.get(rand.nextInt(keys.size()));
        if (routers.get(r1).neighbors.isEmpty()) return;

        List<String> r1Neighbors = new ArrayList<>(routers.get(r1).neighbors.keySet());
        String r2 = r1Neighbors.get(rand.nextInt(r1Neighbors.size()));

        int newCost = 1 + rand.nextInt(10);
        routers.get(r1).neighbors.put(r2, newCost);
        routers.get(r2).neighbors.put(r1, newCost);

        String msg = String.format("Cost changed: %s <-> %s = %d", r1, r2, newCost);
        System.out.println("[Time = " + (System.currentTimeMillis() / 1000) + "s] " + msg);
        logChange(msg);
    }

    public void logChange(String msg) {
        log.add("[LOG] " + msg);
    }

    public void printLog() {
        System.out.println("---- LOG ----");
        for (String entry : log) {
            System.out.println(entry);
        }
    }
}
