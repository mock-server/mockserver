package org.mockserver.collections;

import com.google.common.collect.Multimap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class SubSets {

    public static <T> Multimap<Integer, List<T>> distinctSubSetsMap(List<T> input, Multimap<Integer, List<T>> allSubSets, int i) {
        return distinctSubSetsMap(input, new ArrayDeque<>(), allSubSets, i);
    }

    private static <T> Multimap<Integer, List<T>> distinctSubSetsMap(List<T> input, Deque<T> subSet, Multimap<Integer, List<T>> allSubSets, int i) {
        // if all elements are processed, print the current subset
        if (i < 0) {
            allSubSets.put(subSet.size(), new ArrayList<>(subSet));
            return allSubSets;
        }

        // include the current element in the current subset and recur
        subSet.addLast(input.get(i));
        distinctSubSetsMap(input, subSet, allSubSets, i - 1);

        // backtrack: exclude the current element in the current subset
        subSet.pollLast();

        // remove adjacent duplicate elements
        while (i > 0 && input.get(i) == input.get(i - 1)) {
            i--;
        }

        // exclude the current element in the current subset and recurse
        distinctSubSetsMap(input, subSet, allSubSets, i - 1);

        return allSubSets;
    }

    public static <T> List<List<T>> distinctSubSetsList(List<T> input, List<List<T>> allSubSets, int i) {
        return distinctSubSetsList(input, new ArrayDeque<>(), allSubSets, i);
    }

    private static <T> List<List<T>> distinctSubSetsList(List<T> input, Deque<T> subSet, List<List<T>> allSubSets, int i) {
        // if all elements are processed, print the current subset
        if (i < 0) {
            allSubSets.add(new ArrayList<>(subSet));
            return allSubSets;
        }

        // include the current element in the current subset and recur
        subSet.addLast(input.get(i));
        distinctSubSetsList(input, subSet, allSubSets, i - 1);

        // backtrack: exclude the current element in the current subset
        subSet.pollLast();

        // remove adjacent duplicate elements
        while (i > 0 && input.get(i) == input.get(i - 1)) {
            i--;
        }

        // exclude the current element in the current subset and recurse
        distinctSubSetsList(input, subSet, allSubSets, i - 1);

        return allSubSets;
    }

}
