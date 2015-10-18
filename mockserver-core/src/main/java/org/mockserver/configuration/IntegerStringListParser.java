package org.mockserver.configuration;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class IntegerStringListParser {

    private Logger logger = LoggerFactory.getLogger(IntegerStringListParser.class);

    public Integer[] toArray(String integers) {
        List<Integer> integerList = toList(integers);
        return integerList.toArray(new Integer[integerList.size()]);
    }

    public List<Integer> toList(String integers) {
        List<Integer> integerList = new ArrayList<Integer>();
        for (String integer : Splitter.on(",").split(integers)) {
            try {
                integerList.add(Integer.parseInt(integer.trim()));
            } catch (NumberFormatException nfe) {
                logger.error("NumberFormatException converting " + integer + " to integer", nfe);
            }
        }
        return integerList;
    }

    public String toString(Integer[] integers) {
        return toString(Arrays.asList(integers));
    }

    public String toString(List<Integer> integers) {
        return Joiner.on(",").join(integers);
    }
}
