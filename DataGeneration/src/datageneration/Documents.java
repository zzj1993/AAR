package datageneration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Abstract class that handles user documents for profiles and topic models
 * 
* @author Jordan & Michael
 */
public abstract class Documents {

    // Instance connection to the database
    private CapstoneDBConnection con;

    // An SQL resultset of unique IDs of each user
    private static ResultSet userIds;

    /**
     * Creates documents for each user and generates metrics.
     *
     * @param target What type of documents need to be created
     * @param con Database connection instance
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     */
    public Documents(String target, CapstoneDBConnection con) throws IOException, SQLException {
        this.con = con; // set connection field

        // Query and store user IDs
        collectUserIds();

        // Generate a document for each user from the targeted dataset
        makeDocuments(target);

        // Analyse the returned data and calculate simple metrics
        createMetricsDocument();
    }

    /**
     * Counts the number of users found in the movie_tags table
     *
     * @return The number of users found
     * @throws SQLException
     */
    protected int countUsers() throws SQLException {

        // Prepared statement for querying database
        PreparedStatement prepStatement;

        // Store the results of the query
        ResultSet result;

        // SQL query to count the number of users
        prepStatement = con.getConnection().prepareStatement(
                "SELECT COUNT(DISTINCT USER_ID) FROM capstone.movie_tags");

        // Execute the query and store the results
        result = prepStatement.executeQuery();

        // Ensure resultset is set to the first record
        result.first();

        // Return the number of users
        return result.getInt("COUNT(DISTINCT USER_ID)");
    }

    /**
     * Creates a directory
     *
     * @param directoryName The name of the directory
     */
    protected void createDirectory(String directoryName) {
        File directory = new File(directoryName);

        // If the directory does not exist then create it
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    /**
     * Collects all the user IDs and stores them in the userIds ResultSet
     */
    protected void collectUserIds() {

        // Prepared statement to query for user IDs
        PreparedStatement prepStatement;

        // Get all the unique user ids
        try {
            prepStatement = con.getConnection().prepareStatement(
                    "SELECT DISTINCT USER_ID FROM capstone.movie_tags");
            userIds = prepStatement.executeQuery();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Getter method for connection
     *
     * @return Database connection instance
     */
    protected CapstoneDBConnection getDocumentsConnection() {
        return con;
    }

    /**
     * Getter method for userId
     *
     * @return the user IDs in a ResultSet
     */
    protected ResultSet getUserIds() {
        return userIds;
    }

    /**
     * Creates the documents for each user for the given target data. Each document consists of all
     * a user's artifacts in a random order.
     *
     * @param target The type of documents to be generated
     * @throws IOException
     * @throws java.sql.SQLException
     */
    abstract protected void makeDocuments(String target) throws IOException, SQLException;

    /**
     * Generate the document for the overall metrics of the data.<p>
     *
     * The metrics include:<br>
     * <li>The number of users
     * <li>The largest amount of data for any one user
     * <li>The smallest amount of data for any one
     * <li>The average amount of data across the entire userbase.<p>
     *
     * @throws FileNotFoundException File or filepath not found
     * @throws IOException
     * @throws SQLException
     */
    abstract void createMetricsDocument() throws FileNotFoundException, IOException, SQLException;
}
