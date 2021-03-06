package recommendationevaluation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import static recommendationevaluation.MovieRecommenderEngine.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import org.grouplens.lenskit.ItemRecommender;
import org.grouplens.lenskit.RecommenderBuildException;
import org.grouplens.lenskit.core.LenskitConfiguration;
import org.grouplens.lenskit.core.LenskitRecommenderEngine;
import org.grouplens.lenskit.core.RecommenderConfigurationException;
import org.grouplens.lenskit.data.sql.JDBCRatingDAO;
import org.grouplens.lenskit.scored.ScoredId;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Reads in a LenskitRecommenderEngine from file and calculates precision/recall based upon a
 * neighbourhood size and a number of recommendations.<p>
 *
 * For recommendations will be found for each user. These recommendations will be used to calculate
 * the precision and recall of each user's recommendations, as well as an average for this engine.
 * If a LenskitRecommenderEngine for the requested neighbourhood size does not exist, then one will
 * be created.<p>
 *
 * A number of different recommendation engines can be produced and evaluated by separating the
 * neighbourhood size and total recommendations input with spaces when run (eg. Neighbourhood Size:
 * 10 20 30 40)
 *
 * @author Jordan
 */
public class RecommendationEvaluation {

    // DB connection
    private static Connection con;
    private static final String JDBC_DRIVER = "com.mysql.jdbc.Driver",
            DB_URL = "jdbc:mysql://localhost:3306/",
            USER = "root",
            PASS = "password";

    // Parameters for generating recommendations
    private static final ArrayList<Integer> allTotalRecommendations = new ArrayList(),
            allNeighbourhoodSizes = new ArrayList(),
            allAlgorithms = new ArrayList();

    // Metrics
    private static Integer totalDataPieces,
            minDataPieces,
            totalNotIdealUsers;

    private static final Integer minIdealDataPieces = 20;

    private static double totalPrecision,
            totalRecall;

    /**
     * Entry point for the recommender.
     *
     * @param args
     * @throws java.lang.ClassNotFoundException
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.grouplens.lenskit.core.RecommenderConfigurationException
     */
    public static void main(String[] args) throws ClassNotFoundException, SQLException,
            IOException, RecommenderConfigurationException, RecommenderBuildException {

        // Set slf4j logger to only show errors
        Logger root = (Logger) getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.ERROR);

        // Set up DB connection
        Class.forName(JDBC_DRIVER);
        con = DriverManager.getConnection(DB_URL, USER, PASS);

        // Get the neighbourhood size and total recommendations
        getUserInput();

        // Counters for metrics completed and metrics total
        Integer counterMetrics = 0;
        Integer totalMetrics = allNeighbourhoodSizes.size() * allTotalRecommendations.size();

        // Generatics metrics for each neighbourhood size and number of recommendations
        for (Integer algorithm : allAlgorithms) {
            for (Integer size : allNeighbourhoodSizes) {
                for (Integer recommendations : allTotalRecommendations) {
                    // generate metrics
                    generateMetrics(size, recommendations, algorithm, con);

                    counterMetrics++; // this metric is done

                    System.out.println("----FINISHED " + counterMetrics + "/" + totalMetrics + ": "
                            + size + " neighbours and " + recommendations + " recommendations "
                            + getAlgorithmName(algorithm) + "----");
                }
            }
        }

