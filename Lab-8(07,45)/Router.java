import java.util.*;
import java.io.*;
class RoutingEntry {
    public int cost;
    public String nextHop;

    public RoutingEntry(int cost, String nextHop) {
        this.cost = cost;
        this.nextHop = nextHop;
    }
}

public class Router {
    public String id;
    public Map<String, Integer> neighbors = new HashMap<>();
    public Map<String, RoutingEntry> routingTable = new HashMap<>();
    public Map<String, Integer> distanceVector = new HashMap<>();

    public Router(String id) {
        this.id = id;
    }

    public void initializeRoutingTable(Set<String> allRouters) {
        for (String dest : allRouters) {
            if (dest.equals(id)) {
                routingTable.put(dest, new RoutingEntry(0, id));
                distanceVector.put(dest, 0);
            } else if (neighbors.containsKey(dest)) {
                int cost = neighbors.get(dest);
                routingTable.put(dest, new RoutingEntry(cost, dest));
                distanceVector.put(dest, cost);
            } else {
                routingTable.put(dest, new RoutingEntry(Integer.MAX_VALUE, null));
                distanceVector.put(dest, Integer.MAX_VALUE);
            }
        }
    }

    public boolean receiveVector(String fromNeighbor, Map<String, Integer> receivedVector) {
        boolean updated = false;
        int costToNeighbor = neighbors.get(fromNeighbor);

        for (Map.Entry<String, Integer> entry : receivedVector.entrySet()) {
            String destination = entry.getKey();
            int advertisedCost = entry.getValue();
            int newCost = BellmanFord.computeCost(costToNeighbor, advertisedCost);

            RoutingEntry current = routingTable.get(destination);
            if (BellmanFord.shouldUpdate(newCost, current.cost, current.nextHop, fromNeighbor)) {
                routingTable.put(destination, new RoutingEntry(newCost, fromNeighbor));
                distanceVector.put(destination, newCost);
                updated = true;
            }
        }
        return updated;
    }

    public Map<String, Integer> getDistanceVectorFor(String neighbor) {
        Map<String, Integer> poisoned = new HashMap<>();
        for (String dest : distanceVector.keySet()) {
            if (routingTable.get(dest).nextHop != null && routingTable.get(dest).nextHop.equals(neighbor)) {
                poisoned.put(dest, Integer.MAX_VALUE);
            } else {
                poisoned.put(dest, distanceVector.get(dest));
            }
        }
        return poisoned;
    }

    public void printRoutingTable(int time) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Time = ").append(time).append("s] Routing Table at Router ").append(id).append(":\n");
        sb.append("Dest | Cost | Next Hop\n-----------------------\n");
        for (String dest : routingTable.keySet()) {
            RoutingEntry entry = routingTable.get(dest);
            String costStr = (entry.cost == Integer.MAX_VALUE) ? "∞" : Integer.toString(entry.cost);
            String next = (entry.nextHop == null) ? "-" : entry.nextHop;
            sb.append(dest).append(" | ").append(costStr).append(" | ").append(next).append("\n");
        }
        sb.append("\n");
        System.out.print(sb.toString());
        logToFile(sb.toString());
    }

    private void logToFile(String logText) {
        try {
            FileWriter fw = new FileWriter("router_logs_" + id + ".txt", true);
            fw.write(logText);
            fw.close();
        } catch (IOException e) {
            System.err.println("Error writing log for Router " + id + ": " + e.getMessage());
        }
    }
}