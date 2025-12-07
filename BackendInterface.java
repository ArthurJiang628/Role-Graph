import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * This is the interface that a backend developer will implement, so that
 * a frontend developer's code can make use of this functionality.  It makes
 * use of a GraphADT to perform shortest path computations.
 */
public interface BackendInterface {

  /*
   * Implementing classes should support the constructor below.
   * @param graph object to store the backend's graph data
   */
  // public Backend(GraphADT<String,Double> graph);

  /**
   * Loads graph data from a dot file.  If a graph was previously loaded, this
   * method should first delete the contents (nodes and edges) of the existing 
   * graph before loading a new one.
   * @param filename the path to a dot file to read graph data from
   * @throws IOException if there was any problem reading from this file
   */
  public void loadGraphData(String filename) throws IOException;

  /**
   * Returns a list of all locations (node data) available in the graph.
   * @return list of all location names
   */
  public List<String> getListOfAllLocations();

  /**
   * Return the sequence of locations along the shortest path from 
   * startLocation to endLocation, or an empty list if no such path exists.
   * @param startLocation the start location of the path
   * @param endLocation the end location of the path
   * @return a list with the nodes along the shortest path from startLocation 
   *         to endLocation, or an empty list if no such path exists
   */
  public List<String> findLocationsOnShortestPath(String startLocation, String endLocation);

  /**
   * Return the walking times in seconds between each two nodes on the 
   * shortest path from startLocation to endLocation, or an empty list of no 
   * such path exists.
   * @param startLocation the start location of the path
   * @param endLocation the end location of the path
   * @return a list with the walking times in seconds between two nodes along 
   *         the shortest path from startLocation to endLocation, or an empty 
   *         list if no such path exists
   */
  public List<Double> findTimesOnShortestPath(String startLocation, String endLocation);
    
  /**
   * Returns the list of locations that can be reached when starting from the 
   * provided startLocation, and travelling a maximum of travelTime seconds.
   * @param startLocation the location to find the reachable locations from
   * @param travelTime is the maximum number of seconds away the start location
   *         that a destination must be in order to be returned
   * @return the list of destinations that can be reached from startLocation 
   *         in travelTime seconds or less
   * @throws NoSuchElementException if startLocation does not exist
   */
    public List<String> getReachableFromWithin(String startLocation, double travelTime) throws NoSuchElementException;

}