        con.close();
    }

    /**
     * Prompts user for total recommendations to make and neighbourhood size, stores in private
     * variables
     */
    private static void getUserInput() {
        Scanner in = new Scanner(System.in);

        // Get user input for required recommendation algorithm
        System.out.print("\nRecommendation algorithms: \n"
                + "\t1 - Item-based CF\n"
                + "\t2 - User-based CF\n"
                + "\t3 - LDA\n"
                + "\t4 - Word-based Pattern Mining\n"
                + "Which Algorithms (1-4)?: ");
        String[] input = in.nextLine().split("\\s+");

        for (String chosenAlgorithm : input) {
            allAlgorithms.add(Integer.parseInt(chosenAlgorithm));

        }

        // Get user input for number of recommendations and neighbourhood size
        System.out.print("\nNeighbourhood Size: ");
        input = in.nextLine().split("\\s+");

        for (String neighbour : input) {
            allNeighbourhoodSizes.add(Integer.parseInt(neighbour));
        }

        System.out.print("\nNumber of Recommendations: ");
        input = in.nextLine().split("\\s+");

        for (String recommendationSize : input) {
            allTotalRecommendations.add(Integer.parseInt(recommendationSize));
        }

    }

    /**
     * Generates metrics for a neighbourhood size and number of recommendations
     *
     * @param neighbourhoodSize The size of the neighbourhood for these recommendations
     * @param totalRecommendations The number of recommendations to make
     * @param algorithm The algorithm to perform
     * @param con Capstone database connection
     * @throws ClassNotFoundException
     * @throws IOException
     * @throws SQLException
     * @throws RecommenderBuildException
     */
    public static void generateMetrics(Integer neighbourhoodSize, Integer totalRecommendations,
            Integer algorithm, Connection con) throws ClassNotFoundException, IOException, SQLException,
            RecommenderBuildException {

        System.out.println("\n\n----Generating Metrics for " + neighbourhoodSize
                + " neighbours and " + totalRecommendations + " recommendations "
                + getAlgorithmName(algorithm) + "----");

        resetVariables();

        createEngineIfNecessary(algorithm, neighbourhoodSize);

        // Set up recommender using engine and DAO, and get item recommender   
        System.out.println("Creating Item Recommender...");
        ItemRecommender irec = createItemRecommender(algorithm, neighbourhoodSize);
        System.out.println("DONE\n");

        // Get all users
        ResultSet users = getUsers();

        System.out.println("Generating Precision/Recall Documents...");

        initialiseDirectories(algorithm, neighbourhoodSize, totalRecommendations);

        // Iterate over all users and calculate precision and recall
        while (users.next()) {
            ArrayList<Long> recs = getMovieRecommendations(users.getInt("USER_ID"), irec,
                    totalRecommendations);

            // create PrecisionRecall for this user based upon their recommendations 
            // and a comparison set of data
            PrecisionRecall pr = new PrecisionRecall(recs,
                    getComparisonDataSet(users.getInt("USER_ID")));

            // get precision and recall for this user
            double precision = pr.getPrecision();
            double recall = pr.getRecall();
            Integer dataCount = getUserDataCount(users.getInt("USER_ID"));

            // output metrics to file
            outputUserMetrics(algorithm, users.getInt("USER_ID"), precision, recall, dataCount, recs,
                    neighbourhoodSize, totalRecommendations);

            // hack for userbasedcf, when no recommendations are made then totalPrecision breaks
            // not sure why no recommendations would be made for a user
            if (Double.isNaN(precision)) {
                precision = 0;
            }

            // update metrics
            totalPrecision += precision;
            totalRecall += recall;
            totalDataPieces += dataCount;

            if (dataCount < minDataPieces) {
                minDataPieces = dataCount;
            }

            if (dataCount < minIdealDataPieces) {
                totalNotIdealUsers++;
            }

        }

        // output the averrage metrics for this recommendation engine
        outputAverageMetrics(algorithm, neighbourhoodSize, totalRecommendations);
        System.out.println("DONE");
    }

    /**
     * Reset all the variables to their default values.
     */
    private static void resetVariables() {
        totalPrecision = 0;
        totalRecall = 0;
        totalDataPieces = 0;
        minDataPieces = Integer.MAX_VALUE;
        totalNotIdealUsers = 0;
    }

    /**
     * Creates an engine file if one does not already exist.
     *
     * @throws ClassNotFoundException
     * @throws IOException
     * @throws SQLException
     * @throws RecommenderBuildException
     */
    private static void createEngineIfNecessary(Integer algorithm, Integer neighbourhoodSize)
            throws ClassNotFoundException, IOException, SQLException, RecommenderBuildException {

        // engine file for this CF operation
        File engineFile = new File(getEngineName(algorithm, neighbourhoodSize));

        // check for existance of file
        if (!engineFile.exists()) {
            // Create new engine based on neighbourhood size, and write to file
            System.out.println("Creating New Engine...");

            MovieRecommenderEngine newEngine
                    = new MovieRecommenderEngine(neighbourhoodSize, algorithm);

            newEngine.writeToFile(algorithm);
            System.out.println("DONE\n");

        } else {
            System.out.println("Engine Already Exists\n");
        }
    }

    /**
     * Creates a recommender, based on engine file, and returns it's item recommender.
     *
     * @return The item recommender
     * @throws IOException
     * @throws RecommenderConfigurationException
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    private static ItemRecommender createItemRecommender(Integer algorithm,
            Integer neighbourhoodSize) throws IOException, RecommenderConfigurationException,
            ClassNotFoundException, SQLException {

        JDBCRatingDAO dao = MovieRecommenderEngine.createItemDAO(); // data access object
        LenskitConfiguration dataConfig = new LenskitConfiguration(); // config for DAO free engine

        // Add DAO to config
        dataConfig.addComponent(dao);

        // Read in engine based on file name, and add data config
        LenskitRecommenderEngine engine
                = LenskitRecommenderEngine.newLoader().addConfiguration(dataConfig)
                .load(new File(getEngineName(algorithm, neighbourhoodSize)));

        // create recommender and return item recommender
        return engine.createRecommender().getItemRecommender();
    }

    /**
     * Gets all users as a result set
     *
     * @return All the users
     * @throws SQLException
     */
    private static ResultSet getUsers() throws SQLException {
        // Prepared statement to collect users
        PreparedStatement prepStatement;

        // SQL statement to collect distinct users
        prepStatement = con.prepareStatement(
                "SELECT DISTINCT USER_ID FROM capstone.movie_ratings_final");

        // Execute query and return results
        return prepStatement.executeQuery();
    }

    /**
     * Extracts just the movie IDs to be recommended to a user
     *
     * @param userId The user to make recommendations for
     * @param irec The item recommender for this recommendation
     * @return The recommended movie's IDs
     */
    private static ArrayList<Long> getMovieRecommendations(Integer userId, ItemRecommender irec,
            Integer totalRecommendations) {

        ArrayList<Long> movies = new ArrayList();

        // We don't want to exclude any results from recommendations. By default all previously
        // rate movies are excluded, however we need these to be recommended to get precision/recall
        HashSet<Long> exclude = new HashSet();

        // Get ScoredId recommendations
        List<ScoredId> recs = irec.recommend(userId, totalRecommendations, null, exclude);

        // Pull out just ratings from ScoredIds
        for (ScoredId item : recs) {
            movies.add(item.getId());
        }

        return movies;
    }

    /**
     * Gets the comparison data from the DB for a user
     *
     * @param userId The id of the user to get the comparison set for
     * @return the comparison movie's IDs
     */
    private static ArrayList<Long> getComparisonDataSet(Integer userId) throws SQLException {
        ResultSet rs;
        ArrayList<Long> movieIds = new ArrayList();
        PreparedStatement prepStatement;

        // SQL statement to collect distinct users
        prepStatement = con.prepareStatement(
                "SELECT MOVIE_ID FROM capstone.movie_ratings_final WHERE USER_ID = " + userId);

        // Execute query and return results
        rs = prepStatement.executeQuery();

        // Iterate over each row in result set and add movie id to array list
        while (rs.next()) {
            movieIds.add(rs.getLong("MOVIE_ID"));
        }
        return movieIds;
    }

    /**
     * Counts the number of data pieces used to recommend content to a user.
     *
     * @param userId The ID of the user to retrieve a count for
     * @return The data count
     */
    private static Integer getUserDataCount(int userId) throws SQLException {
        PreparedStatement prepStatement;
        ResultSet result;

        // SQL statement to count data for user
        prepStatement = con.prepareStatement(
                "SELECT COUNT(*) FROM capstone.movie_ratings_final WHERE USER_ID = " + userId);

        // Execute query and store result
        result = prepStatement.executeQuery();

        // Ensure resultset is set to the first record
        result.first();

        // Return the number of data items
        return result.getInt("COUNT(*)");
    }

    /**
     * Creates a directory for metric files to be placed.
     */
    private static void initialiseDirectories(Integer algorithm, Integer neighbourhoodSize,
            Integer totalRecommendations) {
        
        File rootMetricsDirectory = new File("metrics");
        
        File metricsDirectory = new File("metrics/" + 
                getAlgorithmName(algorithm));
        
        File thisRecommendationDirectory = new File("metrics/" + 
                getAlgorithmName(algorithm)+ "/neighbours" + neighbourhoodSize + 
                "top" + totalRecommendations + "/");

        // If the directories do not exist then create them
        if (!rootMetricsDirectory.exists()) {
            rootMetricsDirectory.mkdir();
        }
        
        if (!metricsDirectory.exists()) {
            metricsDirectory.mkdir();
        }

        if (!thisRecommendationDirectory.exists()) {
            thisRecommendationDirectory.mkdir();
        }
    }

    /**
     * Creates a file containing the metrics for a user.
     *
     * @param userId The id of the user to create a file for
     * @param precision The precision of this users recommendations
     * @param recall The recall of this users recommendations
     * @param dataPieces Pieces of data for this user
     * @throws FileNotFoundException
     * @throws IOException
     */
    private static void outputUserMetrics(Integer algorithm, int userId, double precision,
            double recall, Integer dataPieces, ArrayList<Long> recs, Integer neighbourhoodSize,
            Integer totalRecommendations) throws FileNotFoundException, IOException {

        // File to be written to for user
        File userDocument = new File("metrics/" + 
                getAlgorithmName(algorithm) + "/neighbours"+ neighbourhoodSize + 
                "top" + totalRecommendations + "/" + userId + ".dat");

        PrintWriter writer;

        // Create file for the metrics
        userDocument.createNewFile();
        writer = new PrintWriter(userDocument);

        // Add metrics to file
        writer.println("User Id: " + userId);
        writer.println("Precision: " + precision);
        writer.println("Recall: " + recall);
        writer.println("Data Pieces: " + dataPieces);

        // Add recommendations to file
        writer.println("Recommendations:");
        for (Long rec : recs) {
            writer.println(rec);
        }

        writer.close();
    }

    /**
     * Creates a file containing the average metrics for this recommendation engine.
     *
     * @throws FileNotFoundException
     * @throws IOException
     * @throws SQLException
     */
    private static void outputAverageMetrics(Integer algorithm, Integer neighbourhoodSize,
            Integer totalRecommendations)
            throws FileNotFoundException, IOException, SQLException {

        // File to be written to for user
        File metricsDocument = new File("metrics/" + 
                getAlgorithmName(algorithm) + "/neighbours" + neighbourhoodSize + 
                "top" + totalRecommendations + "/average.dat");

        PrintWriter writer;
        double userCount;

        // Create file for the metrics
        metricsDocument.createNewFile();
        writer = new PrintWriter(metricsDocument);

        // Count all users
        userCount = countUsers();

        // Add metrics to file
        writer.println("Neighbourhood Size: " + neighbourhoodSize);
        writer.println("Total Recommendations: " + totalRecommendations);
        writer.println("Avg. Data Pieces Per User: " + totalDataPieces / userCount);
        writer.println("Min Data Pieces For A User: " + minDataPieces);
        writer.println("Users With Less Than " + minIdealDataPieces + " Pieces Of Data: "
                + totalNotIdealUsers / userCount * 100 + "%");
        writer.println("Avg. Precision: " + totalPrecision / userCount);
        writer.println("Avg. Recall: " + totalRecall / userCount);

        writer.close();
    }

    /**
     * Counts the number of users found in the movie_ratings_final table
     *
     * @return The number of users found
     * @throws SQLException
     */
    private static double countUsers() throws SQLException {
        // Prepared statement for querying database
        PreparedStatement prepStatement;

        // Store the results of the query
        ResultSet result;

        // SQL query to count the number of users
        prepStatement = con.prepareStatement(
                "SELECT COUNT(DISTINCT USER_ID) FROM capstone.movie_ratings_final");

        // Execute the query and store the results
        result = prepStatement.executeQuery();

        // Ensure resultset is set to the first record
        result.first();

        // Return the number of users
        return result.getInt("COUNT(DISTINCT USER_ID)");
    }

}
