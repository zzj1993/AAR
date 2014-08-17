/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package datageneration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;

/**
 * Prepare supplied dataset for processing.
 *
 * @author Jordan & Michael
 */
public class ReadInDat {

    // Connection to the database
    private static final CapstoneDBConnection con = new CapstoneDBConnection();

    /**
     * Read in an array of .dat files and close connection to db when done
     * @param files all the files to be read in as Strings
     */
    public static void importTagData(String[] files) {
        // Read and insert the data into the database
        for (String thisFile : files) {
            readData(thisFile);
        }

        // shut down connection
        con.shutDown();
    }

    /**
     * Read in data from file, split on tab character into array and insert required columns only
     * @param location The location of the file to be read
     */
    public static void readData(String location) {
        
        // String to represent each line of the file as it is read
        String line;
        
        // PreparedStatement to pass query string to the database
        PreparedStatement prepStatement = null;
        
        // BufferedReader to parse over file text
        BufferedReader reader;

        try {
            // Set up file reader
            reader = new BufferedReader(new FileReader(location));
            
            // Depending on the provided name and path of the file, read differently
            switch (location) {
                case "user_taggedmovies.dat":

                    // Prepared statement for movie tags
                    prepStatement = con.getConnection().prepareStatement("INSERT INTO "
                            + "capstone.movie_tags(USER_ID, MOVIE_ID, TAG_ID) VALUES (?, ?, ?)");

                    // Jump to the second line, skipping over column names
                    line = reader.readLine();

                    // Read the file line-by-line, 
                    // creating statements and adding to a batch insertion command
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\t");
                        prepStatement.setString(1, normaliseStrings(parts[0]));
                        prepStatement.setString(2, normaliseStrings(parts[1]));
                        prepStatement.setString(3, normaliseStrings(parts[2]));
                        prepStatement.addBatch();
                    }

                    break;

                case "tags.dat":

                    // Prepared statement for movie tags
                    prepStatement = con.getConnection().prepareStatement("INSERT INTO "
                            + "capstone.tags(TAG_ID, TAG_VAL) VALUES (?, ?)");

                    // Jump to the second line, skipping over column names
                    line = reader.readLine();

                    // Read the file line-by-line, 
                    // creating statements and adding to a batch insertion command
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\t");
                        prepStatement.setString(1, normaliseStrings(parts[0]));
                        prepStatement.setString(2, normaliseStrings(parts[1]));
                        prepStatement.addBatch();
                    }
                    break;

                case "movie_genres.dat":
                    // Prepared statement for movie genres
                    prepStatement = con.getConnection().prepareStatement("INSERT INTO "
                            + "capstone.movie_genres(MOVIE_ID, GENRE_VAL) VALUES (?, ?)");

                    // Jump to the second line, skipping over the column names
                    line = reader.readLine();

                    // Read the file line-by-line, 
                    // creating statements and adding to a batch insertion command
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\t");
                        prepStatement.setString(1, normaliseStrings(parts[0]));
                        prepStatement.setString(2, normaliseStrings(parts[1]));
                        prepStatement.addBatch();
                    }
                    break;
            }

            // Execute the batch insertion
            prepStatement.executeBatch();

        } catch (SQLException | IOException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Format strings as required by Mallet. 
     * @param input Original string
     * @return Correctly formatted string
     */
    private static String normaliseStrings(String input) {
        
        // Capitalise first letter
        String output = input.substring(0, 1).toUpperCase() + input.substring(1);
        
        // Remove all spaces
        output = output.replaceAll("\\s", "");
        
        // Remove all other non-word character (punctuation etc.)
        output = output.replaceAll("\\W", "");
        
        return output;
    }
}
