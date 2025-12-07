import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backend implementation that loads a (weighted) graph from a DOT file and
 * provides shortest-path and reachability operations.
 * NOTE: This class must be in the default package (no package statement).
 *
 * Design notes:
 * - This implementation maintains its own adjacency list internally so it can
 *   run Dijkstra without depending on any specific GraphADT API beyond the
 *   assignment's BackendInterface.
 * - DOT parsing supports directed edges "A -> B" and undirected edges "A -- B".
 * - Edge weight can be specified using either [weight=1.23] or [label=1.23].
 *   If no weight is present, a default weight of 1.0 is used.
 */
public class Backend implements BackendInterface {

    private GraphADT<String, Double> graph; // NEW FIELD added

    /** Adjacency list: node -> list of edges (to, weight) */
    private final Map<String, List<Edge>> adj = new HashMap<>();

    /** Edge record used in adjacency lists */
    private static class Edge {
        final String to;
        final double w;
        Edge(String to, double w) { this.to = to; this.w = w; }
    }

    /**
     * Constructor (no dependencies required).
     * If your graders expect a constructor with GraphADT, you can add an
     * overloaded one later without changing this default constructor.
     */
     public Backend(GraphADT<String, Double> graph) {
      this.graph = graph;
}

        // nothing to initialize beyond 'adj'

