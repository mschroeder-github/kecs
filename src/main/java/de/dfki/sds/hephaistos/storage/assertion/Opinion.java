
package de.dfki.sds.hephaistos.storage.assertion;

import java.time.LocalDateTime;

/**
 * A opinion from an agent.
 */
public class Opinion {

    private Intelligence intelligence;
    private String name;
    private LocalDateTime when;
    private Rating rating;
    private double confidence;

    public Opinion() {
    }

    public Opinion(Intelligence intelligence, String name, LocalDateTime when, Rating rating, double confidence) {
        this.intelligence = intelligence;
        this.name = name;
        this.when = when;
        this.rating = rating;
        this.confidence = confidence;
    }

    public Intelligence getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(Intelligence intelligence) {
        this.intelligence = intelligence;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getWhen() {
        return when;
    }

    public void setWhen(LocalDateTime when) {
        this.when = when;
    }

    public Rating getRating() {
        return rating;
    }

    public void setRating(Rating rating) {
        this.rating = rating;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        return "Opinion{" + "who=" + intelligence + " " + name + ", when=" + when + ", rating=" + rating + ", confidence=" + confidence + '}';
    }
    
}
