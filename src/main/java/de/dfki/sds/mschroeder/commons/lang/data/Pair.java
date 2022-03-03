package de.dfki.sds.mschroeder.commons.lang.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 *
 * 
 * @param <T>
 */
public class Pair<T> extends Couple<T, T> {

    public enum Side {
        Left,
        Right
    }
    
    public Pair(T left, T right) {
        super(left, right);
    }

    /**
     * 
     * @param <T>
     * @param c
     * @return
     * @deprecated need the code from CombinatoricsUtils.combinationsIterator to avoid apache dependency 
     */
    @Deprecated
    public static <T> List<Pair<T>> pairs(Collection<T> c) {
        Object[] array = c.toArray();

        long expectedSize = 0; //TODO CombinatoricsUtils.binomialCoefficient(c.size(), 2);
        List<Pair<T>> l = new ArrayList<>((int) expectedSize);

        Iterator<int[]> iter = null;//TODO CombinatoricsUtils.combinationsIterator(c.size(), 2);

        Stream<int[]> stream = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iter, 0),
                true);

        stream.forEach(pairIndex -> {
            l.add(new Pair((T) array[pairIndex[0]], (T) array[pairIndex[1]]));
        });

        //long count = 0;
        //while (iter.hasNext()) {
        //if (limit != null && count > limit) {
        //    break;
        //}
        //int[] pairIndex = iter.next();
        //l.add(new Pair((T) array[pairIndex[0]], (T) array[pairIndex[1]]));
        //count++;
        //}
        return l;
    }

    public static <T> List<Pair<T>> pairs(Collection<T> left, Collection<T> right) {

        List<Pair<T>> result = new ArrayList<>();

        for (T l : left) {
            for (T r : right) {
                result.add(new Pair<>(l, r));
            }
        }

        return result;
    }

    public static <T> List<Pair<T>> pairwiseComparison(Collection<T> c, Function<Pair<T>, Double> evaluator) {
        List<Pair<T>> pairs = pairs(c);
        for (Pair<T> pair : pairs) {
            pair.setScore(evaluator.apply(pair));
        }
        Collections.sort(pairs);
        return pairs;
    }

}
