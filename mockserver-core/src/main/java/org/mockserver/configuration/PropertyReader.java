package org.mockserver.configuration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

/**
 * @author jamesdbloom
 */
public class PropertyReader {

    public String getPropValues() throws IOException {

        String result = "";
        Properties prop = new Properties();
        String propFileName = "config.properties";

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);

        if (inputStream != null) {
            prop.load(inputStream);
        } else {
            throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
        }

        Date time = new Date(System.currentTimeMillis());

        // get the property value and print it out
        String user = prop.getProperty("user");
        String company1 = prop.getProperty("company1");
        String company2 = prop.getProperty("company2");
        String company3 = prop.getProperty("company3");

        result = "Company List = " + company1 + ", " + company2 + ", " + company3;
        System.out.println(result + "\nProgram Ran on " + time + " by user=" + user);
        return result;
    }
}
