public class BellmanFord {
    public static int computeCost(int costToNeighbor, int advertisedCost) {
        if (advertisedCost == Integer.MAX_VALUE || costToNeighbor == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return costToNeighbor + advertisedCost;
    }

    public static boolean shouldUpdate(int newCost, int currentCost, String currentHop, String fromNeighbor) {
        return (newCost < currentCost) || (currentHop != null && currentHop.equals(fromNeighbor));
    }
}
