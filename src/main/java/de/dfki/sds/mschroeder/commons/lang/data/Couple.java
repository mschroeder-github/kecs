package de.dfki.sds.mschroeder.commons.lang.data;

import java.util.Objects;

/**
 *
 * @param <L>
 * @param <R>
 */
public class Couple<L, R> implements Comparable<Couple<L, R>>{

    public static boolean showScore = false;
    
    protected L left;
    protected R right;
    protected double score;

    public Couple(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public R getRight() {
        return right;
    }

    @Override
    public String toString() {
        if(showScore) {
            return String.format("%.3f", score) + " = " + "(" + String.valueOf(left) + "," + String.valueOf(right) + ")";
        }
        return "('" + String.valueOf(left) + "','" + String.valueOf(right) + "')";
    }

    public double getScore() {
        return score;
    }

    public Couple<L, R> setScore(double score) {
        this.score = score;
        return this;
    }
    
    @Override
    public int compareTo(Couple<L, R> o) {
        return Double.compare(this.score, o.score);
    }
    
     @Override
    public int hashCode() {
        int hash = 3;
        hash = 59 * hash + Objects.hashCode(this.left);
        hash = 59 * hash + Objects.hashCode(this.right);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Couple<?, ?> other = (Couple<?, ?>) obj;
        if (!Objects.equals(this.left, other.left)) {
            return false;
        }
        if (!Objects.equals(this.right, other.right)) {
            return false;
        }
        return true;
    }
    
   
    
    
}