    // ---------------------------------------------------------------------
    // 1) loadGraphData
    // ---------------------------------------------------------------------
    /**
     * Load graph data from a DOT file.
     * Pre: filename refers to a readable DOT file.
     * Post: internal graph is cleared and then populated from file contents.
     */
@Override
    public void loadGraphData(String filename) throws IOException {
        adj.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;

            // Regex patterns for parsing DOT syntax
            Pattern edgePat = Pattern.compile(
                "\\s*([A-Za-z0-9_./-]+)\\s*(->|--)\\s*([A-Za-z0-9_./-]+)\\s*(\\[[^\\]]*\\])?\\s*;?.*"
            );
            Pattern nodePat = Pattern.compile(
                "\\s*([A-Za-z0-9_./-]+)\\s*(\\[[^\\]]*\\])?\\s*;\\s*"
            );
            Pattern weightPat = Pattern.compile(
                ".*(?:weight|label)\\s*=\\s*\"?([0-9]+(?:\\.[0-9]+)?)\"?.*"
            );

            while ((line = br.readLine()) != null) {
                line = line.trim();

                // Ignore blank lines, comments, and structural DOT keywords
                if (line.isEmpty() ||
                    line.startsWith("//") || line.startsWith("#") ||
                    line.equals("{") || line.equals("}") ||
                    line.startsWith("graph") || line.startsWith("digraph"))
                    continue;

                // ---------------------------
                // Match edge case: A -> B [w]
                // ---------------------------
                Matcher em = edgePat.matcher(line);
                if (em.matches()) {
                    String u = em.group(1);
                    String op = em.group(2);
                    String v = em.group(3);
                    String attr = em.group(4);

                    // Default weight = 1.0
                    double w = 1.0;
                    if (attr != null) {
                        Matcher wm = weightPat.matcher(attr);
                        if (wm.matches()) w = Double.parseDouble(wm.group(1));
                    }

                    // Build adjacency list
                    adj.computeIfAbsent(u, k -> new ArrayList<>());
                    adj.computeIfAbsent(v, k -> new ArrayList<>());
                    adj.get(u).add(new Edge(v, w));
                    if (op.equals("--"))
                        adj.get(v).add(new Edge(u, w));

                    // Mirror into GraphADT
                    if (graph != null) {
                        graph.insertNode(u);
                        graph.insertNode(v);
                        graph.insertEdge(u, v, w);
                        if (op.equals("--"))
                            graph.insertEdge(v, u, w);
                    }

                    continue;
                }

                // ---------------------------
                // Match standalone node case
                // ---------------------------
                Matcher nm = nodePat.matcher(line);
                if (nm.matches()) {
                    String node = nm.group(1);

                    adj.computeIfAbsent(node, k -> new ArrayList<>());

                    if (graph != null)
                        graph.insertNode(node);
                }
            }
        }
    }

    // ===============================================================
    // Get all nodes
    // ===============================================================

    /**
     * @return list of all nodes in sorted lexicographic order.
     */
    @Override
    public List<String> getListOfAllLocations() {
        List<String> list = new ArrayList<>(adj.keySet());
        Collections.sort(list);
        return list;
    }

    // ===============================================================
    // Shortest path (locations)
    // ===============================================================

    /**
     * Computes the shortest path from start to end using internal Dijkstra.
     * Returns empty list if either node is missing or unreachable.
     */
    @Override
    public List<String> findLocationsOnShortestPath(String start, String end) {
        if (!adj.containsKey(start) || !adj.containsKey(end))
            return Collections.emptyList();

        DijkstraResult dj = dijkstra(start);

        if (!dj.dist.containsKey(end) || Double.isInfinite(dj.dist.get(end)))
            return Collections.emptyList();

        return reconstruct(dj.prev, start, end);
    }

    // ===============================================================
    // Shortest path (weights)
    // ===============================================================

    /**
     * Returns the list of edge weights along the shortest path.
     */
    @Override
    public List<Double> findTimesOnShortestPath(String start, String end) {
        List<String> path = findLocationsOnShortestPath(start, end);
        if (path.isEmpty()) return Collections.emptyList();

        List<Double> times = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            times.add(weightOf(path.get(i), path.get(i + 1)));
        }
        return times;
    }

    // ===============================================================
    // Reachability
    // ===============================================================

    /**
     * Computes all nodes reachable from start within a given travel time.
     */
    @Override
    public List<String> getReachableFromWithin(
            String start, double travelTime) {

        if (!adj.containsKey(start))
            throw new NoSuchElementException("Start node does not exist");

        DijkstraResult dj = dijkstra(start);
        List<String> out = new ArrayList<>();

        for (var entry : dj.dist.entrySet()) {
            String node = entry.getKey();
            double dist = entry.getValue();

            if (!node.equals(start) && dist <= travelTime)
                out.add(node);
        }

        Collections.sort(out);
        return out;
    }

    // ===============================================================
    // Dijkstra Helpers
    // ===============================================================

    /** Stores distances and predecessors for Dijkstra */
    private static class DijkstraResult {
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
    }

    /**
     * Standard Dijkstra over adjacency list.
     */
    private DijkstraResult dijkstra(String src) {
        DijkstraResult r = new DijkstraResult();

        for (String v : adj.keySet())
            r.dist.put(v, Double.POSITIVE_INFINITY);
        r.dist.put(src, 0.0);

        PriorityQueue<String> pq =
            new PriorityQueue<>(Comparator.comparingDouble(r.dist::get));

        pq.add(src);

        while (!pq.isEmpty()) {
            String u = pq.poll();
            double du = r.dist.get(u);

            for (Edge e : adj.getOrDefault(u, List.of())) {
                double nd = du + e.w;

                if (nd < r.dist.get(e.to)) {
                    r.dist.put(e.to, nd);
                    r.prev.put(e.to, u);

                    pq.remove(e.to);
                    pq.add(e.to);
                }
            }
        }

        return r;
    }

    /**
     * Reconstructs the shortest path from predecessor links.
     */
    private List<String> reconstruct(Map<String, String> prev,
                                     String src, String dst) {
        LinkedList<String> path = new LinkedList<>();
        String cur = dst;

        while (cur != null) {
            path.addFirst(cur);
            if (cur.equals(src)) break;
            cur = prev.get(cur);
        }

        if (path.getFirst().equals(src))
            return path;

        return Collections.emptyList();
    }

    /**
     * Finds weight of edge u -> v or returns +∞ if no such edge exists.
     */
    private double weightOf(String u, String v) {
        for (Edge e : adj.getOrDefault(u, List.of()))
            if (e.to.equals(v))
                return e.w;

        return Double.POSITIVE_INFINITY;
    }
}
